package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.auth.Action
import ch.mathorck.easystock.auth.Permissions
import ch.mathorck.easystock.auth.Role

/**
 * Remembers that the phone was a Guest, so that a promotion pulled by a sync (even by the background job,
 * while the app was closed) is noticed at the next role change seen by the app.
 */
data class GuestMark(val wasGuest: Boolean, val ask: Boolean)

/**
 * The next [GuestMark] for the stored role. [connected]: a `sync_state` row exists. [role]: the stored
 * role, null when this app version does not know it (nothing changes then). [GuestMark.ask] is true once:
 * when the role goes from Guest to one that receives alerts.
 */
fun nextGuestMark(wasGuest: Boolean, connected: Boolean, role: Role?): GuestMark = when {
    !connected -> GuestMark(wasGuest = false, ask = false)
    role == null -> GuestMark(wasGuest, ask = false)
    role == Role.GUEST -> GuestMark(wasGuest = true, ask = false)
    else -> GuestMark(wasGuest = false, ask = wasGuest && Permissions.can(Action.RECEIVE_ALERTS, true, role))
}

/** Who can turn the alerts on or off with `PATCH /me` (a Guest can only change the display name). */
fun canChooseAlerts(role: Role?): Boolean = role != null && role != Role.GUEST

/** The effective choice: the server default (null) is on for Admins and Editors, off for Viewers. */
fun alertsOn(role: Role?, preference: Boolean?): Boolean = preference ?: (canChooseAlerts(role) && role != Role.VIEWER)
