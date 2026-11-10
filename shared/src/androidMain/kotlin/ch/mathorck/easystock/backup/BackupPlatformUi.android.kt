package ch.mathorck.easystock.backup

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.backup_camera_allow
import easystock.shared.generated.resources.backup_camera_denied
import easystock.shared.generated.resources.backup_camera_settings
import easystock.shared.generated.resources.backup_share_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// ---------- QR encode (ZXing) ----------

actual fun encodeQr(text: String): QrMatrix? = try {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
    val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = bits.width
    QrMatrix(size, BooleanArray(size * size) { bits[it % size, it / size] })
} catch (e: WriterException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

/** Reads a QR code from a luminance plane (camera Y plane). Null when none is found. */
fun decodeQr(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (e: NotFoundException) {
        null
    } catch (e: ReaderException) {
        null
    }
}

// ---------- QR scan (CameraX + ZXing) ----------

@Composable
actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(granted()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }

    if (permission) {
        CameraPreview(onScanned, modifier)
    } else if (asked) {
        Column(
            modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.backup_camera_denied), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(Res.string.backup_camera_allow)) }
            // Refused for good: only the system settings can allow it again
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // No settings app: nothing else to offer
                }
            }) { Text(stringResource(Res.string.backup_camera_settings)) }
        }
    } else {
        Box(modifier)
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanned by rememberUpdatedState(onScanned)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val done = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val cameras = future.get()
            provider = cameras
            // Dense codes (up to version ~37): a high resolution helps
            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                val text = image.use { readQr(it) }
                if (text != null && done.compareAndSet(false, true)) {
                    ContextCompat.getMainExecutor(context).execute { scanned(text) }
                }
            }
            try {
                cameras.unbindAll()
                cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // No back camera: the preview stays empty
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun readQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    // The Y plane may end before the last row's padding
    val needed = plane.rowStride * (image.height - 1) + image.width
    if (bytes.size < needed) return null
    return decodeQr(bytes, plane.rowStride, image.width, image.height)
}

// ---------- Files (SAF, share sheet) ----------

@Composable
actual fun rememberBackupFiles(
    onOpened: (ImportInput.File) -> Unit,
    onSaved: () -> Unit,
    onError: (BackupError) -> Unit,
): BackupFiles {
    val context = LocalContext.current
    val service = remember(context) { backupService(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val opened by rememberUpdatedState(onOpened)
    val saved by rememberUpdatedState(onSaved)
    val failed by rememberUpdatedState(onError)
    // The export to save once the user chose a place (kept while the picker is open)
    val pending = remember { mutableStateOf<ExportedBackup?>(null) }

    suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: BackupException) {
            failed(e.error)
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val export = pending.value
        pending.value = null
        if (uri != null && export != null) {
            scope.launch { attempt { service.copyOut(export) { context.contentResolver.openOutputStream(uri, "wt") }; saved() } }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { attempt { opened(service.copyIn { context.contentResolver.openInputStream(uri) }) } }
    }

    return remember(create, open, service) {
        object : BackupFiles {
            override fun save(export: ExportedBackup) {
                pending.value = export
                try {
                    create.launch(export.fileName)
                } catch (e: ActivityNotFoundException) {
                    pending.value = null
                    failed(BackupError.FAILED)
                }
            }

            override fun share(export: ExportedBackup) {
                scope.launch {
                    attempt {
                        val file = service.shareCopy(export)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(export.fileName, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(send, getString(Res.string.backup_share_title)))
                        } catch (e: ActivityNotFoundException) {
                            failed(BackupError.FAILED)
                        }
                    }
                }
            }

            override fun open() {
                try {
                    // Any type: a backup received by mail or messenger often has a generic one
                    open.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    failed(BackupError.FAILED)
                }
            }
        }
    }
}

@Composable
actual fun rememberIsChangingConfigurations(): () -> Boolean {
    val context = LocalContext.current
    return { context.findActivity()?.isChangingConfigurations == true }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
