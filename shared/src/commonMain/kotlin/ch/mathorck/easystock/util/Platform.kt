package ch.mathorck.easystock.util

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform