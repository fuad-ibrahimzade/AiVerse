package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.defaultRoleDataStore by preferencesDataStore(
    name = "default_role_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 持久化「新会话默认角色」。
 *
 * 用户每次切换会话角色时，除绑定当前会话外还会写入此处（切回默认角色时写入 null 即清除）；
 * 之后新建会话默认沿用该角色（见 AIAgentViewModel.createSession）。未设置时新建会话不绑定，
 * 回退默认角色。DataStore 用法与 [DefaultModelSettingsRepository] 一致。
 */
@Singleton
class DefaultRoleSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val ROLE_ID_KEY = stringPreferencesKey("default_role_id")
    }

    /** 当前持久化的新会话默认角色 id 流；null 表示未设置（新建会话回落默认角色）。 */
    val roleIdFlow: Flow<String?> = context.defaultRoleDataStore.data.map { it[ROLE_ID_KEY] }

    /** 写入新会话默认角色；[roleId] 为 null 表示清除（新建会话回落默认角色）。 */
    suspend fun setDefaultRole(roleId: String?) {
        context.defaultRoleDataStore.edit {
            if (roleId == null) it.remove(ROLE_ID_KEY)
            else it[ROLE_ID_KEY] = roleId
        }
    }

    /** 读取一次当前新会话默认角色 id（冷读用）；null 表示未设置。 */
    suspend fun getDefaultRoleId(): String? = roleIdFlow.first()
}
