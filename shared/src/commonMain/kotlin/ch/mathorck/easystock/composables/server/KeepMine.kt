package ch.mathorck.easystock.composables.server

import ch.mathorck.easystock.enums.sync.RowKeepMineBlock

/** What "Keep mine" can do for a row conflict: not read yet, offered, or why not. */
internal sealed interface KeepMine {
    data object Loading : KeepMine

    data object Offered : KeepMine

    data class Blocked(val block: RowKeepMineBlock) : KeepMine
}
