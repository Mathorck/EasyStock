package ch.mathorck.easystock

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform