pub mod server;
pub mod state;
pub mod tg;

use std::sync::Arc;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::state::{AppState, AuthResponse};

static RUNTIME: Lazy<tokio::runtime::Runtime> = Lazy::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(4)
        .enable_all()
        .build()
        .expect("Failed to initialize Tokio runtime")
});

static GLOBAL_STATE: Lazy<std::sync::Mutex<Option<Arc<AppState>>>> =
    Lazy::new(|| std::sync::Mutex::new(None));

fn get_state() -> Option<Arc<AppState>> {
    let lock = GLOBAL_STATE.lock().unwrap();
    lock.clone()
}

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_initEngine(
    mut env: JNIEnv,
    _class: JClass,
    data_dir: JString,
    cache_dir: JString,
) -> jint {
    let data_str: String = match env.get_string(&data_dir) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    let cache_str: String = match env.get_string(&cache_dir) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    let _ = android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("AdhoraEngine"),
    );

    let mut lock = GLOBAL_STATE.lock().unwrap();
    if let Some(ref existing) = *lock {
        return existing.port() as jint;
    }

    let state = Arc::new(AppState::new(data_str, cache_str, 0));
    *lock = Some(Arc::clone(&state));

    let port = server::start_http_server(Arc::clone(&state), RUNTIME.handle().clone());
    state.set_port(port);

    log::info!("AdhoraEngine initialized, port={}", port);
    port as jint
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_isLoggedIn(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let state = match get_state() {
        Some(s) => s,
        None => return 0,
    };

    let authed = RUNTIME.block_on(async {
        tg::check_auth(&state).await
    });

    if authed { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_requestLoginCode(
    mut env: JNIEnv,
    _class: JClass,
    api_id: jint,
    api_hash: JString,
    phone: JString,
) -> jstring {
    let hash_str: String = match env.get_string(&api_hash) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    let phone_str: String = match env.get_string(&phone) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let state = match get_state() {
        Some(s) => s,
        None => return to_jstring(&mut env, "{\"status\": \"error\", \"message\": \"Engine not ready\"}"),
    };

    let res: AuthResponse = RUNTIME.block_on(async {
        tg::request_login_code(&state, api_id as i32, &hash_str, &phone_str).await
    });

    let json = serde_json::to_string(&res).unwrap_or_else(|_| "{}".to_string());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_signIn(
    mut env: JNIEnv,
    _class: JClass,
    code: JString,
) -> jstring {
    let code_str: String = match env.get_string(&code) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let state = match get_state() {
        Some(s) => s,
        None => return to_jstring(&mut env, "{\"status\": \"error\", \"message\": \"Engine not ready\"}"),
    };

    let res: AuthResponse = RUNTIME.block_on(async {
        tg::sign_in(&state, &code_str).await
    });

    let json = serde_json::to_string(&res).unwrap_or_else(|_| "{}".to_string());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_checkPassword(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
) -> jstring {
    let pw_str: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let state = match get_state() {
        Some(s) => s,
        None => return to_jstring(&mut env, "{\"status\": \"error\", \"message\": \"Engine not ready\"}"),
    };

    let res: AuthResponse = RUNTIME.block_on(async {
        tg::check_password(&state, &pw_str).await
    });

    let json = serde_json::to_string(&res).unwrap_or_else(|_| "{}".to_string());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_logout(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let state = match get_state() {
        Some(s) => s,
        None => return 0,
    };

    let ok = RUNTIME.block_on(async {
        tg::logout(&state).await
    });

    if ok { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_getDialogs(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let state = match get_state() {
        Some(s) => s,
        None => return to_jstring(&mut env, "[]"),
    };

    let res = RUNTIME.block_on(async {
        tg::fetch_dialogs(&state).await
    });

    let json = match res {
        Ok(dialogs) => serde_json::to_string(&dialogs).unwrap_or_else(|_| "[]".to_string()),
        Err(e) => format!("{{\"error\": \"{}\"}}", e),
    };

    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_getMedia(
    mut env: JNIEnv,
    _class: JClass,
    channel_ids_json: JString,
    limit: jint,
    offset_id: jint,
) -> jstring {
    let ids_str: String = match env.get_string(&channel_ids_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };

    let channel_ids: Vec<i64> = serde_json::from_str(&ids_str).unwrap_or_default();

    let state = match get_state() {
        Some(s) => s,
        None => return to_jstring(&mut env, "[]"),
    };

    let res = RUNTIME.block_on(async {
        tg::fetch_media_for_channels(&state, &channel_ids, limit as usize, offset_id as i32).await
    });

    let json = match res {
        Ok(media) => serde_json::to_string(&media).unwrap_or_else(|_| "[]".to_string()),
        Err(e) => format!("{{\"error\": \"{}\"}}", e),
    };

    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_getHttpPort(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    match get_state() {
        Some(s) => s.port() as jint,
        None => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_adhora_app_engine_NativeEngine_downloadMediaToGallery(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    msg_id: jint,
    dest_path: JString,
) -> jstring {
    let dest_str: String = match env.get_string(&dest_path) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let state = match get_state() {
        Some(s) => s,
        None => return to_jstring(&mut env, "{\"status\": \"error\", \"message\": \"Engine not ready\"}"),
    };

    let res = RUNTIME.block_on(async {
        let (info_res, target_opt) = tg::get_media_stream_info(&state, chat_id, msg_id).await;
        let (_size, _) = info_res.map_err(|e| e)?;
        let target = target_opt.ok_or_else(|| "Target media not found".to_string())?;

        let client_opt = {
            let lock = state.client.lock().await;
            lock.clone()
        };
        let client = client_opt.ok_or_else(|| "Client not ready".to_string())?;

        let dest = std::path::Path::new(&dest_str);
        if let Some(parent) = dest.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        match target {
            tg::StreamTarget::Doc(doc) => {
                client.download_media(&doc, dest).await.map_err(|e| e.to_string())?;
            }
            tg::StreamTarget::Photo(photo) => {
                client.download_media(&photo, dest).await.map_err(|e| e.to_string())?;
            }
        }

        Ok::<(), String>(())
    });

    let result_json = match res {
        Ok(_) => "{\"status\": \"ok\", \"message\": \"Saved to Gallery\"}".to_string(),
        Err(e) => format!("{{\"status\": \"error\", \"message\": \"{}\"}}", e),
    };

    to_jstring(&mut env, &result_json)
}
