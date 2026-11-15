package com.aiverse.core.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.res.ResourcesCompat
import com.aiverse.core.util.FileLogger
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** 一个可用的字体条目：文件绝对路径 + 展示名（文件名去扩展名）。 */
data class FontEntry(
    val path: String,
    val displayName: String
)

/**
 * 通用字体库：把「导入的 ttf/otf/ttc 存放在 App 私有目录、按路径加载并缓存 Typeface」这套逻辑
 * 参数化目录名后下沉到 core，供终端字体与界面字体共用（各自独立目录互不干扰）。
 *
 * 不走 Hilt，调用方（设置面板 / 终端渲染）直接以 Context 构造使用。
 *
 * @param dirName 私有目录名（filesDir 下的子目录），如 terminal_fonts / ui_fonts。
 * @param builtinPath 可选的内置字体伪路径（如 terminal 的 JetBrains Mono）；null 表示无内置字体。
 * @param builtinResource 内置字体的资源 id，仅在 [builtinPath] 非空时使用。
 */
class FontLibrary(
    private val dirName: String,
    private val builtinPath: String? = null,
    private val builtinResource: Int? = null
) {
    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("ttf", "otf", "ttc")

        /** 导入体积上限（20MB），避免把超大/损坏字体拷进私有目录拖慢加载。 */
        const val MAX_IMPORT_BYTES = 20L * 1024 * 1024
    }

    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    fun fontsDir(context: Context): File =
        File(context.filesDir, dirName).apply { if (!exists()) mkdirs() }

    fun listFonts(context: Context): List<FontEntry> =
        fontsDir(context).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?.map { FontEntry(path = it.absolutePath, displayName = it.nameWithoutExtension) }
            ?: emptyList()

    /** 复制字体文件到私有目录并校验可解析；超过体积上限或无效时返回 null。 */
    fun importFont(context: Context, uri: Uri): FontEntry? {
        val fileName = queryDisplayName(context, uri) ?: return null
        if (File(fileName).extension.lowercase() !in SUPPORTED_EXTENSIONS) return null

        val target = File(fontsDir(context), fileName)
        return try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                copyBounded(input, target)
            }

            // null = 流不可读或超过体积上限，统一清理目标文件并放弃
            if (copied == null) {
                target.delete()
                FileLogger.e("FontLibrary", "导入字体失败: $fileName (无法读取或超过 ${MAX_IMPORT_BYTES / 1024 / 1024}MB)")
                return null
            }

            // 无效字体文件会在此抛异常，避免把坏文件留在目录里
            Typeface.createFromFile(target)
            typefaceCache.remove(target.absolutePath)
            FontEntry(path = target.absolutePath, displayName = target.nameWithoutExtension)
        } catch (e: Exception) {
            FileLogger.e("FontLibrary", "导入字体失败: $fileName", e)
            target.delete()
            null
        }
    }

    /** 拷贝到目标文件并限制体积；超过 [MAX_IMPORT_BYTES] 返回 null（调用方负责清理目标文件）。 */
    private fun copyBounded(input: InputStream, target: File): Long? {
        var total = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) return null
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    fun deleteFont(path: String): Boolean {
        typefaceCache.remove(path)
        return File(path).delete()
    }

    /** 加载字体；路径为空、内置路径无资源或文件损坏时返回 null，由调用方回落到系统默认。 */
    fun loadTypeface(context: Context, path: String): Typeface? {
        if (path.isBlank()) return null
        if (builtinPath != null && path == builtinPath) {
            val resource = builtinResource ?: return null
            typefaceCache[builtinPath]?.let { return it }
            return runCatching { ResourcesCompat.getFont(context, resource) }
                .onFailure { FileLogger.e("FontLibrary", "加载内置字体失败", it) }
                .getOrNull()
                ?.also { typefaceCache[builtinPath] = it }
        }
        typefaceCache[path]?.let { return it }
        val file = File(path)
        if (!file.isFile) return null
        return try {
            Typeface.createFromFile(file).also { typefaceCache[path] = it }
        } catch (e: Exception) {
            FileLogger.e("FontLibrary", "加载字体失败: $path", e)
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return sanitize(name)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { sanitize(it) }
    }

    private fun sanitize(name: String): String = name.replace(Regex("[/\\\\:\\s]+"), "_")
}

/**
 * 界面字体管理：导入的 ttf/otf/ttc 存放在 App 私有目录 ui_fonts，供外观设置「界面字体」与全局主题注入共用。
 * 不走 Hilt，设置面板直接以 Context 调用。
 */
object UiFontManager {

    private const val DIR_NAME = "ui_fonts"

    private val library = FontLibrary(dirName = DIR_NAME)

    fun fontsDir(context: Context): File = library.fontsDir(context)

    fun listFonts(context: Context): List<FontEntry> = library.listFonts(context)

    fun importFont(context: Context, uri: Uri): FontEntry? = library.importFont(context, uri)

    fun deleteFont(path: String): Boolean = library.deleteFont(path)

    fun loadTypeface(context: Context, path: String): Typeface? = library.loadTypeface(context, path)
}
