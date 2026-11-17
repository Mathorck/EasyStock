package ch.mathorck.easystock.services.alerts

/**
 * Remembers that the phone was a Guest, so that a promotion pulled by a sync (even by the background job,
 * while the app was closed) is noticed at the next role change seen by the app.
 */
data class GuestMark(val wasGuest: Boolean, val ask: Boolean)
