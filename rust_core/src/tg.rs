use std::path::{Path, PathBuf};
use std::sync::mpsc::SyncSender;
use std::sync::Arc;
use grammers_client::media::{Downloadable, Media, PhotoSize};
use grammers_client::peer::Peer;
use grammers_client::{Client, SignInError};
use grammers_mtsender::SenderPool;
use grammers_session::storages::SqliteSession;
use grammers_tl_types as tl;

use crate::state::{AppState, AuthResponse, DialogDto, MediaDto};

pub enum StreamTarget {
    Doc(grammers_client::media::Document),
    Photo(grammers_client::media::Photo),
}

pub async fn get_or_create_client(
    state: &AppState,
    api_id: i32,
) -> Result<Client, Box<dyn std::error::Error + Send + Sync>> {
    let mut lock = state.client.lock().await;
    if let Some(ref client) = *lock {
        return Ok(client.clone());
    }

    let session_path = PathBuf::from(&state.data_dir).join("adhora_session.db");
    let session = Arc::new(SqliteSession::open(session_path).await?);
    let pool = SenderPool::new(Arc::clone(&session), api_id);
    let handle = pool.handle;
    let runner = pool.runner;

    tokio::spawn(async move {
        let _ = runner.run().await;
    });

    let client = Client::new(handle);
    *lock = Some(client.clone());
    Ok(client)
}

pub async fn check_auth(state: &AppState) -> bool {
    let client = match get_or_create_client(state, 2040).await {
        Ok(c) => c,
        Err(_) => return false,
    };
    client.is_authorized().await.unwrap_or(false)
}

pub async fn request_login_code(
    state: &AppState,
    api_id: i32,
    api_hash: &str,
    phone: &str,
) -> AuthResponse {
    let client = match get_or_create_client(state, api_id).await {
        Ok(c) => c,
        Err(e) => {
            return AuthResponse {
                status: "error".to_string(),
                message: Some(format!("Failed to connect: {}", e)),
                hint: None,
                user_id: None,
                user_name: None,
            };
        }
    };

    match client.request_login_code(phone, api_hash).await {
        Ok(token) => {
            let mut tok_lock = state.login_token.lock().await;
            *tok_lock = Some(token);
            AuthResponse {
                status: "ok".to_string(),
                message: Some("Verification code sent to Telegram app/SMS".to_string()),
                hint: None,
                user_id: None,
                user_name: None,
            }
        }
        Err(e) => AuthResponse {
            status: "error".to_string(),
            message: Some(e.to_string()),
            hint: None,
            user_id: None,
            user_name: None,
        },
    }
}

pub async fn sign_in(state: &AppState, code: &str) -> AuthResponse {
    let client_opt = {
        let lock = state.client.lock().await;
        lock.clone()
    };
    let client = match client_opt {
        Some(c) => c,
        None => {
            return AuthResponse {
                status: "error".to_string(),
                message: Some("Client not initialized".to_string()),
                hint: None,
                user_id: None,
                user_name: None,
            }
        }
    };

    let token_opt = {
        let mut tok_lock = state.login_token.lock().await;
        tok_lock.take()
    };

    let token = match token_opt {
        Some(t) => t,
        None => {
            return AuthResponse {
                status: "error".to_string(),
                message: Some("No pending login session found. Request code first.".to_string()),
                hint: None,
                user_id: None,
                user_name: None,
            }
        }
    };

    match client.sign_in(&token, code.trim()).await {
        Ok(user) => AuthResponse {
            status: "ok".to_string(),
            message: Some("Logged in successfully".to_string()),
            hint: None,
            user_id: Some(user.id().bare_id_unchecked()),
            user_name: user.username().map(|s| s.to_string()),
        },
        Err(SignInError::PasswordRequired(pw_token)) => {
            let hint = pw_token.hint().map(|s| s.to_string());
            let mut pw_lock = state.password_token.lock().await;
            *pw_lock = Some(pw_token);
            AuthResponse {
                status: "2fa_required".to_string(),
                message: Some("2-Step Verification is enabled on your account".to_string()),
                hint,
                user_id: None,
                user_name: None,
            }
        }
        Err(e) => AuthResponse {
            status: "error".to_string(),
            message: Some(e.to_string()),
            hint: None,
            user_id: None,
            user_name: None,
        },
    }
}

pub async fn check_password(state: &AppState, password: &str) -> AuthResponse {
    let client_opt = {
        let lock = state.client.lock().await;
        lock.clone()
    };
    let client = match client_opt {
        Some(c) => c,
        None => {
            return AuthResponse {
                status: "error".to_string(),
                message: Some("Client not initialized".to_string()),
                hint: None,
                user_id: None,
                user_name: None,
            }
        }
    };

    let pw_token_opt = {
        let mut pw_lock = state.password_token.lock().await;
        pw_lock.take()
    };

    let pw_token = match pw_token_opt {
        Some(t) => t,
        None => {
            return AuthResponse {
                status: "error".to_string(),
                message: Some("No pending 2FA token".to_string()),
                hint: None,
                user_id: None,
                user_name: None,
            }
        }
    };

    match client.check_password(pw_token, password.trim()).await {
        Ok(user) => AuthResponse {
            status: "ok".to_string(),
            message: Some("2FA verification successful".to_string()),
            hint: None,
            user_id: Some(user.id().bare_id_unchecked()),
            user_name: user.username().map(|s| s.to_string()),
        },
        Err(e) => AuthResponse {
            status: "error".to_string(),
            message: Some(e.to_string()),
            hint: None,
            user_id: None,
            user_name: None,
        },
    }
}

pub async fn logout(state: &AppState) -> bool {
    let client_opt = {
        let mut lock = state.client.lock().await;
        lock.take()
    };
    if let Some(client) = client_opt {
        let _ = client.sign_out().await;
    }
    let session_path = PathBuf::from(&state.data_dir).join("adhora_session.db");
    let _ = std::fs::remove_file(session_path);
    true
}

pub async fn fetch_dialogs(state: &AppState) -> Result<Vec<DialogDto>, String> {
    let client = match get_or_create_client(state, 2040).await {
        Ok(c) => c,
        Err(e) => return Err(format!("Client error: {}", e)),
    };

    let mut dialog_iter = client.iter_dialogs();
    let mut dialogs = Vec::new();
    let mut peers_lock = state.cached_peers.lock().await;

    while let Ok(Some(dialog)) = dialog_iter.next().await {
        let peer = dialog.peer;
        let unread = match dialog.raw {
            tl::enums::Dialog::Dialog(ref d) => d.unread_count,
            tl::enums::Dialog::Folder(ref f) => f.unread_muted_messages_count,
            _ => 0,
        };

        match peer {
            Peer::Channel(ref c) => {
                let id = c.id().bare_id_unchecked();
                let title = c.title().to_string();
                let username = c.username().map(|s| s.to_string());
                let is_creator = c.raw.creator;
                let is_admin = is_creator || c.admin_rights().is_some();
                if let Ok(Some(pref)) = c.to_ref().await {
                    peers_lock.insert(id, pref);
                }
                dialogs.push(DialogDto {
                    id,
                    title,
                    username,
                    is_channel: true,
                    is_group: false,
                    is_admin,
                    is_creator,
                    unread_count: unread,
                    thumb_url: format!("http://127.0.0.1:{}/thumb?chat_id={}&msg_id=0", state.port(), id),
                });
            }
            Peer::Group(ref g) => {
                let id = g.id().bare_id_unchecked();
                let title = g.title().unwrap_or("Group").to_string();
                let username = g.username().map(|s| s.to_string());
                let mut is_creator = false;
                let mut is_admin = false;
                match &g.raw {
                    tl::enums::Chat::Chat(chat) => {
                        is_creator = chat.creator;
                        is_admin = is_creator || chat.admin_rights.is_some();
                    }
                    tl::enums::Chat::Channel(chan) => {
                        is_creator = chan.creator;
                        is_admin = is_creator || chan.admin_rights.is_some();
                    }
                    _ => {}
                }
                if let Ok(Some(pref)) = g.to_ref().await {
                    peers_lock.insert(id, pref);
                }
                dialogs.push(DialogDto {
                    id,
                    title,
                    username,
                    is_channel: false,
                    is_group: true,
                    is_admin,
                    is_creator,
                    unread_count: unread,
                    thumb_url: format!("http://127.0.0.1:{}/thumb?chat_id={}&msg_id=0", state.port(), id),
                });
            }
            Peer::Community(ref comm) => {
                let id = comm.id().bare_id_unchecked();
                let title = comm.title().to_string();
                let is_creator = comm.raw.creator;
                let is_admin = is_creator || comm.raw.admin_rights.is_some();
                if let Ok(Some(pref)) = comm.to_ref().await {
                    peers_lock.insert(id, pref);
                }
                dialogs.push(DialogDto {
                    id,
                    title,
                    username: None,
                    is_channel: false,
                    is_group: true,
                    is_admin,
                    is_creator,
                    unread_count: unread,
                    thumb_url: format!("http://127.0.0.1:{}/thumb?chat_id={}&msg_id=0", state.port(), id),
                });
            }
            _ => {}
        }
    }

    let mut cached = state.cached_dialogs.lock().await;
    *cached = dialogs.clone();
    Ok(dialogs)
}

pub async fn fetch_media_for_channels(
    state: &AppState,
    channel_ids: &[i64],
    limit_per_channel: usize,
    offset_id: i32,
) -> Result<Vec<MediaDto>, String> {
    let client = match get_or_create_client(state, 2040).await {
        Ok(c) => c,
        Err(e) => return Err(format!("Client error: {}", e)),
    };

    let peers_lock = state.cached_peers.lock().await;
    let mut all_media = Vec::new();

    for &chat_id in channel_ids {
        let peer_ref = match peers_lock.get(&chat_id) {
            Some(p) => *p,
            None => continue,
        };

        let mut msg_iter = client.iter_messages(peer_ref);
        if offset_id > 0 {
            msg_iter = msg_iter.offset_id(offset_id);
        }

        let mut count = 0;

        while let Ok(Some(msg)) = msg_iter.next().await {
            if count >= limit_per_channel {
                break;
            }

            let msg_id = msg.id();
            let date = msg.date().as_second();
            let caption = msg.text().to_string();

            if let Some(media) = msg.media() {
                match media {
                    Media::Photo(ref photo) => {
                        let size = photo.thumbs().iter().map(|t| t.size()).max().unwrap_or(0);
                        let w = 1280;
                        let h = 720;

                        all_media.push(MediaDto {
                            id: msg_id,
                            chat_id,
                            chat_title: "".to_string(),
                            media_type: "photo".to_string(),
                            file_name: format!("photo_{}_{}.jpg", chat_id, msg_id),
                            mime_type: "image/jpeg".to_string(),
                            size,
                            duration: 0,
                            width: w,
                            height: h,
                            date,
                            caption,
                            stream_url: format!(
                                "http://127.0.0.1:{}/media?chat_id={}&msg_id={}",
                                state.port(), chat_id, msg_id
                            ),
                            thumb_url: format!(
                                "http://127.0.0.1:{}/thumb?chat_id={}&msg_id={}",
                                state.port(), chat_id, msg_id
                            ),
                        });
                        count += 1;
                    }
                    Media::Document(ref doc) => {
                        let (is_video, is_image, dur, w, h, fname, mime) = detect_media(doc);
                        if is_video || is_image {
                            let size = doc.size().unwrap_or(0);
                            let mtype = if is_video { "video" } else { "photo" };

                            all_media.push(MediaDto {
                                id: msg_id,
                                chat_id,
                                chat_title: "".to_string(),
                                media_type: mtype.to_string(),
                                file_name: fname,
                                mime_type: mime,
                                size,
                                duration: dur,
                                width: w,
                                height: h,
                                date,
                                caption,
                                stream_url: format!(
                                    "http://127.0.0.1:{}/stream?chat_id={}&msg_id={}",
                                    state.port(), chat_id, msg_id
                                ),
                                thumb_url: format!(
                                    "http://127.0.0.1:{}/thumb?chat_id={}&msg_id={}",
                                    state.port(), chat_id, msg_id
                                ),
                            });
                            count += 1;
                        }
                    }
                    _ => {}
                }
            }
        }
    }

    all_media.sort_by(|a, b| b.date.cmp(&a.date));
    Ok(all_media)
}

fn detect_media(
    doc: &grammers_client::media::Document,
) -> (bool, bool, i32, i32, i32, String, String) {
    let mut is_video = false;
    let mut is_image = false;
    let mut duration = 0;
    let mut w = 0;
    let mut h = 0;
    let filename = doc.name().unwrap_or("media_file").to_string();
    let mut mime = doc.mime_type().unwrap_or("").to_lowercase();

    if let Some(tl::enums::Document::Document(d)) = &doc.raw.document {
        for attr in &d.attributes {
            match attr {
                tl::enums::DocumentAttribute::Video(v) => {
                    is_video = true;
                    duration = v.duration as i32;
                    w = v.w;
                    h = v.h;
                }
                tl::enums::DocumentAttribute::ImageSize(img) => {
                    is_image = true;
                    w = img.w;
                    h = img.h;
                }
                _ => {}
            }
        }
    }

    if mime.starts_with("video/") {
        is_video = true;
    } else if mime.starts_with("image/") {
        is_image = true;
    }

    let lower_name = filename.to_lowercase();
    let video_exts = [".mp4", ".mkv", ".mov", ".avi", ".webm", ".flv", ".3gp", ".wmv", ".m4v", ".ts"];
    let image_exts = [".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".bmp", ".svg"];

    if !is_video && video_exts.iter().any(|ext| lower_name.ends_with(ext)) {
        is_video = true;
        if mime.is_empty() {
            mime = "video/mp4".to_string();
        }
    }

    if !is_video && !is_image && image_exts.iter().any(|ext| lower_name.ends_with(ext)) {
        is_image = true;
        if mime.is_empty() {
            mime = "image/jpeg".to_string();
        }
    }

    (is_video, is_image, duration, w, h, filename, mime)
}

pub async fn download_thumbnail(
    state: &AppState,
    chat_id: i64,
    msg_id: i32,
    dest: &Path,
) -> Result<(), String> {
    let client = match get_or_create_client(state, 2040).await {
        Ok(c) => c,
        Err(e) => return Err(format!("Client error: {}", e)),
    };

    let peer_ref = {
        let peers_lock = state.cached_peers.lock().await;
        match peers_lock.get(&chat_id) {
            Some(p) => *p,
            None => return Err("Chat peer not found in cache".to_string()),
        }
    };

    let msgs = client
        .get_messages_by_id(peer_ref, &[msg_id])
        .await
        .map_err(|e| e.to_string())?;

    let msg = match msgs.first().and_then(|m| m.as_ref()) {
        Some(m) => m,
        None => return Err("Message not found".to_string()),
    };

    if let Some(media) = msg.media() {
        match media {
            Media::Photo(photo) => {
                let thumbs = photo.thumbs();
                // Priority 1: Pick the best real thumbnail (medium ~320px or large ~800px)
                let best_size_thumb = thumbs.iter().filter_map(|t| {
                    if let PhotoSize::Size(ref s) = t {
                        Some((s.width.max(s.height), t))
                    } else {
                        None
                    }
                }).max_by_key(|(dim, _)| *dim).map(|(_, t)| t);

                if let Some(thumb) = best_size_thumb {
                    if client.download_media(thumb, dest).await.is_ok() {
                        return Ok(());
                    }
                }

                // Priority 2: Download full photo if thumbnail not available
                if client.download_media(&photo, dest).await.is_ok() {
                    return Ok(());
                }

                // Priority 3: Fallback to stripped/cached thumbnail if network failed
                for thumb in thumbs {
                    if let Some(data) = thumb.to_data() {
                        if !data.is_empty() {
                            let _ = std::fs::write(dest, data);
                            return Ok(());
                        }
                    }
                }
            }
            Media::Document(doc) => {
                let thumbs = doc.thumbs();
                // Priority 1: Pick high quality real thumbnail for document/video
                let best_size_thumb = thumbs.iter().filter_map(|t| {
                    if let PhotoSize::Size(ref s) = t {
                        Some((s.width.max(s.height), t))
                    } else {
                        None
                    }
                }).max_by_key(|(dim, _)| *dim).map(|(_, t)| t);

                if let Some(thumb) = best_size_thumb {
                    if client.download_media(thumb, dest).await.is_ok() {
                        return Ok(());
                    }
                }

                // Priority 2: If image document, download doc
                let mime = doc.mime_type().unwrap_or("").to_lowercase();
                let name = doc.name().unwrap_or("").to_lowercase();
                if mime.starts_with("image/") || name.ends_with(".jpg") || name.ends_with(".png") || name.ends_with(".webp") {
                    if client.download_media(&doc, dest).await.is_ok() {
                        return Ok(());
                    }
                }

                // Priority 3: Fallback to stripped/cached thumbnail
                for thumb in thumbs {
                    if let Some(data) = thumb.to_data() {
                        if !data.is_empty() {
                            let _ = std::fs::write(dest, data);
                            return Ok(());
                        }
                    }
                }
            }
            _ => {}
        }
    }

    Err("No thumbnail found for media".to_string())
}

pub async fn get_media_stream_info(
    state: &AppState,
    chat_id: i64,
    msg_id: i32,
) -> (Result<(usize, String), String>, Option<StreamTarget>) {
    let client = match get_or_create_client(state, 2040).await {
        Ok(c) => c,
        Err(e) => return (Err(format!("Client error: {}", e)), None),
    };

    let peer_ref = {
        let peers_lock = state.cached_peers.lock().await;
        match peers_lock.get(&chat_id) {
            Some(p) => *p,
            None => return (Err("Chat peer not found".to_string()), None),
        }
    };

    let msgs = match client.get_messages_by_id(peer_ref, &[msg_id]).await {
        Ok(m) => m,
        Err(e) => return (Err(e.to_string()), None),
    };

    let msg = match msgs.first().and_then(|m| m.as_ref()) {
        Some(m) => m,
        None => return (Err("Message not found".to_string()), None),
    };

    if let Some(media) = msg.media() {
        match media {
            Media::Document(doc) => {
                let size = doc.size().unwrap_or(0);
                let mime = doc.mime_type().unwrap_or("video/mp4").to_string();
                return (Ok((size, mime)), Some(StreamTarget::Doc(doc)));
            }
            Media::Photo(photo) => {
                let size = photo.thumbs().iter().map(|t| t.size()).max().unwrap_or(0);
                return (Ok((size, "image/jpeg".to_string())), Some(StreamTarget::Photo(photo)));
            }
            _ => {}
        }
    }

    (Err("Media not streamable".to_string()), None)
}

pub async fn stream_download_worker(
    client: Client,
    target: StreamTarget,
    chunk_size: usize,
    start_chunk: i32,
    mut first_skip: usize,
    content_length: usize,
    tx: SyncSender<Result<Vec<u8>, std::io::Error>>,
) {
    let mut sent = 0;

    match target {
        StreamTarget::Doc(doc) => {
            let mut download = client
                .iter_download(&doc)
                .chunk_size(chunk_size as i32)
                .skip_chunks(start_chunk);

            while sent < content_length {
                match download.next().await {
                    Ok(Some(mut bytes)) => {
                        if first_skip > 0 {
                            if bytes.len() > first_skip {
                                bytes = bytes[first_skip..].to_vec();
                            } else {
                                first_skip -= bytes.len();
                                continue;
                            }
                            first_skip = 0;
                        }

                        let len = bytes.len().min(content_length - sent);
                        let slice = bytes[..len].to_vec();
                        if tx.send(Ok(slice)).is_err() {
                            break;
                        }
                        sent += len;
                    }
                    Ok(None) => break,
                    Err(e) => {
                        let _ = tx.send(Err(std::io::Error::new(
                            std::io::ErrorKind::Other,
                            e.to_string(),
                        )));
                        break;
                    }
                }
            }
        }
        StreamTarget::Photo(photo) => {
            let mut download = client
                .iter_download(&photo)
                .chunk_size(chunk_size as i32)
                .skip_chunks(start_chunk);

            while sent < content_length {
                match download.next().await {
                    Ok(Some(mut bytes)) => {
                        if first_skip > 0 {
                            if bytes.len() > first_skip {
                                bytes = bytes[first_skip..].to_vec();
                            } else {
                                first_skip -= bytes.len();
                                continue;
                            }
                            first_skip = 0;
                        }

                        let len = bytes.len().min(content_length - sent);
                        let slice = bytes[..len].to_vec();
                        if tx.send(Ok(slice)).is_err() {
                            break;
                        }
                        sent += len;
                    }
                    Ok(None) => break,
                    Err(e) => {
                        let _ = tx.send(Err(std::io::Error::new(
                            std::io::ErrorKind::Other,
                            e.to_string(),
                        )));
                        break;
                    }
                }
            }
        }
    }
}
