package ch.mathorck.easystock.services.push

internal class FakeConnector(var installed: List<PushDistributor> = listOf(NTFY)) : PushConnector {
    val calls = mutableListOf<String>()

    override fun distributors() = installed

    override fun use(distributor: String) {
        calls += "use:$distributor"
    }

    override fun register() {
        calls += "register"
    }

    override fun unregister() {
        calls += "unregister"
    }
}
