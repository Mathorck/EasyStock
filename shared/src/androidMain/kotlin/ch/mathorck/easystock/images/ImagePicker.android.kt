package ch.mathorck.easystock.images

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

// Camera: TakePicture writes to a FileProvider URI, no CAMERA permission needed as long as the
// manifest doesn't declare it. Gallery: the system photo picker, no storage permission needed.

@Composable
actual fun rememberImagePicker(onPicked: (CropSource) -> Unit, onError: (ImageError) -> Unit): ImagePicker {
    // Previews have no activity to launch from. Constant for a given composition, so the branch is safe.
    if (LocalInspectionMode.current) return PreviewImagePicker
    val context = LocalContext.current.applicationContext
    val store = rememberAndroidImageStore()
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)
    val busy = remember { mutableStateOf(false) }

    // The image waits in temporary storage for the crop screen, which saves or discards it.
    // The camera already wrote to that file.
    fun pending(open: (() -> InputStream?)?) {
        busy.value = true
        scope.launch {
            withContext(NonCancellable) {
                val ok = try {
                    if (open != null) store.copyToPending(open)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                busy.value = false
                if (ok) picked(CropSource.Pending) else failed(ImageError.FAILED)
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pending(open = null) else store.captureFile().delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pending(open = { context.contentResolver.openInputStream(uri) })
    }

    return remember(camera, gallery, store) {
        object : ImagePicker {
            override val busy: Boolean get() = busy.value

            override fun takePhoto() {
                if (busy.value) return
                try {
                    // The capture file is fixed: nothing to remember if the activity is recreated meanwhile
                    camera.launch(store.captureUri(store.captureFile()))
                } catch (e: ActivityNotFoundException) {
                    failed(ImageError.NO_CAMERA)
                }
            }

            override fun pickFromGallery() {
                if (busy.value) return
                try {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: ActivityNotFoundException) {
                    failed(ImageError.FAILED)
                }
            }
        }
    }
}

private object PreviewImagePicker : ImagePicker {
    override val busy: Boolean = false

    override fun takePhoto() = Unit

    override fun pickFromGallery() = Unit
}
