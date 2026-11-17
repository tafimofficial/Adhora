package com.adhora.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.adhora.app.engine.DialogItem
import com.adhora.app.engine.MediaItem
import com.adhora.app.engine.NativeEngine
import com.adhora.app.ui.screens.ChannelsScreen
import com.adhora.app.ui.screens.LoginScreen
import com.adhora.app.ui.screens.MediaFeedScreen
import com.adhora.app.ui.theme.AdhoraTheme
import com.adhora.app.ui.theme.ElectricLime
import com.adhora.app.ui.theme.PitchBlack
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

enum class Screen {
    LOADING,
    LOGIN,
    CHANNELS,
    MEDIA_FEED
}

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences("adhora_prefs", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AdhoraTheme {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf(Screen.LOADING) }
                var dialogs by remember { mutableStateOf<List<DialogItem>>(emptyList()) }
                var selectedChannelIds by remember { mutableStateOf<Set<Long>>(loadSavedChannels()) }
                var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                var isMediaLoading by remember { mutableStateOf(false) }
                var isMediaLoadingMore by remember { mutableStateOf(false) }
                var lastBackPressTime by remember { mutableLongStateOf(0L) }

                fun refreshDialogs() {
                    lifecycleScope.launch {
                        val items = NativeEngine.loadDialogs()
                        dialogs = items
                    }
                }

                fun loadMediaForSelected(initial: Boolean = true) {
                    if (selectedChannelIds.isEmpty()) {
                        mediaList = emptyList()
                        return
                    }
                    if (initial) {
                        isMediaLoading = true
                    } else {
                        if (isMediaLoadingMore) return
                        isMediaLoadingMore = true
                    }

                    lifecycleScope.launch {
                        val offset = if (initial) 0 else (mediaList.lastOrNull()?.id ?: 0)
                        val items = NativeEngine.loadMedia(selectedChannelIds.toList(), limit = 100, offsetId = offset)
                        val dialogMap = dialogs.associateBy { it.id }
                        items.forEach { m ->
                            m.chatTitle = dialogMap[m.chatId]?.title ?: ""
                        }

                        if (initial) {
                            mediaList = items
                            isMediaLoading = false
                        } else {
                            if (items.isNotEmpty()) {
                                val existingIds = mediaList.map { it.id }.toSet()
                                val newUniqueItems = items.filter { !existingIds.contains(it.id) }
                                mediaList = mediaList + newUniqueItems
                            }
                            isMediaLoadingMore = false
                        }
                    }
                }

                // Double back to exit on Channels screen
                BackHandler(enabled = currentScreen == Screen.CHANNELS) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000L) {
                        finish()
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(context, "Press back again to exit ADHORA", Toast.LENGTH_SHORT).show()
                    }
                }

                // Back to channels when in media feed
                BackHandler(enabled = currentScreen == Screen.MEDIA_FEED) {
                    currentScreen = Screen.CHANNELS
                }

                LaunchedEffect(Unit) {
                    NativeEngine.init(applicationContext)
                    val authed = NativeEngine.checkIsLoggedIn()
                    if (authed) {
                        currentScreen = Screen.CHANNELS
                        refreshDialogs()
                    } else {
                        currentScreen = Screen.LOGIN
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PitchBlack)
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == Screen.MEDIA_FEED && initialState == Screen.CHANNELS) {
                                // Forward navigation: slide in from right + fade in
                                (slideInHorizontally(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() }
                                ) + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() }
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                            } else if (targetState == Screen.CHANNELS && initialState == Screen.MEDIA_FEED) {
                                // Backward navigation: slide in from left + fade in
                                (slideInHorizontally(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    initialOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() }
                                ) + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() }
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                            } else {
                                fadeIn(animationSpec = tween(220)).togetherWith(fadeOut(animationSpec = tween(160)))
                            }
                        },
                        label = "ScreenTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { screen ->
                        when (screen) {
                            Screen.LOADING -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = ElectricLime)
                                }
                            }

                            Screen.LOGIN -> {
                                LoginScreen(
                                    onLoginSuccess = {
                                        currentScreen = Screen.CHANNELS
                                        refreshDialogs()
                                    }
                                )
                            }

                            Screen.CHANNELS -> {
                                ChannelsScreen(
                                    dialogs = dialogs,
                                    selectedIds = selectedChannelIds,
                                    onToggleChannel = { id ->
                                        val newSet = if (selectedChannelIds.contains(id)) {
                                            selectedChannelIds - id
                                        } else {
                                            selectedChannelIds + id
                                        }
                                        selectedChannelIds = newSet
                                        saveChannels(newSet)
                                    },
                                    onSelectAll = {
                                        val newSet = dialogs.map { it.id }.toSet()
                                        selectedChannelIds = newSet
                                        saveChannels(newSet)
                                    },
                                    onSelectAdminOnly = {
                                        val newSet = dialogs.filter { it.isAdmin || it.isCreator }.map { it.id }.toSet()
                                        selectedChannelIds = newSet
                                        saveChannels(newSet)
                                    },
                                    onDeselectAll = {
                                        selectedChannelIds = emptySet()
                                        saveChannels(emptySet())
                                    },
                                    onRefresh = {
                                        refreshDialogs()
                                    },
                                    onNavigateToMedia = {
                                        currentScreen = Screen.MEDIA_FEED
                                        loadMediaForSelected(initial = true)
                                    },
                                    onLogout = {
                                        lifecycleScope.launch {
                                            NativeEngine.performLogout()
                                            selectedChannelIds = emptySet()
                                            saveChannels(emptySet())
                                            dialogs = emptyList()
                                            mediaList = emptyList()
                                            currentScreen = Screen.LOGIN
                                        }
                                    }
                                )
                            }

                            Screen.MEDIA_FEED -> {
                                MediaFeedScreen(
                                    mediaList = mediaList,
                                    isLoading = isMediaLoading,
                                    isLoadingMore = isMediaLoadingMore,
                                    onBack = { currentScreen = Screen.CHANNELS },
                                    onRefresh = { loadMediaForSelected(initial = true) },
                                    onLoadMore = { loadMediaForSelected(initial = false) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadSavedChannels(): Set<Long> {
        val json = prefs.getString("selected_channels", null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<Long>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveChannels(channels: Set<Long>) {
        prefs.edit().putString("selected_channels", gson.toJson(channels)).apply()
    }
}
