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
                        name = "DTunnel Cloudflare WS (SSH)",
                        mode = TunnelMode.SSH_WEBSOCKET_SSL,
                        serverHost = "sg1.dtunnel.network",
                        serverPort = 443,
                        username = "dtunnel_user",
                        password = "demo_password",
                        sniHost = "cloudflare.com",
                        customPayload = "GET / HTTP/1.1[crlf]Host: sg1.dtunnel.network[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]",
                        isDefault = true
                    ),
                    TunnelConfig(
                        name = "SSH Direct Fast Node",
                        mode = TunnelMode.SSH_DIRECT,
                        serverHost = "us1.dtunnel.network",
                        serverPort = 22,
                        username = "free_ssh",
                        password = "password123",
                        isDefault = false
                    ),
                    TunnelConfig(
                        name = "HTTP Custom Payload Injection",
                        mode = TunnelMode.SSH_PAYLOAD,
                        serverHost = "br1.dtunnel.network",
                        serverPort = 80,
                        username = "brazil_ssh",
                        password = "secure_pass",
                        proxyHost = "104.16.132.229",
                        proxyPort = 80,
                        customPayload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]X-Online-Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]",
                        isDefault = false
                    ),
                    TunnelConfig(
                        name = "SSL / TLS Stunnel (SNI Bug)",
                        mode = TunnelMode.SSH_SSL,
                        serverHost = "eu1.dtunnel.network",
                        serverPort = 443,
                        username = "tls_user",
                        password = "password",
                        sniHost = "m.facebook.com",
                        isDefault = false
                    )
                )
                dao.insertAll(preloaded)
            }
        }
    }
}
