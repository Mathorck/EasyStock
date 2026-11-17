package ch.mathorck.easystock.enums.auth

/** What a user can do in the app. The UI hides what is not allowed; the server checks everything again. */
enum class Action {
    /** Stock list, article and location details, search. */
    VIEW_STOCK,

    /** Movement history section of the article screen. */
    VIEW_HISTORY,

    /** Reviews screen and finished reviews. */
    VIEW_REVIEWS,

    /** Add, remove, transfer, lend, return stock. */
    STOCK_ACTION,

    /** Create and edit articles, units, locations, categories, attributes, name templates. */
    EDIT,

    /** Soft-delete articles, units, locations, categories, attributes. */
    DELETE,

    /** Link an NFC tag to an article or a location, or unlink it. */
    LINK_TAG,

    /** Start, count and finish a stock review. */
    RUN_REVIEW,

    /** Settings screens that change data: categories, attributes, locations, tags, backup import. */
    MANAGE_SETTINGS,

    /** Send local changes to the server (sync push). */
    PUSH_CHANGES,

    /** Low-stock alert notifications (a Viewer must turn them on, a Guest never gets them). */
    RECEIVE_ALERTS,

    /** Users, invites, printed QR code, server backup, server settings. */
    ADMINISTER,

    /** Raise a flag (a problem note) on an article: Admin, Member and Viewer. Resolve, edit and delete need EDIT. */
    FLAG_ARTICLE,
}
