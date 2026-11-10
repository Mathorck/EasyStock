package ch.mathorck.easystock.data.db

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Volatile
private var instance: AppDatabase? = null

/** Process-wide database, built once from the application context. */
fun appDatabase(context: Context): AppDatabase =
    instance ?: synchronized(AppDatabase::class) {
        instance ?: buildAppDatabase(databaseBuilder(context)).also { instance = it }
    }

@Composable
actual fun rememberAppDatabase(): AppDatabase {
    val context = LocalContext.current.applicationContext
    return remember(context) { appDatabase(context) }
}
