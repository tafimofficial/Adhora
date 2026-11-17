package com.adhora.app.ui.components

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adhora.app.engine.MediaItem
import com.adhora.app.engine.NativeEngine
import com.adhora.app.ui.theme.ElectricLime
import com.adhora.app.ui.theme.PitchBlack
import com.adhora.app.ui.theme.SurfaceDark
import com.adhora.app.ui.theme.TextPrimary
import com.adhora.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FullscreenMediaViewer(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0)),
        pageCount = { mediaList.size }
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var isUiVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
    ) {
        // Horizontal Pager for Left / Right Swipe Navigation
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            userScrollEnabled = true
        ) { page ->
            val item = mediaList.getOrNull(page)
            if (item != null) {
                if (item.mediaType == "video") {
                    // Instant video streaming via Rust engine HTTP proxy
                    val isCurrentPage = pagerState.currentPage == page
                    VideoStreamPlayer(
                        videoUrl = item.streamUrl,
                        modifier = Modifier.fillMaxSize(),
                        isActive = isCurrentPage,
                        isUiVisible = isUiVisible,
                        onToggleUi = { isUiVisible = !isUiVisible },
                        bottomPadding = if (item.caption.isNotBlank()) 95.dp else 55.dp
                    )
                } else {
                    // Original Photo with pinch-to-zoom & double-tap
                    ZoomableImageView(
                        imageUrl = item.streamUrl,
                        thumbUrl = item.thumbUrl,
                        onToggleUi = { isUiVisible = !isUiVisible }
                    )
                }
            }
        }

        val currentItem = mediaList.getOrNull(pagerState.currentPage)

        // Top Navigation Bar (Animated Show / Hide on Single Tap)
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                        color = ElectricLime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (currentItem != null && currentItem.chatTitle.isNotEmpty()) {
                        Text(
                            text = currentItem.chatTitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Download button
                    IconButton(
                        onClick = {
                            if (currentItem != null && !isDownloading) {
                                isDownloading = true
                                scope.launch {
                                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                    val adhoraDir = File(picturesDir, "ADHORA").apply { mkdirs() }
                                    val ext = if (currentItem.mediaType == "video") "mp4" else "jpg"
                                    val destFile = File(adhoraDir, "ADHORA_${currentItem.chatId}_${currentItem.id}.$ext")

                                    NativeEngine.downloadMediaFile(currentItem.chatId, currentItem.id, destFile.absolutePath)
                                    isDownloading = false
                                    Toast.makeText(context, "Saved to Gallery / ADHORA", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                color = ElectricLime,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download Original",
                                tint = ElectricLime
                            )
                        }
                    }
                }
            }
        }

        // Bottom Info Bar (Animated Show / Hide on Single Tap)
        AnimatedVisibility(
            visible = isUiVisible && currentItem != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (currentItem != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {}
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceDark)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (currentItem.mediaType == "video") "VIDEO • STREAM" else "PHOTO",
                                    color = ElectricLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (currentItem.size > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceDark)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = formatSize(currentItem.size),
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (currentItem.fileName.isNotEmpty()) {
                            Text(
                                text = currentItem.fileName,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                        }
                    }

                    if (currentItem.caption.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentItem.caption,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableImageView(
    imageUrl: String,
    thumbUrl: String,
    onToggleUi: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val currentOnToggleUi by rememberUpdatedState(onToggleUi)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scale > 1.05f) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { currentOnToggleUi() },
                                onDoubleTap = {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { currentOnToggleUi() },
                            onDoubleTap = {
                                scale = 2.5f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        )
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Media Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}
