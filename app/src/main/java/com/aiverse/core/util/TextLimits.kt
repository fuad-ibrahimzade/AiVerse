package com.aiverse.core.util

/**
 * 按 UTF-8 字节数截断字符串（角色名称、快捷入口文字图标等短文本输入通用）。
 *
 * 中文一个字占 3 字节、英文一个字母占 1 字节，按「字数」限制会让中文与英文的
 * 可输入长度差异悬殊；按字节限制后两边都能获得合理的输入余量（如 24 字节 ≈ 8 个中文
 * 或 24 个英文字母）。
 *
 * 按 UTF-8 编码宽度逐字符累加推进，不会把一个多字节字符切成半个（也不会拆开
 * emoji 的 surrogate 对）；[maxBytes] 非法时返回空串。
 */
fun String.truncateToUtf8Bytes(maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    var bytes = 0
    var index = 0
    for (char in this) {
        bytes += utf8ByteWidth(char)
        if (bytes > maxBytes) break
        index++
    }
    return if (index >= length) this else substring(0, index)
}

/** 单个 char 的 UTF-8 编码宽度：拉丁 1 字节、非 BMP 首/尾代理各按 3 字节计（合成 6 字节）。 */
private fun utf8ByteWidth(char: Char): Int = when {
    char.code <= 0x7F -> 1
    char.code <= 0x7FF -> 2
    else -> 3
}
