package com.adhora.app.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.adhora.app.ui.theme.ElectricLime
import com.adhora.app.ui.theme.PitchBlack
import com.adhora.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoStreamPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    isUiVisible: Boolean = true,
    onToggleUi: () -> Unit = {},
    bottomPadding: Dp = 80.dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by remember { mutableStateOf(isActive) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var seekSide by remember { mutableIntStateOf(0) }
    val currentOnToggleUi by rememberUpdatedState(onToggleUi)

    // Ultra-optimized streaming LoadControl:
    // - bufferForPlaybackMs = 500: Starts playing as soon as 0.5s is loaded (YouTube-like fast start)
    // - maxBufferMs = 6000: Buffers max 6s ahead so it does not waste mobile data on large videos
    val exoPlayer = remember(videoUrl) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 6000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = isActive
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            duration = this@apply.duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            }
    }

    // Stop playback and background streaming immediately when user swipes away or page is inactive!
    LaunchedEffect(isActive) {
        if (!isActive) {
            exoPlayer.pause()
            exoPlayer.playWhenReady = false
        } else {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    // Pause audio/video when screen locks or app goes to background
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                exoPlayer.playWhenReady = false
            } else if (event == Lifecycle.Event.ON_RESUME && isActive) {
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Complete cleanup on disposal
    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Position tracking ticker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide controls after 3.5s of playback when UI is visible
    LaunchedEffect(isUiVisible, isPlaying, isScrubbing) {
        if (isUiVisible && isPlaying && !isScrubbing) {
            delay(3500)
            currentOnToggleUi()
        }
    }

    // Dismiss seek feedback after 700ms
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700)
            seekFeedback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        currentOnToggleUi()
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        if (offset.x < width * 0.35f) {
                            // Rewind 10s
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPosition = target
                            seekFeedback = "◀◀ 10s"
                            seekSide = -1
                        } else if (offset.x > width * 0.65f) {
                            // Forward 10s
                            val dur = exoPlayer.duration.coerceAtLeast(0L)
                            val target = if (dur > 0) (exoPlayer.currentPosition + 10_000L).coerceAtMost(dur) else exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(target)
                            currentPosition = target
                            seekFeedback = "10s ▶▶"
                            seekSide = 1
                        } else {
                            // Center double-tap: Toggle Play / Pause
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering spinner
        if (isBuffering) {
            CircularProgressIndicator(
                color = ElectricLime,
                strokeWidth = 3.dp,
                modifier = Modifier.size(52.dp)
            )
        }

        // Seek feedback overlay pill
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(if (seekSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 40.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, ElectricLime)
            ) {
                Text(
                    text = seekFeedback ?: "",
                    color = ElectricLime,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Center Play/Pause button
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ElectricLime,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Bottom Scrubber Bar & Timers placed comfortably above caption
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = bottomPadding)
                ) {
                    // Time indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatTime(duration),
                                color = ElectricLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = ElectricLime,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Lime Slider Scrubber
                    Slider(
                        value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            isScrubbing = true
                            val target = (frac * duration).toLong()
                            exoPlayer.seekTo(target)
                            currentPosition = target
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricLime,
                            activeTrackColor = ElectricLime,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
