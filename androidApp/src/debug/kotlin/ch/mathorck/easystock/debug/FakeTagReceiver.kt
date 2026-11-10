package ch.mathorck.easystock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ch.mathorck.easystock.nfc.TagEvent
import ch.mathorck.easystock.nfc.appNfcTags
import ch.mathorck.easystock.nfc.normalizeTagId

/**
 * Debug builds only: feeds a fake NFC tag to the app, like the platform reader does.
 * `adb shell am broadcast -n ch.mathorck.easystock/.debug.FakeTagReceiver --es id 04AABBCC`.
 * Without a valid `id`, sends a failed read. Runs on the main thread, as [appNfcTags] needs.
 */
class FakeTagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tagId = intent.getStringExtra("id")?.let(::normalizeTagId)
        val event = tagId?.let { TagEvent.Read(it) } ?: TagEvent.Failed
        val taken = appNfcTags.dispatch(event)
        Log.i("FakeTagReceiver", "$event taken=$taken")
    }
}
