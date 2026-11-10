package ch.mathorck.stockapp2

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform