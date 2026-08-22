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

@Database(entities = [TunnelConfig::class], version = 2, exportSchema = false)
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
                val preloaded = listOf(
                    TunnelConfig(
                        name = "WebSocket SSL (Cloudflare)",
                        mode = TunnelMode.SSH_WEBSOCKET_SSL,
                        serverHost = "",
                        serverPort = 443,
                        username = "",
                        password = "",
                        sniHost = "cloudflare.com",
                        customPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]",
                        isDefault = true
                    ),
                    TunnelConfig(
                        name = "SSH Direct (TCP)",
                        mode = TunnelMode.SSH_DIRECT,
                        serverHost = "",
                        serverPort = 22,
                        username = "",
                        password = "",
                        isDefault = false
                    ),
                    TunnelConfig(
                        name = "HTTP Custom Payload",
                        mode = TunnelMode.SSH_PAYLOAD,
                        serverHost = "",
                        serverPort = 80,
                        username = "",
                        password = "",
                        customPayload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]",
                        isDefault = false
                    ),
                    TunnelConfig(
                        name = "SSL / TLS Stunnel (SNI)",
                        mode = TunnelMode.SSH_SSL,
                        serverHost = "",
                        serverPort = 443,
                        username = "",
                        password = "",
                        sniHost = "",
                        isDefault = false
                    )
                )
                dao.insertAll(preloaded)
            }
        }
    }
}
