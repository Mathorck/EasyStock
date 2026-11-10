package ch.mathorck.easystock.data.db

import androidx.compose.runtime.Composable

private val database: AppDatabase by lazy { buildAppDatabase(databaseBuilder()) }

@Composable
actual fun rememberAppDatabase(): AppDatabase = database
