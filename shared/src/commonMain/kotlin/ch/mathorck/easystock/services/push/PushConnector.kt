package ch.mathorck.easystock.services.push

/** The UnifiedPush connector library (Android), behind an interface. */
interface PushConnector {
    fun distributors(): List<PushDistributor>

    /** Saves [distributor] as the one to use and asks it for an address. */
    fun use(distributor: String)

    /** Asks the saved distributor again (it answers with the same or a new address). */
    fun register()

    fun unregister()
}
