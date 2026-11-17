use std::io::{Cursor, Read};
use std::path::PathBuf;
use std::sync::mpsc::{sync_channel, Receiver};
use std::sync::Arc;
use tiny_http::{Header, ListenAddr, Response, Server, StatusCode};
use url::Url;

use crate::state::AppState;

pub fn start_http_server(state: Arc<AppState>, runtime: tokio::runtime::Handle) -> u16 {
    let server = Server::http("127.0.0.1:0").expect("Failed to bind local HTTP server");
    let port = match server.server_addr() {
        ListenAddr::IP(addr) => addr.port(),
        _ => 18943,
    };
    log::info!("Adhora streaming HTTP server started on port {}", port);

    let state_clone = Arc::clone(&state);
    std::thread::spawn(move || {
        for request in server.incoming_requests() {
            let s = Arc::clone(&state_clone);
            let r = runtime.clone();
            std::thread::spawn(move || {
                handle_request(request, &s, &r);
            });
        }
    });

    port
}

fn handle_request(
    request: tiny_http::Request,
    state: &Arc<AppState>,
    runtime: &tokio::runtime::Handle,
) {
    let url_str = format!("http://127.0.0.1{}", request.url());
    let url = match Url::parse(&url_str) {
        Ok(u) => u,
        Err(_) => {
            let _ = request.respond(Response::empty(StatusCode(400)));
            return;
        }
    };

    let path = url.path().to_string();
    let query: std::collections::HashMap<_, _> = url.query_pairs().into_owned().collect();

    let chat_id: i64 = match query.get("chat_id").and_then(|s| s.parse().ok()) {
        Some(id) => id,
        None => {
            let _ = request.respond(Response::empty(StatusCode(400)));
            return;
        }
    };

    let msg_id: i32 = match query.get("msg_id").and_then(|s| s.parse().ok()) {
        Some(id) => id,
        None => {
            let _ = request.respond(Response::empty(StatusCode(400)));
            return;
        }
    };

    match path.as_str() {
        "/thumb" => {
            handle_thumb(request, state, runtime, chat_id, msg_id);
        }
        "/stream" | "/media" => {
            handle_stream(request, state, runtime, chat_id, msg_id);
        }
        _ => {
            let _ = request.respond(Response::empty(StatusCode(404)));
        }
    }
}

fn handle_thumb(
    request: tiny_http::Request,
    state: &Arc<AppState>,
    runtime: &tokio::runtime::Handle,
    chat_id: i64,
    msg_id: i32,
) {
    let thumb_dir = PathBuf::from(&state.cache_dir).join("thumbs");
    let _ = std::fs::create_dir_all(&thumb_dir);
    let thumb_file = thumb_dir.join(format!("{}_{}.jpg", chat_id, msg_id));

    if thumb_file.exists() {
        if let Ok(bytes) = std::fs::read(&thumb_file) {
            let header = Header::from_bytes(&b"Content-Type"[..], &b"image/jpeg"[..]).unwrap();
            let cache = Header::from_bytes(&b"Cache-Control"[..], &b"max-age=604800"[..]).unwrap();
            let cors = Header::from_bytes(&b"Access-Control-Allow-Origin"[..], &b"*"[..]).unwrap();
            let mut res = Response::from_data(bytes);
            res.add_header(header);
            res.add_header(cache);
            res.add_header(cors);
            let _ = request.respond(res);
            return;
        }
    }

    // Not in disk cache, fetch thumbnail via Telegram client
    let state_clone = Arc::clone(state);
    let thumb_file_clone = thumb_file.clone();
    let res = runtime.block_on(async move {
        crate::tg::download_thumbnail(&state_clone, chat_id, msg_id, &thumb_file_clone).await
    });

    if res.is_ok() && thumb_file.exists() {
        if let Ok(bytes) = std::fs::read(&thumb_file) {
            let header = Header::from_bytes(&b"Content-Type"[..], &b"image/jpeg"[..]).unwrap();
            let cache = Header::from_bytes(&b"Cache-Control"[..], &b"max-age=604800"[..]).unwrap();
            let cors = Header::from_bytes(&b"Access-Control-Allow-Origin"[..], &b"*"[..]).unwrap();
            let mut resp = Response::from_data(bytes);
            resp.add_header(header);
            resp.add_header(cache);
            resp.add_header(cors);
            let _ = request.respond(resp);
            return;
        }
    }

    let _ = request.respond(Response::empty(StatusCode(404)));
}

fn handle_stream(
    request: tiny_http::Request,
    state: &Arc<AppState>,
    runtime: &tokio::runtime::Handle,
    chat_id: i64,
    msg_id: i32,
) {
    let range_header = request
        .headers()
        .iter()
        .find(|h| h.field.equiv("Range"))
        .map(|h| h.value.to_string());

    let state_clone = Arc::clone(state);
    let (info_res, target_opt) = runtime.block_on(async move {
        crate::tg::get_media_stream_info(&state_clone, chat_id, msg_id).await
    });

    let (total_size, mime) = match info_res {
        Ok(info) => info,
        Err(_) => {
            let _ = request.respond(Response::empty(StatusCode(404)));
            return;
        }
    };

    let target = match target_opt {
        Some(t) => t,
        None => {
            let _ = request.respond(Response::empty(StatusCode(404)));
            return;
        }
    };

    let (start, end) = parse_range(range_header.as_deref(), total_size);
    let content_length = if end >= start { end - start + 1 } else { 0 };

    if content_length == 0 {
        let _ = request.respond(Response::empty(StatusCode(416)));
        return;
    }

    let (tx, rx) = sync_channel::<Result<Vec<u8>, std::io::Error>>(2);
    let client_opt = runtime.block_on(async {
        let lock = state.client.lock().await;
        lock.clone()
    });

    let client = match client_opt {
        Some(c) => c,
        None => {
            let _ = request.respond(Response::empty(StatusCode(503)));
            return;
        }
    };

    // Adaptive chunk size:
    // Small ranges (e.g. metadata / moov atom <= 128KB) download in 128KB chunks for instant response;
    // larger ranges download in 512KB chunks for high throughput.
    let chunk_size: usize = if content_length <= 128 * 1024 {
        128 * 1024
    } else {
        512 * 1024
    };
    let start_chunk = (start / chunk_size) as i32;
    let first_skip = start % chunk_size;

    runtime.spawn(async move {
        crate::tg::stream_download_worker(
            client,
            target,
            chunk_size,
            start_chunk,
            first_skip,
            content_length,
            tx,
        )
        .await;
    });

    let reader = StreamChannelReader {
        rx,
        curr: Cursor::new(Vec::new()),
        remaining: content_length,
    };

    let is_range = range_header.is_some();
    let status = if is_range {
        StatusCode(206)
    } else {
        StatusCode(200)
    };

    let content_type = Header::from_bytes(&b"Content-Type"[..], mime.as_bytes()).unwrap();
    let accept_ranges = Header::from_bytes(&b"Accept-Ranges"[..], &b"bytes"[..]).unwrap();
    let cors = Header::from_bytes(&b"Access-Control-Allow-Origin"[..], &b"*"[..]).unwrap();

    let mut headers = vec![content_type, accept_ranges, cors];
    if is_range {
        let cr_header_val = format!("bytes {}-{}/{}", start, end, total_size);
        if let Ok(cr) = Header::from_bytes(&b"Content-Range"[..], cr_header_val.as_bytes()) {
            headers.push(cr);
        }
    }

    let response = Response::new(
        status,
        headers,
        reader,
        Some(content_length),
        None,
    );

    let _ = request.respond(response);
}

fn parse_range(range_header: Option<&str>, total_size: usize) -> (usize, usize) {
    if total_size == 0 {
        return (0, 0);
    }
    let max_index = total_size.saturating_sub(1);
    if let Some(header) = range_header {
        let trimmed = header.trim();
        if let Some(stripped) = trimmed.strip_prefix("bytes=") {
            let parts: Vec<&str> = stripped.split('-').collect();
            if parts.len() == 2 {
                let p0 = parts[0].trim();
                let p1 = parts[1].trim();

                if p0.is_empty() && !p1.is_empty() {
                    // Suffix range: bytes=-500 (the last 500 bytes of the file)
                    if let Ok(suffix) = p1.parse::<usize>() {
                        let start = total_size.saturating_sub(suffix);
                        return (start, max_index);
                    }
                } else if !p0.is_empty() && p1.is_empty() {
                    // Open-ended range: bytes=1000- (from 1000 to end of file)
                    if let Ok(start) = p0.parse::<usize>() {
                        return (start.min(max_index), max_index);
                    }
                } else if !p0.is_empty() && !p1.is_empty() {
                    // Closed range: bytes=1000-2000
                    if let (Ok(start), Ok(end)) = (p0.parse::<usize>(), p1.parse::<usize>()) {
                        return (start.min(max_index), end.min(max_index));
                    }
                }
            }
        }
    }
    (0, max_index)
}

struct StreamChannelReader {
    rx: Receiver<Result<Vec<u8>, std::io::Error>>,
    curr: Cursor<Vec<u8>>,
    remaining: usize,
}

impl Read for StreamChannelReader {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        if self.remaining == 0 {
            return Ok(0);
        }

        while (self.curr.position() as usize) >= self.curr.get_ref().len() {
            match self.rx.recv() {
                Ok(Ok(chunk)) => {
                    self.curr = Cursor::new(chunk);
                }
                Ok(Err(e)) => return Err(e),
                Err(_) => return Ok(0), // EOF or closed channel
            }
        }

        let to_read = buf.len().min(self.remaining);
        let n = self.curr.read(&mut buf[..to_read])?;
        self.remaining -= n;
        Ok(n)
    }
}
