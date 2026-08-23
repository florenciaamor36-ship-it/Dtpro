package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TunnelConfig::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tunnelConfigDao(): TunnelConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dtunnel_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialConfigs(database.tunnelConfigDao())
                    }
                }
            }
        }

        suspend fun populateInitialConfigs(dao: TunnelConfigDao) {
            if (dao.getCount() == 0) {
                val initialProfile = listOf(
                    TunnelConfig(
                        name = "Mi Configuración Manual",
                        mode = TunnelMode.SSH_PAYLOAD,
                        serverHost = "",
                        serverPort = 80,
                        proxyHost = "",
                        proxyPort = 80,
                        username = "",
                        password = "",
                        customPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]",
                        isDefault = true
                    )
                )
                dao.insertAll(initialProfile)
            }
        }
    }
}
