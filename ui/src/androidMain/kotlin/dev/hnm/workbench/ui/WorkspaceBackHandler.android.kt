package dev.hnm.workbench.ui
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
@Composable
actual fun WorkspaceBackHandler(onBack: () -> Unit) { BackHandler(onBack = onBack) }
