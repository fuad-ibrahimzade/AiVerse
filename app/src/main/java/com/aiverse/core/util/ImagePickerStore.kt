package com.aiverse.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** 落盘图标最长边默认值（像素）：头像/图标槽显示尺寸小，无需高分辨率。 */
private const val DEFAULT_MAX_EDGE = 512

/** 解码峰值像素上限，避免超大图撑爆内存。 */
private const val DECODE_MAX_PIXELS = 4_000_000L

/** 重编码 JPEG 的质量。 */
private const val JPEG_QUALITY = 85

/**
 * 把 content URI 指向的图片落盘到 App 私有目录，返回绝对路径；失败返回 null。
 *
 * content URI 的授权会过期，不能直接持久化；各功能（快捷入口图标、角色头像等）因此统一走
 * 「读字节 → 规整成 JPEG → 写 filesDir 子目录」这条路径，条目里只存绝对路径。
 *
 * @param context App 上下文。
 * @param uri 相册选择器返回的图片 URI。
 * @param dirName filesDir 下的子目录名。
 * @param filePrefix 文件名前缀，时间戳作为后缀。
 * @param maxEdge 最长边像素上限；按采样档位解码控峰值内存，再精确缩放到该尺寸。
 * @return 落盘文件的绝对路径；解码或写盘失败时为 null。
 */
suspend fun savePickedImageToAppFiles(
    context: Context,
    uri: Uri,
    dirName: String,
    filePrefix: String,
    maxEdge: Int = DEFAULT_MAX_EDGE
): String? {
    val bytes = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
    } ?: return null
    val encoded = withContext(Dispatchers.IO) { runCatching { normalizeToJpeg(bytes, maxEdge) }.getOrNull() }
        ?: return null
    val dir = File(context.filesDir, dirName)
    dir.mkdirs()
    val target = File(dir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
    val written = withContext(Dispatchers.IO) {
        runCatching {
            target.outputStream().use { it.write(encoded) }
            target.length() > 0
        }.getOrDefault(false)
    }
    return if (written) target.absolutePath else null
}

/**
 * 把原图字节规整成最长边不超过 [maxEdge] 的 JPEG。
 *
 * 先按采样档位解码控峰值内存，再用 Matrix 精确缩放到目标尺寸（不改方向，源图按原方向落盘）。
 */
private fun normalizeToJpeg(source: ByteArray, maxEdge: Int): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("无法读取图片尺寸")
    }
    var sample = 1
    while (sample < 32 &&
        (bounds.outWidth / (sample * 2) >= maxEdge || bounds.outHeight / (sample * 2) >= maxEdge ||
            bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > DECODE_MAX_PIXELS)
    ) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        source, 0, source.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: throw IllegalArgumentException("图片解码失败")
    val longest = maxOf(decoded.width, decoded.height)
    val scale = if (longest > maxEdge) maxEdge.toFloat() / longest else 1f
    val bitmap = if (scale < 1f) {
        val matrix = Matrix().apply { postScale(scale, scale) }
        val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (scaled != decoded) decoded.recycle()
        scaled
    } else {
        decoded
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    bitmap.recycle()
    return output.toByteArray()
}
