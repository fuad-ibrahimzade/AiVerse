package com.aiverse.feature.agent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.agent.domain.session.SessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 角色切换（issue #110）：驱动聊天顶栏角色入口与角色切换底部抽屉。
 *
 * 仅负责角色列表、当前角色解析与「会话绑定角色」的切换；不承担提示词注入
 * （该链路在 [AIAgentViewModel] 内由另一批次接线，勿改本类之外的文件）。
 */
@HiltViewModel
class RoleSwitcherViewModel @Inject constructor(
    private val roleDao: RoleDao,
    private val sessionUseCase: SessionUseCase
) : ViewModel() {

    /**
     * 抽屉候选角色：默认角色置顶，随后是所有启用角色（不含默认，避免重复）。
     *
     * 默认角色是会话的兜底绑定（roleId 为 null 时即默认），
     * 即使被用户停用也应始终可选，故从全量里单独取出置于首位。
     */
    val sheetRoles: StateFlow<List<Role>> = roleDao.observeAll()
        .map { all ->
            val default = all.firstOrNull { it.id == Role.DEFAULT_ROLE_ID }
            val enabled = all.filter { it.enabled && it.id != Role.DEFAULT_ROLE_ID }
            buildList {
                if (default != null) add(default.toDomain())
                addAll(enabled.map { it.toDomain() })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 解析当前会话应展示的角色：[sessionRoleId] 为 null（未绑定，即默认角色）
     * 或查不到对应角色时，回退到默认角色。
     */
    fun resolveCurrentRole(sessionRoleId: String?, roles: List<Role>): Role? {
        val targetId = sessionRoleId ?: Role.DEFAULT_ROLE_ID
        return roles.firstOrNull { it.id == targetId }
    }

    /** 切换会话角色并落库；[roleId] 为 null 表示切回默认角色。 */
    fun switchSessionRole(sessionId: String, roleId: String?) {
        viewModelScope.launch {
            sessionUseCase.updateRoleId(sessionId, roleId)
        }
    }
}
