package com.kirimly.app.network

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    private const val COLOR_DARK: Int = -0x1000000
    private const val COLOR_LIGHT: Int = -0x1

    /** Generates a high-contrast QR [ImageBitmap]; returns null for blank or unencodable content. */
    fun generate(content: String, sizePx: Int = 720): ImageBitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) COLOR_DARK else COLOR_LIGHT
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
        } catch (e: WriterException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Standard Wi-Fi join payload understood by the iOS Camera app. */
    fun buildWifiPayload(ssid: String, password: String, hidden: Boolean = false): String {
        val security = if (password.isEmpty()) "nopass" else "WPA"
        return buildString {
            append("WIFI:T:").append(security).append(';')
            append("S:").append(escape(ssid)).append(';')
            if (password.isNotEmpty()) append("P:").append(escape(password)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
    }

    private fun escape(value: String): String = buildString {
        for (char in value) {
            if (char == '\\' || char == ';' || char == ',' || char == ':' || char == '"') append('\\')
            append(char)
        }
    }
}
