package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DTunnel Manager", appName)
    }

    @Test
    fun `test database insert and retrieve config`() = runBlocking {
        val dao = db.tunnelConfigDao()
        val config = TunnelConfig(
            name = "Robolectric Server",
            mode = TunnelMode.SSH_DIRECT,
            serverHost = "1.2.3.4",
            serverPort = 22,
            username = "admin",
            password = "pwd",
            isDefault = true
        )
        val id = dao.insertConfig(config)
        val retrieved = dao.getConfigById(id)

        assertNotNull(retrieved)
        assertEquals("Robolectric Server", retrieved?.name)
        assertEquals("1.2.3.4", retrieved?.serverHost)
        assertEquals(TunnelMode.SSH_DIRECT, retrieved?.mode)

        val all = dao.getAllConfigs().first()
        assertEquals(1, all.size)
    }
}
