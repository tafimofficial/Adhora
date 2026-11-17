package com.adhora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adhora.app.engine.MediaItem
import com.adhora.app.ui.components.FullscreenMediaViewer
import com.adhora.app.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MediaFeedScreen(
    mediaList: List<MediaItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("all") } // "all", "video", "photo"
    var activeViewerIndex by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()

    // Back button handling:
    // 1. If Fullscreen viewer is open, Back closes the viewer
    BackHandler(enabled = activeViewerIndex != null) {
        activeViewerIndex = null
    }

    // 2. If viewing the grid, Back navigates back to Channels screen
    BackHandler(enabled = activeViewerIndex == null) {
        onBack()
    }

    val filteredList = remember(mediaList, selectedFilter) {
        when (selectedFilter) {
            "video" -> mediaList.filter { it.mediaType == "video" }
            "photo" -> mediaList.filter { it.mediaType == "photo" }
            else -> mediaList
        }
    }

    val videoCount = remember(mediaList) { mediaList.count { it.mediaType == "video" } }
    val photoCount = remember(mediaList) { mediaList.count { it.mediaType == "photo" } }

    // Automatic infinite scroll: load more when near the bottom
    LaunchedEffect(gridState, filteredList.size, isLoading, isLoadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= filteredList.size - 6 &&
                    !isLoading &&
                    !isLoadingMore &&
                    filteredList.isNotEmpty()
                ) {
                    onLoadMore()
                }
            }
    }

    Scaffold(
        containerColor = PitchBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PitchBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ElectricLime)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Unified Media Feed",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (isLoadingMore) "${filteredList.size} loaded • Loading more..." else "${filteredList.size} loaded • Scroll down for more",
                                color = if (isLoadingMore) ElectricLime else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricLime)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Media Type Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "all",
                        onClick = { selectedFilter = "all" },
                        label = { Text("All (${mediaList.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricLime,
                            selectedLabelColor = PitchBlack,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "video",
                        onClick = { selectedFilter = "video" },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedFilter == "video") PitchBlack else ElectricLime
                            )
                        },
                        label = { Text("Videos ($videoCount)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricLime,
                            selectedLabelColor = PitchBlack,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "photo",
                        onClick = { selectedFilter = "photo" },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedFilter == "photo") PitchBlack else ElectricLime
                            )
                        },
                        label = { Text("Photos ($photoCount)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricLime,
                            selectedLabelColor = PitchBlack,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading && mediaList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricLime)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading media from selected channels...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No media found in selected channels",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(filteredList, key = { _, item -> "${item.chatId}_${item.id}" }) { index, item ->
                        MediaGridThumbnail(
                            item = item,
                            onClick = { activeViewerIndex = index }
                        )
                    }

                    // Bottom item for infinite scroll status
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = ElectricLime,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Loading more media...",
                                        color = ElectricLime,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onLoadMore,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceCard,
                                        contentColor = ElectricLime
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = ElectricLime
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load 100 More", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen swipe viewer overlay with butter-smooth scale + fade transition
    AnimatedVisibility(
        visible = activeViewerIndex != null,
        enter = fadeIn(animationSpec = tween(180)) + scaleIn(
            initialScale = 0.93f,
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(
            targetScale = 0.93f,
            animationSpec = tween(150, easing = FastOutSlowInEasing)
        )
    ) {
        val currentIndex = activeViewerIndex
        if (currentIndex != null) {
            FullscreenMediaViewer(
                mediaList = filteredList,
                initialIndex = currentIndex,
                onClose = { activeViewerIndex = null }
            )
        }
    }
}

@Composable
fun MediaGridThumbnail(
    item: MediaItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(SurfaceDark)
            .clickable(onClick = onClick)
    ) {
        // Thumbnail Image
        AsyncImage(
            model = item.thumbUrl,
            contentDescription = item.caption.ifBlank { item.fileName },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay badges for videos
        if (item.mediaType == "video") {
            // Play icon badge in center
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = ElectricLime,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Duration Pill in bottom right
            if (item.duration > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = formatDuration(item.duration),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
