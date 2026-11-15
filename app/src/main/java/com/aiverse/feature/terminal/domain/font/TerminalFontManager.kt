package com.aiverse.feature.terminal.domain.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.aiverse.R
import com.aiverse.core.font.FontLibrary

/** 一个可用的终端字体：文件绝对路径 + 展示名（文件名去扩展名）。 */
data class TerminalFont(
    val path: String,
    val displayName: String
)

/**
 * 终端自定义字体管理：薄壳委托 [FontLibrary]（目录 terminal_fonts，含内置 JetBrains Mono），
 * 公开签名保持不变。不走 Hilt，设置面板与终端渲染两侧都直接以 Context 调用。
 */
object TerminalFontManager {

    /** 内置字体的伪路径。不是真实文件路径，凡是校验 fontPath 是否存在的地方都要先放行它。 */
    const val BUILTIN_PATH = "builtin:jetbrains-mono-nl"

    private const val DIR_NAME = "terminal_fonts"

    private val library = FontLibrary(
        dirName = DIR_NAME,
        builtinPath = BUILTIN_PATH,
        builtinResource = R.font.jetbrains_mono_nl
    )

    fun fontsDir(context: Context) = library.fontsDir(context)

    fun listFonts(context: Context): List<TerminalFont> =
        library.listFonts(context).map { TerminalFont(path = it.path, displayName = it.displayName) }

    /** 复制字体文件到私有目录并校验可解析；失败返回 null。 */
    fun importFont(context: Context, uri: Uri): TerminalFont? =
        library.importFont(context, uri)?.let { TerminalFont(path = it.path, displayName = it.displayName) }

    fun deleteFont(path: String): Boolean = library.deleteFont(path)

    /** 加载字体；路径为空或文件损坏时返回 null，由调用方回落到系统等宽字体。 */
    fun loadTypeface(context: Context, path: String): Typeface? = library.loadTypeface(context, path)
}
