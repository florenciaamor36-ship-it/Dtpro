package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import java.io.Closeable

/**
 * Small, lifecycle-safe MINA SSH connection wrapper.
 * Host-key verification is intentionally left to MINA's configured verifier;
 * this class never disables it or silently accepts unknown keys.
 */
class MinaSshEngine(
    private val logger: (String) -> Unit = {}
) : Closeable {
    private var client: SshClient? = null
    private var session: ClientSession? = null

    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        timeoutMillis: Long = 20_000
    ) = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "SSH host is blank" }
        require(username.isNotBlank()) { "SSH username is blank" }
        require(port in 1..65535) { "SSH port is invalid" }
        close()

        val ssh = SshClient.setUpDefaultClient()
        client = ssh
        try {
            ssh.start()
            val connected = ssh.connect(username, host, port).verify(timeoutMillis).session
            session = connected
            if (password.isNotEmpty()) connected.addPasswordIdentity(password)
            connected.auth().verify(timeoutMillis)
            logger("MINA SSH autenticado correctamente")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun isConnected(): Boolean = session?.isOpen == true && session?.isAuthenticated == true

    fun currentSession(): ClientSession? = session

    override fun close() {
        try { session?.close(false) } catch (_: Throwable) {}
        try { client?.stop() } catch (_: Throwable) {}
        session = null
        client = null
    }
}
