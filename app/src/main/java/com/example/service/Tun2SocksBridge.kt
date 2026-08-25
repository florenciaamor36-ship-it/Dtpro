package com.example.service

import android.os.ParcelFileDescriptor

/** Starts the MIT-licensed tun2socks engine through the gomobile AAR.
 * Reflection keeps ordinary JVM/unit builds possible when the AAR is absent.
 */
class Tun2SocksBridge {
    private var engineClass: Class<*>? = null

    fun start(tun: ParcelFileDescriptor, socksPort: Int): Boolean = runCatching {
        val keyClass = load("com.example.tun2socks.engine.Key", "go.engine.Key")
        val engine = load("com.example.tun2socks.engine.Engine", "go.engine.Engine")
        val key = keyClass.getDeclaredConstructor().newInstance()
        keyClass.getMethod("setDevice", String::class.java).invoke(key, "fd://${tun.fd}")
        keyClass.getMethod("setProxy", String::class.java)
            .invoke(key, "socks5://127.0.0.1:$socksPort")
        keyClass.getMethod("setMTU", Int::class.javaPrimitiveType).invoke(key, 1500)
        keyClass.getMethod("setLogLevel", String::class.java).invoke(key, "info")
        engine.getMethod("insert", keyClass).invoke(null, key)
        engine.getMethod("start").invoke(null)
        engineClass = engine
        true
    }.getOrElse { false }

    fun stop() {
        runCatching { engineClass?.getMethod("stop")?.invoke(null) }
        engineClass = null
    }

    private fun load(vararg names: String): Class<*> = names.firstNotNullOfOrNull {
        runCatching { Class.forName(it) }.getOrNull()
    } ?: error("tun2socks AAR no disponible")
}
