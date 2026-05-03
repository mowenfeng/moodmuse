package com.example.myapplication.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.cover.ui.CoverScreen
import com.example.myapplication.cover.viewmodel.CoverViewModel
import com.example.myapplication.domain.GenerationStatus
import com.example.myapplication.ui.components.PaywallDialog
import com.example.myapplication.ui.screens.GeneratingScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.ResultScreen
import com.example.myapplication.viewmodel.MoodMuseViewModel

private object Routes {
    const val Home = "home"
    const val Generating = "generating"
    const val Result = "result"
    const val Cover = "cover"
}

@Composable
fun MoodMuseNavHost(vm: MoodMuseViewModel) {
    val navController = rememberNavController()
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current
    var showPaywall by remember { mutableStateOf(false) }

    if (showPaywall) {
        PaywallDialog(
            onDismiss = { showPaywall = false },
            onMockPay = {
                vm.mockPay {
                    vm.requestDownloadUrl(
                        onSuccess = { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        onDenied = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                showPaywall = false
            }
        )
    }

    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            GenerationStatus.Generating -> navController.navigate(Routes.Generating) {
                popUpTo(Routes.Home) { inclusive = false }
                launchSingleTop = true
            }

            GenerationStatus.Completed -> navController.navigate(Routes.Result) {
                launchSingleTop = true
            }

            GenerationStatus.Failed -> {
                uiState.errorMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
                navController.navigate(Routes.Home) {
                    popUpTo(Routes.Home) { inclusive = true }
                    launchSingleTop = true
                }
            }

            GenerationStatus.Idle -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Home
    ) {
        composable(Routes.Home) {
            HomeScreen(
                emotionText = uiState.emotionText,
                errorMessage = uiState.errorMessage,
                onEmotionChange = vm::updateEmotionText,
                onQuickEmotionClick = vm::applyQuickEmotion,
                onOpenCover = { navController.navigate(Routes.Cover) },
                onGenerateClick = vm::generateMusic
            )
        }

        composable(Routes.Cover) {
            val coverVm: CoverViewModel = viewModel()
            val coverState by coverVm.uiState.collectAsState()
            CoverScreen(
                state = coverState,
                onBack = { navController.popBackStack() },
                onReferenceAudioUrlChange = coverVm::updateReferenceAudioUrl,
                onStylePromptChange = coverVm::updateStylePrompt,
                onLyricsChange = coverVm::updateLyrics,
                onPreprocessClick = coverVm::preprocess,
                onGenerateClick = coverVm::generateCover
            )
        }

        composable(Routes.Generating) {
            GeneratingScreen(taskId = uiState.taskId)
        }

        composable(Routes.Result) {
            ResultScreen(
                title = uiState.title.ifBlank { "未命名曲目" },
                previewUrl = uiState.previewUrl,
                isPaid = uiState.isExportPaid,
                onRegenerate = vm::generateMusic,
                onExportClick = {
                    if (uiState.isExportPaid) {
                        vm.requestDownloadUrl(
                            onSuccess = { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            onDenied = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        showPaywall = true
                    }
                }
            )
        }
    }
}
