package ch.mathorck.easystock.services.images

/** Takes a photo or picks one from the gallery, then copies it into temporary storage for the crop screen. */
interface ImagePicker {
    /** True while a photo is being copied. */
    val busy: Boolean

    fun takePhoto()

    fun pickFromGallery()
}
