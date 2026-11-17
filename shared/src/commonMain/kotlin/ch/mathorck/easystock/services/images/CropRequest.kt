package ch.mathorck.easystock.services.images

/**
 * What to keep: [box] in the displayed image (the upright photo turned [quarterTurns] x 90 degrees
 * clockwise), in full-size pixels.
 */
data class CropRequest(val box: CropBox, val quarterTurns: Int)
