package ch.mathorck.easystock.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** iOS is not supported: no reader, the Scan page shows NFC as unavailable. */
@Composable
actual fun NfcReaderHost(tags: NfcTags) {
    LaunchedEffect(tags) { tags.setAvailability(NfcAvailability.Unavailable) }
}

@Composable
actual fun rememberOpenNfcSettings(): () -> Unit = remember { {} }
