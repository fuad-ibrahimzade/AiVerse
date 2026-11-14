package com.aiverse.feature.agent.domain.role

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.data.local.entity.RoleEntity
import com.aiverse.feature.agent.domain.container.ContainerInstaller
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.agent.domain.prompt.PromptFragment
import com.aiverse.feature.agent.domain.prompt.PromptFragmentResolver
import com.aiverse.feature.workspace.domain.ProjectAiVerseRoot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.roleSeedDataStore by preferencesDataStore(
    name = "role_seed_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 角色系统的一次性数据迁移（第 8 条数据迁移，运行期 seeder）。
 *
 * 角色接手了原 00 号身份片段。升级 / 全新安装首次启动时，把 00 号的身份正文迁移进内置默认角色
 * [Role.DEFAULT_ROLE_ID]，并清理已迁移的自定义 00 覆盖文件，避免内容丢失与重复注入。
 *
 * 幂等：DataStore 标志位 [FLAG_MIGRATED] 置位后不再执行；任一步失败不置位，下次启动重试。
 * 升级与全新安装统一走此流程：升级时默认角色行由 Room 迁移插入，全新安装无该行则在此补建。
 */
@Singleton
class RoleSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val roleDao: RoleDao,
    private val containerInstaller: ContainerInstaller,
    private val projectAiVerseRoot: ProjectAiVerseRoot
) {
    private companion object {
        const val TAG = "RoleSeeder"
        const val ASSET_DIR = "prompts"
        const val CUSTOM_DIR = "prompts.custom"
        const val IDENTITY_NUMBER = 0
        val FLAG_MIGRATED = booleanPreferencesKey("role_00_migrated")
    }

    private val globalCustomDir: File
        get() = File(containerInstaller.aiverseDir, CUSTOM_DIR)

    /** 当前工作区的项目层覆盖目录；工作区未就绪时该目录不存在，扫描返回空。 */
    private val projectCustomDir: File
        get() = File(projectAiVerseRoot.current(), CUSTOM_DIR)

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (isMigrated()) return@withContext
        runCatching { seed() }
            .onSuccess { setMigrated() }
            .onFailure { FileLogger.w(TAG, "角色 00 身份迁移失败，将在下次启动重试", it) }
    }

    private suspend fun seed() {
        val now = System.currentTimeMillis()
        // ① 默认角色行：升级由迁移插入，全新安装缺失则补建（幂等）。
        val existing = roleDao.getById(Role.DEFAULT_ROLE_ID)
        val role = existing ?: RoleEntity(
            id = Role.DEFAULT_ROLE_ID,
            name = "默认角色",
            description = "",
            identityPrompt = "",
            tags = "",
            avatarKind = "NONE",
            avatarValue = "",
            enabled = true,
            isBuiltin = true,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now
        ).also { roleDao.upsert(it) }

        // ② 取 00 内容：优先用户自定义（全局层 > 项目层），都没有则回落内置默认身份。
        val customFiles = listOf(globalCustomDir, projectCustomDir)
            .flatMap { dir -> numberFiles(dir, IDENTITY_NUMBER) }
        val customBody = customFiles.firstNotNullOfOrNull { file ->
            readBody(file)?.takeIf { it.isNotBlank() }
        }
        val identity = customBody ?: readBuiltinIdentityBody()

        // ③ 写入默认角色身份：原为空（首次接管）或取到的是用户自定义内容时覆盖，保留用户自定义、不丢内容。
        if (!identity.isNullOrBlank() && (role.identityPrompt.isBlank() || customBody != null) && identity != role.identityPrompt) {
            roleDao.upsert(role.copy(identityPrompt = identity, updatedAt = System.currentTimeMillis()))
            FileLogger.i(TAG, "已把 00 号身份写入默认角色（来源：${if (customBody != null) "用户自定义" else "内置默认"}）")
        }

        // ④ 仅在成功读到并写入之后清理已迁移的自定义 00 覆盖文件，防止残留重复注入。
        var deleted = 0
        customFiles.forEach { if (it.exists() && it.delete()) deleted++ }
        if (deleted > 0) FileLogger.i(TAG, "已清理 $deleted 个已迁移的自定义 00 覆盖文件")
    }

    /** 目录顶层编号为 [number] 的覆盖文件，按文件名升序（同编号取首个）。 */
    private fun numberFiles(dir: File, number: Int): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && PromptFragmentResolver.parseNumber(it.name) == number }
            ?.sortedBy { it.name }
            .orEmpty()

    /** 读文件正文并去掉前导 `<!-- 描述 -->` 注释，读失败返回 null。 */
    private fun readBody(file: File): String? = runCatching {
        PromptFragment.bodyOf(file.readText())
    }.getOrNull()

    /** 内置默认身份：assets/prompts 顶层编号 0 的 md（兜底 00-identity.md）。 */
    private fun readBuiltinIdentityBody(): String? = runCatching {
        val names = context.assets.list(ASSET_DIR).orEmpty()
        val name = names.firstOrNull { PromptFragmentResolver.parseNumber(it) == IDENTITY_NUMBER }
            ?: "00-identity.md"
        val content = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        PromptFragment.bodyOf(content)
    }.getOrNull()

    private suspend fun isMigrated(): Boolean =
        context.roleSeedDataStore.data.first()[FLAG_MIGRATED] ?: false

    private suspend fun setMigrated() {
        context.roleSeedDataStore.edit { it[FLAG_MIGRATED] = true }
    }
}
