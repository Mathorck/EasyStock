package ch.mathorck.easystock.services.backup

/** Modules of a QR code, row by row. [size] x [size]. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    init {
        require(modules.size == size * size)
    }

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}
