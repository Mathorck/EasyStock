package ch.mathorck.easystock.services.images

/** The image to crop. */
sealed interface CropSource {
    /** A photo or gallery image just copied by the [ImagePicker], in temporary storage. At most one at a time. */
    data object Pending : CropSource

    /** The stored photo at [path] (see [newImagePath]). Never modified: the crop makes a new file. */
    data class Stored(val path: String) : CropSource
}
