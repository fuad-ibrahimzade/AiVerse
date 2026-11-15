package com.aiverse.feature.settings.data.repository

import android.content.Context
import android.net.Uri
import com.aiverse.core.util.savePickedImageToAppFiles
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_DEFAULT
import com.aiverse.feature.settings.domain.model.WelcomeEntry
import com.aiverse.feature.settings.domain.model.WelcomeIconKind
import com.aiverse.feature.settings.domain.model.buildDefaultWelcomeEntries
import com.aiverse.feature.settings.domain.model.defaultWelcomeEntries
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 新对话页可配置快捷入口（欢迎卡片）的存储与增删改。
 *
 * 单一真源：[entries] StateFlow；设置页与对话页订阅同一流，改动即时同步。
 * 持久化：SharedPreferences（[PREFS_NAME]）里存 JSON 列表（kotlinx.serialization）。
 * 自定义图片图标经 [saveIconImage] 落到 filesDir/[ICON_DIR]/ 下（不依赖 content URI 授权），
 * 条目里只存绝对路径；删除条目/恢复默认时同步清理图片文件。
 */
@Singleton
class WelcomeEntriesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 列表 JSON 编解码：忽略未知字段，便于向前兼容。 */
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(loadFromPrefs())

    /** 全部欢迎卡片快照（实时，含预置项与自定义项，按展示顺序）。 */
    val entries: StateFlow<List<WelcomeEntry>> = _entries.asStateFlow()

    /** 当前快照。 */
    fun load(): List<WelcomeEntry> = _entries.value

    /**
     * 导出当前快照为 JSON 字符串（供备份应用设置）。
     * 空列表时返回 null（视为无可备份内容，保持与其它设置段一致）。
     */
    fun entriesJsonSnapshot(): String? {
        val current = _entries.value
        if (current.isEmpty()) return null
        return runCatching { json.encodeToString(current) }.getOrNull()
    }

    /**
     * 从备份的 JSON 字符串恢复欢迎卡片（写入 SharedPreferences 并发布到 [entries]）。
     * 解析失败时静默忽略（保留当前状态），与 [loadFromPrefs] 的容错策略一致。
     */
    fun restoreFromJson(json: String) {
        runCatching { this.json.decodeFromString<List<WelcomeEntry>>(json) }
            .getOrNull()
            ?.let { persist(it) }
    }

    /**
     * 新增欢迎卡片。
     *
     * 达到 [MAX_ENTRIES] 上限时拒绝并返回 false（调用方可据此提示）；否则追加到末尾并返回 true。
     * 传空 id 时自动生成 UUID。
     */
    suspend fun add(entry: WelcomeEntry): Boolean {
        val current = _entries.value
        if (current.size >= MAX_ENTRIES) return false
        val normalized = if (entry.id.isBlank()) entry.copy(id = UUID.randomUUID().toString()) else entry
        persist(current + normalized)
        return true
    }

    /** 更新欢迎卡片（按 id 匹配；id 不存在时忽略）。 */
    suspend fun update(entry: WelcomeEntry) {
        val current = _entries.value
        if (current.none { it.id == entry.id }) return
        persist(current.map { if (it.id == entry.id) entry else it })
    }

    /** 删除欢迎卡片（按 id）；条目为图片图标时同步删除图片文件。 */
    suspend fun remove(id: String) {
        val current = _entries.value
        current.firstOrNull { it.id == id }?.let { deleteIconIfImage(it) }
        persist(current.filterNot { it.id == id })
    }

    /** 启用/停用欢迎卡片（按 id）。 */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = _entries.value
        persist(current.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /**
     * 移动欢迎卡片到目标索引（0 起，上下移）。
     * [toIndex] 越界时夹到合法区间；id 不存在或位置不变时忽略。
     */
    suspend fun move(id: String, toIndex: Int) {
        val current = _entries.value
        val from = current.indexOfFirst { it.id == id }
        if (from < 0) return
        val target = toIndex.coerceIn(0, current.lastIndex)
        if (from == target) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(target, item)
        persist(mutable)
    }

    /**
     * 恢复默认：清空（含删除所有图片图标文件）后写入 [defaultWelcomeEntries]。
     * 用户自定义项被移除，预置项按默认排序重建。
     */
    suspend fun restoreDefaults() {
        _entries.value.forEach { deleteIconIfImage(it) }
        persist(defaultWelcomeEntries(context))
    }

    /**
     * 把所选图片保存为欢迎卡片图标，写 filesDir/[ICON_DIR]/icon_<ts>.jpg。
     *
     * 返回写入文件的绝对路径（供存入 [WelcomeEntry.iconValue]）；失败返回 null。
     */
    suspend fun saveIconImage(uri: Uri): String? = savePickedImageToAppFiles(
        context = context,
        uri = uri,
        dirName = ICON_DIR,
        filePrefix = "icon"
    )

    /** 首次加载：无数据（或解析失败）时回退到预置项；否则返回持久化列表（含旧 emoji 数据迁移）。 */
    private fun loadFromPrefs(): List<WelcomeEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null)
        if (raw.isNullOrBlank()) return buildDefaultWelcomeEntries(context)
        return runCatching { json.decodeFromString<List<WelcomeEntry>>(raw) }
            .map { migrateLegacyEntries(it) }
            .getOrElse { buildDefaultWelcomeEntries(context) }
    }

    /**
     * 旧数据兼容：历史版本允许用户自定义入口使用 emoji 图标；现统一为 Feather 矢量图标，
     * 无法语义映射的 emoji 一律回退到 [VECTOR_ICON_DEFAULT]。
     */
    private fun migrateLegacyEntries(list: List<WelcomeEntry>): List<WelcomeEntry> =
        list.map { entry ->
            if (entry.iconKind == WelcomeIconKind.EMOJI) {
                entry.copy(iconKind = WelcomeIconKind.VECTOR, iconValue = VECTOR_ICON_DEFAULT)
            } else {
                entry
            }
        }

    /** 持久化 + 发布（单一真源）。 */
    private fun persist(list: List<WelcomeEntry>) {
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(list)).apply()
        _entries.value = list
    }

    /** 条目为图片图标时删除对应图片文件（忽略失败，避免残留文件阻塞流程）。 */
    private fun deleteIconIfImage(entry: WelcomeEntry) {
        if (entry.iconKind == WelcomeIconKind.IMAGE && entry.iconValue.isNotBlank()) {
            runCatching { File(entry.iconValue).delete() }
        }
    }

    private companion object {
        /** 欢迎卡片数量上限（含预置项与自定义项）。 */
        const val MAX_ENTRIES = 6

        const val PREFS_NAME = "welcome_entries_prefs"
        const val KEY_ENTRIES = "welcome_entries_json"

        /** 图片图标落盘子目录（位于 filesDir 下）。 */
        const val ICON_DIR = "welcome_icons"
    }
}
