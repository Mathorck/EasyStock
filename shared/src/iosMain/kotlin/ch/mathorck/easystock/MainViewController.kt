package ch.mathorck.easystock

import androidx.compose.ui.window.ComposeUIViewController
import ch.mathorck.easystock.composables.App

fun MainViewController() = ComposeUIViewController { App() }