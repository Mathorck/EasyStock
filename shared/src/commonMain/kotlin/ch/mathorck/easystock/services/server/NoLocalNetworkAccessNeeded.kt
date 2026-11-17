package ch.mathorck.easystock.services.server

/** Never missing: before Android 17, on iOS, in previews and tests. */
object NoLocalNetworkAccessNeeded : LocalNetworkAccess {
    override fun isMissing() = false

    override fun mustOpenSettings() = false

    override fun request() = Unit

    override fun openSettings() = Unit
}
