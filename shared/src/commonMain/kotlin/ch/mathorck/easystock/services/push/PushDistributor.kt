package ch.mathorck.easystock.services.push

/** A UnifiedPush distributor app installed on the phone (ntfy, NextPush…). [id]: its package name. */
data class PushDistributor(val id: String, val label: String)
