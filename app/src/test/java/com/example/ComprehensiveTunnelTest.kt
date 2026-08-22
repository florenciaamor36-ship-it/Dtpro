package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LogLevel
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.service.TunnelEngine
import com.example.util.ConfigExporter
import com.example.util.HwidManager
import com.example.util.PayloadGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComprehensiveTunnelTest {

    @Test
    fun testAllTunnelModesConfigAndExport() {
        val modes = listOf(
            TunnelMode.SSH_DIRECT,
            TunnelMode.SSH_PAYLOAD,
            TunnelMode.SSH_SSL,
            TunnelMode.SSH_WEBSOCKET,
            TunnelMode.SSH_WEBSOCKET_SSL,
            TunnelMode.DIRECT_PROXY
        )

        for (mode in modes) {
            val config = TunnelConfig(
                name = "Test ${mode.title}",
                mode = mode,
                serverHost = "192.168.1.1",
                serverPort = mode.defaultPort,
                username = "user_${mode.name.lowercase()}",
                password = "pass_${mode.name.lowercase()}",
                sniHost = "sni.test.com",
                customPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]",
                proxyHost = "10.0.0.1",
                proxyPort = 8080
            )

            // 1. Test Export e Import Abierto
            val exportedOpen = ConfigExporter.exportConfig(config.copy(isLocked = false))
            assertTrue("Debe iniciar con dtunnel://", exportedOpen.startsWith("dtunnel://"))
            val importedOpen = ConfigExporter.importConfig(exportedOpen)
            assertNotNull("Fallo al importar modo abierto ${mode.title}", importedOpen)
            assertEquals("El modo no coincide", mode, importedOpen?.mode)
            assertEquals("El usuario no coincide", config.username, importedOpen?.username)
            assertEquals("El SNI no coincide", config.sniHost, importedOpen?.sniHost)

            // 2. Test Export e Import Cerrado / Cifrado
            val exportedLocked = ConfigExporter.exportConfig(
                config.copy(
                    isLocked = true,
                    creatorNote = "Nota para ${mode.name}",
                    expiryTimestamp = System.currentTimeMillis() + 86400000L
                )
            )
            val importedLockedResult = ConfigExporter.importConfigDetailed(exportedLocked)
            assertTrue("Fallo importación detallada para modo cerrado ${mode.title}", importedLockedResult is ConfigExporter.ImportResult.Success)
            val lockedConfig = (importedLockedResult as ConfigExporter.ImportResult.Success).config
            assertTrue("Debe marcarse como cerrado", lockedConfig.isLocked)
            assertEquals("Usuario descifrado no coincide", config.username, lockedConfig.username)
            assertEquals("Contraseña descifrada no coincide", config.password, lockedConfig.password)
            assertEquals("Nota del creador preservada", "Nota para ${mode.name}", lockedConfig.creatorNote)
        }
    }

    @Test
    fun testPayloadGeneratorAllVariants() {
        val methods = listOf("CONNECT", "GET", "POST", "HEAD", "TRACE", "OPTIONS", "PUT")
        val injections = listOf("Front Inject", "Back Inject", "Normal", "Raw")

        for (m in methods) {
            for (inj in injections) {
                val payload = PayloadGenerator.generateCustomPayload(
                    method = m,
                    injectionType = inj,
                    customHost = "target.net",
                    useKeepAlive = true,
                    useUpgrade = true,
                    useOnlineHost = true
                )
                assertNotNull(payload)
                assertTrue("Payload no debe estar vacío", payload.isNotEmpty())
                assertTrue("Payload debe contener target.net", payload.contains("target.net"))
            }
        }
    }

    @Test
    fun testTunnelEngineLifecycleAndLogging() {
        val engine = TunnelEngine.instance
        engine.clearLogs()

        engine.log("Test Info Log", LogLevel.INFO)
        engine.log("Test Success Log", LogLevel.SUCCESS)
        engine.log("Test Warning Log", LogLevel.WARNING)
        engine.log("Test Error Log", LogLevel.ERROR)

        val currentLogs = engine.logs.value
        assertEquals("Debe tener 4 registros", 4, currentLogs.size)
        assertEquals("Primer log es INFO", LogLevel.INFO, currentLogs[0].level)
        assertEquals("Último log es ERROR", LogLevel.ERROR, currentLogs[3].level)

        engine.clearLogs()
        assertEquals("Registros limpiados", 0, engine.logs.value.size)
    }

    @Test
    fun testHwidManagerDeterminismAndMultiHwidList() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val myHwid = HwidManager.getHwid(context)

        // Múltiples HWIDs en lista separada por comas
        val multiHwidConfig = TunnelConfig(
            name = "Multi HWID Server",
            allowedHwids = "DT-0000-0000-0000-0000, $myHwid, DT-1234-5678-9012-3456"
        )
        val check = HwidManager.checkHwidPermission(context, multiHwidConfig)
        assertTrue("Mi HWID debe estar autorizado en lista múltiple", check.first)

        val unauthConfig = TunnelConfig(
            name = "Unauth Multi HWID Server",
            allowedHwids = "DT-0000-0000-0000-0000, DT-9999-9999-9999-9999"
        )
        val unauthCheck = HwidManager.checkHwidPermission(context, unauthConfig)
        assertFalse("Debe ser rechazado si mi HWID no está en la lista", unauthCheck.first)
    }
}
