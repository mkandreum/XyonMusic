package com.xyonmusic.player.data

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Lee la letra incrustada en el archivo (frame ID3v2.3/2.4 "USLT", la que guardan
 * Mp3tag, MusicBrainz Picard, iTunes…). Devuelve null si el archivo no tiene letra.
 */
object Lyrics {
    private const val MAX_TAG = 24 * 1024 * 1024

    fun read(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { parse(it) }
    }.getOrNull()

    private fun parse(ins: InputStream): String? {
        val head = ins.readN(10) ?: return null
        if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return null
        val ver = head[3].toInt()
        if (ver < 3) return null
        val size = synchsafe(head, 6)
        if (size <= 0 || size > MAX_TAG) return null
        val tag = ins.readN(size) ?: return null
        var p = 0
        if (head[5].toInt() and 0x40 != 0) { // cabecera extendida
            p += if (ver == 4) synchsafe(tag, 0) else int32(tag, 0) + 4
        }
        while (p + 10 <= tag.size) {
            if (tag[p].toInt() == 0) break
            val id = String(tag, p, 4, Charsets.ISO_8859_1)
            val fs = if (ver == 4) synchsafe(tag, p + 4) else int32(tag, p + 4)
            if (fs <= 0 || p + 10 + fs > tag.size) break
            if (id == "USLT" && fs > 5) {
                val body = tag.copyOfRange(p + 10, p + 10 + fs)
                decodeUslt(body)?.let { return it }
            }
            p += 10 + fs
        }
        return null
    }

    private fun decodeUslt(b: ByteArray): String? {
        val enc = b[0].toInt()
        val cs: Charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        var i = 4 // encoding (1) + idioma (3)
        // Saltar el descriptor (terminado en 0x00, o 0x00 0x00 en UTF-16)
        if (enc == 1 || enc == 2) {
            while (i + 1 < b.size && !(b[i].toInt() == 0 && b[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < b.size && b[i].toInt() != 0) i++
            i += 1
        }
        if (i >= b.size) return null
        val text = String(b, i, b.size - i, cs).replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n').trim()
        return text.ifEmpty { null }
    }

    private fun InputStream.readN(n: Int): ByteArray? {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(out, off, n - off)
            if (r < 0) return null
            off += r
        }
        return out
    }

    private fun synchsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
