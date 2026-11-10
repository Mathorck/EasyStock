package ch.mathorck.easystock.data.db

import androidx.compose.runtime.Composable

/** Returns the single app database instance, created on first use. */
@Composable
expect fun rememberAppDatabase(): AppDatabase
