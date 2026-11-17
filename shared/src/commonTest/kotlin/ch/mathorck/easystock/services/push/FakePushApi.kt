package ch.mathorck.easystock.services.push

import kotlinx.coroutines.awaitCancellation

internal class FakePushApi : PushApi {
    val calls = mutableListOf<String>()
    var fail = false
    var hang = false

    override suspend fun register(endpoint: String) {
        if (hang) awaitCancellation()
        if (fail) error("offline")
        calls += "register:$endpoint"
    }

    override suspend fun unregister() {
        if (fail) error("offline")
        calls += "unregister"
    }
}
