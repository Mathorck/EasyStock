package ch.mathorck.easystock.enums.images

enum class ImageError {
    /** The image could not be read or saved. */
    FAILED,

    /** No camera app on the device. */
    NO_CAMERA,

    /** The camera permission was refused. */
    CAMERA_DENIED,
}
