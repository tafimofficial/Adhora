use std::collections::HashMap;
use std::sync::atomic::{AtomicU16, Ordering};
use std::sync::Arc;
use tokio::sync::Mutex;
use grammers_client::client::{LoginToken, PasswordToken};
use grammers_client::Client;
use grammers_session::types::PeerRef;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct DialogDto {
    pub id: i64,
    pub title: String,
    pub username: Option<String>,
    pub is_channel: bool,
    pub is_group: bool,
    pub is_admin: bool,
    pub is_creator: bool,
    pub unread_count: i32,
    pub thumb_url: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct MediaDto {
    pub id: i32,
    pub chat_id: i64,
    pub chat_title: String,
    pub media_type: String, // "video" | "photo"
    pub file_name: String,
    pub mime_type: String,
    pub size: usize,
    pub duration: i32,
    pub width: i32,
    pub height: i32,
    pub date: i64,
    pub caption: String,
    pub stream_url: String,
    pub thumb_url: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct AuthResponse {
    pub status: String, // "ok" | "2fa_required" | "error"
    pub message: Option<String>,
    pub hint: Option<String>,
    pub user_id: Option<i64>,
    pub user_name: Option<String>,
}

pub struct AppState {
    pub data_dir: String,
    pub cache_dir: String,
    pub http_port: AtomicU16,
    pub client: Arc<Mutex<Option<Client>>>,
    pub login_token: Arc<Mutex<Option<LoginToken>>>,
    pub password_token: Arc<Mutex<Option<PasswordToken>>>,
    pub cached_peers: Arc<Mutex<HashMap<i64, PeerRef>>>,
    pub cached_dialogs: Arc<Mutex<Vec<DialogDto>>>,
    pub cached_media_targets: Arc<Mutex<HashMap<(i64, i32), (usize, String, crate::tg::StreamTarget)>>>,
}

impl AppState {
    pub fn new(data_dir: String, cache_dir: String, port: u16) -> Self {
        Self {
            data_dir,
            cache_dir,
            http_port: AtomicU16::new(port),
            client: Arc::new(Mutex::new(None)),
            login_token: Arc::new(Mutex::new(None)),
            password_token: Arc::new(Mutex::new(None)),
            cached_peers: Arc::new(Mutex::new(HashMap::new())),
            cached_dialogs: Arc::new(Mutex::new(Vec::new())),
            cached_media_targets: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub fn port(&self) -> u16 {
        self.http_port.load(Ordering::SeqCst)
    }

    pub fn set_port(&self, port: u16) {
        self.http_port.store(port, Ordering::SeqCst);
    }
}
