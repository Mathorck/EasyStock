package ch.mathorck.easystock.services.attributes

internal sealed interface Piece {
    data class Text(val text: String) : Piece
    data class Value(val text: String) : Piece
    data object Gap : Piece
}
