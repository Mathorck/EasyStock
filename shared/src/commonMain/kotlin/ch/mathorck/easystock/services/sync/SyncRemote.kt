package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.services.server.AccountApi

/** The server calls of one connection. */
class SyncRemote(val sync: SyncApi, val account: AccountApi)
