package ch.mathorck.easystock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ch.mathorck.easystock.alerts.handleAlertIntent

// AppCompatActivity: needed for the per-app language below API 33
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A tapped low-stock notification: opens its article (not again after a rotation)
        if (savedInstanceState == null) handleAlertIntent(intent)
        addOnNewIntentListener { handleAlertIntent(it) }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
