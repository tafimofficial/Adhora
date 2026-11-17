package com.adhora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adhora.app.engine.DialogItem
import com.adhora.app.engine.NativeEngine
import com.adhora.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(
    dialogs: List<DialogItem>,
    selectedIds: Set<Long>,
    onToggleChannel: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onSelectAdminOnly: () -> Unit,
    onDeselectAll: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onLogout: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Admin Only

    // Clear search on Back press if search is active
    BackHandler(enabled = searchQuery.isNotBlank()) {
        searchQuery = ""
    }

    val adminDialogs = remember(dialogs) {
        dialogs.filter { it.isAdmin || it.isCreator }
    }

    val currentList = remember(dialogs, selectedTab, searchQuery) {
        val base = if (selectedTab == 0) dialogs else adminDialogs
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        (it.username?.contains(searchQuery, ignoreCase = true) == true)
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
                // Header with App Name and Logout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ADHORA",
                            color = ElectricLime,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "${selectedIds.size} channels active",
                            color = if (selectedIds.isNotEmpty()) NeonLime else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricLime)
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search channels and groups...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = ElectricLime)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricLime,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricLime
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Row: All vs Admin / Managed
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = ElectricLime,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ElectricLime,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "All (${dialogs.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) ElectricLime else TextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Admin / Managed (${adminDialogs.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) ElectricLime else TextSecondary
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Select Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = onSelectAll,
                        label = { Text("Select All", fontSize = 11.sp, color = TextPrimary) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceCard)
                    )
                    FilterChip(
                        selected = false,
                        onClick = onSelectAdminOnly,
                        label = { Text("Admin Only", fontSize = 11.sp, color = ElectricLime) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceCard)
                    )
                    FilterChip(
                        selected = false,
                        onClick = onDeselectAll,
                        label = { Text("Clear All", fontSize = 11.sp, color = TextSecondary) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceCard)
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(160))
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToMedia,
                    containerColor = ElectricLime,
                    contentColor = PitchBlack,
                    shape = RoundedCornerShape(14.dp),
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Media Feed") },
                    text = {
                        Text(
                            text = "View Media (${selectedIds.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No channels matching '$searchQuery'" else "No channels found",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
            ) {
                items(currentList, key = { it.id }) { dialog ->
                    val isChecked = selectedIds.contains(dialog.id)
                    ChannelItemCard(
                        dialog = dialog,
                        isChecked = isChecked,
                        onToggle = { onToggleChannel(dialog.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelItemCard(
    dialog: DialogItem,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) SurfaceCard.copy(alpha = 0.95f) else SurfaceCard
        ),
        border = if (isChecked) borderStroke(1.dp, ElectricLime) else borderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Avatar / Placeholder
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (dialog.isAdmin || dialog.isCreator) ElectricLime.copy(alpha = 0.2f) else SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (dialog.thumbUrl.isNotEmpty()) {
                    AsyncImage(
                        model = dialog.thumbUrl,
                        contentDescription = dialog.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Text(
                    text = dialog.title.take(1).uppercase(),
                    color = if (dialog.isAdmin || dialog.isCreator) ElectricLime else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel Title & Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialog.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (dialog.isCreator) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElectricLime)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "OWNER",
                                color = PitchBlack,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else if (dialog.isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceBorder)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "ADMIN",
                                color = ElectricLime,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (dialog.isChannel) "Channel" else "Group",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    if (dialog.username != null) {
                        Text(
                            text = " • @${dialog.username}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (dialog.unreadCount > 0) {
                        Text(
                            text = " • ${dialog.unreadCount} new",
                            color = ElectricLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Switch Toggle
            Switch(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PitchBlack,
                    checkedTrackColor = ElectricLime,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceDark,
                    uncheckedBorderColor = SurfaceBorder
                )
            )
        }
    }
}

private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)
