package ch.mathorck.easystock.services.sync

/** Wire names the app sets or reads itself. */
object WireFields {
    const val ID = "id"
    const val IMAGE_SHA256 = "imageSha256"
    const val IMAGE_PATH = "image_path"
    const val CREATED_BY = "created_by"
    const val DEVICE_ID = "device_id"

    /** Pull only: the row's `server_seq` on the server. Never part of an entity. */
    const val SERVER_SEQ = "serverSeq"

    /** Push only: the `server_seq` of the server version an edit or soft delete is based on (first change wins). */
    const val BASE_SEQ = "baseSeq"
}
