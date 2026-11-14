package com.aiverse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.data.local.entity.RoleEntity
import com.aiverse.feature.agent.domain.model.AvatarKind
import com.aiverse.feature.agent.domain.model.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 角色保存状态：编辑页据此在保存中转圈、成功后回退、失败就地报错。 */
sealed interface RoleSaveState {
    data object Idle : RoleSaveState
    data object Saving : RoleSaveState
    data object Saved : RoleSaveState
    data class Failed(val message: String) : RoleSaveState
}

/**
 * 角色编辑页状态：把表单提交为一条 Role（新建生成 id，编辑保留原 id 与创建时间），经 RoleDao.upsert 落盘。
 *
 * 保存是写库（IO），完成后置 [RoleSaveState.Saved]，编辑页据此回调导航。重复点击保存时忽略，避免重复写库。
 */
@HiltViewModel
class RoleEditorViewModel @Inject constructor(
    private val roleDao: RoleDao
) : ViewModel() {

    private val _saveState = MutableStateFlow<RoleSaveState>(RoleSaveState.Idle)
    val saveState: StateFlow<RoleSaveState> = _saveState.asStateFlow()

    /**
     * 保存角色。
     *
     * @param existing 编辑既有角色时传入（保留 id / 创建时间 / 内置标记 / 排序）；null 表示新建。
     * @param draftId 新建时的目标 id（由编辑页生成并记住，用于保存成功后的定位）。
     */
    fun save(
        existing: Role?,
        draftId: String,
        name: String,
        description: String,
        identityPrompt: String,
        tags: List<String>,
        avatarKind: AvatarKind,
        avatarValue: String
    ) {
        if (_saveState.value is RoleSaveState.Saving) return
        _saveState.value = RoleSaveState.Saving
        viewModelScope.launch {
            val success = runCatching {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val role = Role(
                        id = existing?.id ?: draftId,
                        name = name,
                        description = description,
                        identityPrompt = identityPrompt,
                        tags = tags,
                        avatarKind = avatarKind,
                        avatarValue = avatarValue,
                        enabled = existing?.enabled ?: true,
                        isBuiltin = existing?.isBuiltin ?: false,
                        sortOrder = existing?.sortOrder ?: nextSortOrder(),
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                    roleDao.upsert(RoleEntity.fromDomain(role))
                }
            }.isSuccess
            _saveState.value = if (success) {
                RoleSaveState.Saved
            } else {
                RoleSaveState.Failed("save failed")
            }
        }
    }

    /** 新建角色的排序权重：排在现有角色之后。 */
    private suspend fun nextSortOrder(): Int =
        (roleDao.getAllOnce().maxOfOrNull { it.sortOrder } ?: -1) + 1

    fun clearSaveState() {
        _saveState.value = RoleSaveState.Idle
    }

    companion object {
        /** 新建角色的 id 前缀。 */
        private const val ID_PREFIX = "role_"

        /** 生成一条新建角色的 id。 */
        fun newRoleId(): String = ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
    }
}
