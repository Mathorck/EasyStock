package ch.mathorck.easystock.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ch.mathorck.easystock.composables.theme.EasyStockTheme
import ch.mathorck.easystock.services.prefs.LocalAppPreferences
import ch.mathorck.easystock.services.prefs.rememberAppPreferences
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource

@Composable
@Preview
fun App() {
    CompositionLocalProvider(LocalAppPreferences provides rememberAppPreferences()) {
        EasyStockTheme {
            MainComposable()
        }
    }
}