package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.util.BatteryManagerHelper
import com.example.util.ConfigExporter
import com.example.util.FileHandlerHelper
import com.example.util.HwidManager
import com.example.util.PayloadGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun testPayloadGeneratorPlaceholders() {
        val payloadTemplate = "[method] [host_port] [protocol][crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
        val processed = PayloadGenerator.parsePayload(payloadTemplate, "server.example.com", 8080)

        assertTrue("Should replace [method] with CONNECT", processed.contains("CONNECT"))
        assertTrue("Should replace [host_port] with target host and port", processed.contains("server.example.com:8080"))
        assertTrue("Should replace [protocol] with HTTP/1.1", processed.contains("HTTP/1.1"))
        assertTrue("Should replace [crlf] with real newlines", processed.contains("\r\n"))
        assertTrue("Should replace [host] with target host", processed.contains("Host: server.example.com"))
    }

    @Test
    fun testPayloadGeneratorCustomBuilder() {
        val generated = PayloadGenerator.generateCustomPayload(
            method = "GET",
            injectionType = "Front Inject",
            customHost = "bug.operator.com",
            useKeepAlive = true,
            useUpgrade = true,
            useOnlineHost = true
        )

        assertTrue(generated.startsWith("GET http://bug.operator.com/ HTTP/1.1"))
        assertTrue(generated.contains("Host: bug.operator.com"))
        assertTrue(generated.contains("X-Online-Host: bug.operator.com"))
        assertTrue(generated.contains("Connection: Keep-Alive"))
        assertTrue(generated.contains("Upgrade: websocket"))
    }

    @Test
    fun testConfigExporterOpenProfile() {
        val originalConfig = TunnelConfig(
            id = 42,
            name = "Test Open Profile",
            mode = TunnelMode.SSH_WEBSOCKET_SSL,
            serverHost = "192.168.1.100",
            serverPort = 443,
            username = "vpnuser",
            password = "securepassword123",
            sniHost = "cloudflare.com",
            customPayload = "GET / HTTP/1.1[crlf]Host: cloudflare.com[crlf][crlf]",
            proxyHost = "10.0.0.1",
            proxyPort = 8080,
            dnsPrimary = "1.1.1.1",
            dnsSecondary = "1.0.0.1",
            autoReconnect = true,
            isUdpForwarding = false,
            isLocked = false
        )

        val exportedString = ConfigExporter.exportConfig(originalConfig)
        assertTrue("Exported string must start with dtunnel://", exportedString.startsWith("dtunnel://"))

        val importedConfig = ConfigExporter.importConfig(exportedString)
        assertNotNull("Imported config must not be null", importedConfig)
        assertEquals("Name must match", originalConfig.name, importedConfig?.name)
        assertEquals("Mode must match", originalConfig.mode, importedConfig?.mode)
        assertEquals("Host must match", originalConfig.serverHost, importedConfig?.serverHost)
        assertEquals("Port must match", originalConfig.serverPort, importedConfig?.serverPort)
        assertEquals("Username must match", originalConfig.username, importedConfig?.username)
        assertEquals("Password must match", originalConfig.password, importedConfig?.password)
        assertEquals("SNI must match", originalConfig.sniHost, importedConfig?.sniHost)
        assertEquals("Payload must match", originalConfig.customPayload, importedConfig?.customPayload)
        assertEquals("DNS Primary must match", originalConfig.dnsPrimary, importedConfig?.dnsPrimary)
        assertFalse("Must remain unlocked", importedConfig?.isLocked ?: true)
    }

    @Test
    fun testConfigExporterLockedAndEncryptedProfile() {
        val expiryTime = System.currentTimeMillis() + 86400000L // +1 día
        val lockedConfig = TunnelConfig(
            name = "VIP Server Encrypted",
            mode = TunnelMode.SSH_WEBSOCKET_SSL,
            serverHost = "vip.dtunnel.network",
            serverPort = 443,
            username = "admin_secret",
            password = "super_classified_password",
            customPayload = "GET / HTTP/1.1[crlf]Upgrade: websocket[crlf][crlf]",
            sniHost = "vip.sni.com",
            isLocked = true,
            expiryTimestamp = expiryTime,
            allowedHwids = "DT-1111-2222-3333-4444",
            creatorNote = "Perfil VIP válido por 24 horas"
        )

        val exported = ConfigExporter.exportConfig(lockedConfig)
        assertTrue("Must be dtunnel URI", exported.startsWith("dtunnel://"))

        val result = ConfigExporter.importConfigDetailed(exported)
        assertTrue("Import must succeed", result is ConfigExporter.ImportResult.Success)

        val imported = (result as ConfigExporter.ImportResult.Success).config
        assertTrue("Must be marked as locked", imported.isLocked)
        assertEquals("Username decrypted properly", "admin_secret", imported.username)
        assertEquals("Password decrypted properly", "super_classified_password", imported.password)
        assertEquals("Payload decrypted properly", "GET / HTTP/1.1[crlf]Upgrade: websocket[crlf][crlf]", imported.customPayload)
        assertEquals("Allowed HWID preserved", "DT-1111-2222-3333-4444", imported.allowedHwids)
        assertEquals("Creator note preserved", "Perfil VIP válido por 24 horas", imported.creatorNote)
        assertEquals("Expiry timestamp preserved", expiryTime, imported.expiryTimestamp)
    }

    @Test
    fun testHwidGenerationAndValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hwid = HwidManager.getHwid(context)

        assertNotNull("HWID must not be null", hwid)
        assertTrue("HWID must start with DT- prefix", hwid.startsWith("DT-"))
        assertEquals("Same device must return consistent HWID", hwid, HwidManager.getHwid(context))

        // Test HWID Authorization check
        val configWithMyHwid = TunnelConfig(
            name = "Auth Config",
            allowedHwids = hwid
        )
        val validCheck = HwidManager.checkHwidPermission(context, configWithMyHwid)
        assertTrue("Should be authorized when my HWID is present", validCheck.first)

        val configWithOtherHwid = TunnelConfig(
            name = "Forbidden Config",
            allowedHwids = "DT-9999-9999-9999-9999"
        )
        val invalidCheck = HwidManager.checkHwidPermission(context, configWithOtherHwid)
        assertFalse("Should be rejected when my HWID is not in list", invalidCheck.first)
    }

    @Test
    fun testExpiryValidation() {
        val futureTimestamp = System.currentTimeMillis() + 1000000L
        val validConfig = TunnelConfig(name = "Valid", expiryTimestamp = futureTimestamp)
        val validResult = HwidManager.checkExpiry(validConfig)
        assertTrue("Future timestamp must be valid", validResult.first)

        val pastTimestamp = System.currentTimeMillis() - 1000000L
        val expiredConfig = TunnelConfig(name = "Expired", expiryTimestamp = pastTimestamp)
        val expiredResult = HwidManager.checkExpiry(expiredConfig)
        assertFalse("Past timestamp must be expired", expiredResult.first)
        assertTrue("Must have expiry message", expiredResult.second.contains("expiró"))
    }

    @Test
    fun testBatteryAndWakeLockHelpers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        BatteryManagerHelper.setWakeLockEnabled(context, true)
        assertTrue("WakeLock should be enabled", BatteryManagerHelper.isWakeLockEnabled(context))

        BatteryManagerHelper.setBatterySaverEnabled(context, true)
        assertTrue("Battery saver should be enabled", BatteryManagerHelper.isBatterySaverEnabled(context))

        BatteryManagerHelper.acquireWakeLock(context)
        BatteryManagerHelper.releaseWakeLock()
    }

    @Test
    fun testFileSharingAndReading() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = TunnelConfig(
            name = "Test File Share",
            serverHost = "1.2.3.4",
            serverPort = 22,
            username = "root",
            password = "pwd"
        )

        val intent = FileHandlerHelper.shareConfigFile(context, config)
        assertNotNull("Sharing intent should not be null", intent)
        assertEquals("Intent action should be ACTION_SEND", android.content.Intent.ACTION_SEND, intent?.action)

        val exportedContent = ConfigExporter.exportConfig(config)
        val testFile = File(context.cacheDir, "test.dtun").apply { writeText(exportedContent) }
        val readBack = testFile.readText()
        val parsed = ConfigExporter.importConfig(readBack)
        assertEquals("Parsed name should match original", config.name, parsed?.name)
    }

    @Test
    fun testConfigExporterInvalidInput() {
        val result = ConfigExporter.importConfig("invalid_string_not_base64")
        assertNull("Invalid string should return null gracefully without crashing", result)
    }
}
