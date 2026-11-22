package com.freeplay.toolbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 把相册图片压缩成 data URI 内嵌到公告 JSON。
 *
 * 为什么内嵌：公告是纯文本文件，别人设备要能显示图片，
 * 图片必须跟着 JSON 一起走，不能引用你手机里的本地文件。
 * 压缩到 100KB 内，base64 后约 135KB，远低于 GitHub 单文件 1MB 限制。
 */
object ImageEmbed {

    private const val MAX_BYTES = 100 * 1024
    private const val MAX_WIDTH = 720

    fun toDataUri(context: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calcSampleSize(bounds.outWidth, bounds.outHeight, MAX_WIDTH)
            }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val scaled = if (raw.width > MAX_WIDTH) {
                val h = (raw.height.toFloat() * MAX_WIDTH / raw.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(raw, MAX_WIDTH, h, true)
            } else raw

            var quality = 78
            var bytes: ByteArray
            while (true) {
                val bos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                bytes = bos.toByteArray()
                if (bytes.size <= MAX_BYTES || quality <= 30) break
                quality -= 12
            }
            if (scaled !== raw) scaled.recycle()
            raw.recycle()

            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Throwable) {
            null
        }
    }

    private fun calcSampleSize(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var maxSide = maxOf(w, h)
        while (maxSide / sample > target * 2) sample *= 2
        return sample
    }

    /** 解码 data URI 为位图（供预览使用） */
    fun decodeDataUri(uri: String): Bitmap? = try {
        val idx = uri.indexOf(",")
        if (idx <= 0) null
        else {
            val b64 = uri.substring(idx + 1)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (_: Throwable) {
        null
    }
}
