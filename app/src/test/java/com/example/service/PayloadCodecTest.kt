package com.example.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadCodecTest {
    @Test fun expandsTokensAndPreservesHttpBytes() {
        val actual = PayloadCodec.expand(
            "GET /x[crlf]Host: [host][crlf][crlf][split]",
            "front.example.test", 80, "DtproTest/1.0"
        )
        assertArrayEquals(
            "GET /x\r\nHost: front.example.test\r\n\r\n".toByteArray(Charsets.ISO_8859_1), actual
        )
    }

    @Test fun acceptsOnlyHttp101AsUpgrade() {
        assertEquals(HttpStatus.Upgrade(101), PayloadCodec.parseStatus("HTTP/1.1 101 Switching Protocols\r\n\r\n".toByteArray()))
        assertEquals(HttpStatus.Rejected(403), PayloadCodec.parseStatus("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray()))
        assertEquals(HttpStatus.Incomplete, PayloadCodec.parseStatus("HTTP/1.1 101 Switching".toByteArray()))
    }
}
