package ch.mathorck.easystock.exceptions.backup

// Platform primitives for backups. Android: JDK (javax.crypto, java.util.zip), also used by the
// JVM host tests. iOS: not implemented (the iOS target is not supported), calls throw.

/** The authentication tag does not match: wrong key, or modified data. */
class CryptoAuthException : Exception("Authentication failed")
