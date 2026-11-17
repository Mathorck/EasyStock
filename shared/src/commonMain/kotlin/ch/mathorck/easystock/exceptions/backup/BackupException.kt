package ch.mathorck.easystock.exceptions.backup

import ch.mathorck.easystock.enums.backup.BackupError

class BackupException(val error: BackupError, message: String = error.name, cause: Throwable? = null) :
    Exception(message, cause)
