package ch.mathorck.easystock.enums.push

enum class PushStatus {
    /** Waiting for the distributor's address or for the server to save it. */
    WAITING,

    /** The server has the address: it wakes the app. */
    ACTIVE,

    /** The distributor refused: the 15-minute background sync is used. */
    FAILED,
}
