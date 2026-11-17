package ch.mathorck.easystock.composables.article

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.mathorck.easystock.enums.images.CropAspect
import ch.mathorck.easystock.enums.images.CropHandle
import ch.mathorck.easystock.services.images.CropPreview
import ch.mathorck.easystock.services.images.CropRequest
import ch.mathorck.easystock.services.images.dragCrop
import ch.mathorck.easystock.services.images.initialCrop
import ch.mathorck.easystock.services.images.minCropSize
import ch.mathorck.easystock.services.images.rotateRect90
import ch.mathorck.easystock.services.images.toCropBox
import ch.mathorck.easystock.services.images.withAspect

/** What the user is editing: the crop rectangle in the displayed frame, the rotation and the aspect lock. */
internal class CropState(private val preview: CropPreview) {
    var quarterTurns by mutableStateOf(0)
        private set
    var aspect by mutableStateOf(CropAspect.FREE)
        private set
    var rect by mutableStateOf(initialCrop(preview.width.toFloat(), preview.height.toFloat(), CropAspect.FREE))
        private set

    val frameWidth: Float get() = if (quarterTurns % 2 == 0) preview.width.toFloat() else preview.height.toFloat()
    val frameHeight: Float get() = if (quarterTurns % 2 == 0) preview.height.toFloat() else preview.width.toFloat()

    fun selectAspect(value: CropAspect) {
        aspect = value
        rect = withAspect(rect, value.ratio, frameWidth, frameHeight)
    }

    fun rotate() {
        rect = rotateRect90(rect, frameHeight)
        quarterTurns = (quarterTurns + 1) % 4
    }

    fun drag(handle: CropHandle, dx: Float, dy: Float) {
        rect = dragCrop(rect, handle, dx, dy, frameWidth, frameHeight, aspect.ratio, minCropSize(frameWidth, frameHeight))
    }

    fun request() = CropRequest(
        box = toCropBox(rect, frameWidth.toInt(), frameHeight.toInt()),
        quarterTurns = quarterTurns,
    )
}
