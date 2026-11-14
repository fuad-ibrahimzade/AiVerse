package com.aiverse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.domain.model.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 角色列表页状态：按 sortOrder 排好序的角色列表。 */
data class RolesUiState(
    val roles: List<Role> = emptyList(),
    val loading: Boolean = true
)

/**
 * 角色列表页状态：观察 Role 表（含停用角色），提供启停与删除。
 *
 * 读（observe）与写（DAO）都是 Room 线程池，统一放 IO 线程。内置角色不可删除，删除入口在调用方过滤，
 * 这里再做一次兜底过滤，避免批量回调拿到内置 id 时误删。
 */
@HiltViewModel
class RolesViewModel @Inject constructor(
    private val roleDao: RoleDao
) : ViewModel() {

    private val _state = MutableStateFlow(RolesUiState())
    val state: StateFlow<RolesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            roleDao.observeAll()
                .map { entities -> entities.map { it.toDomain() } }
                .collect { roles ->
                    _state.value = RolesUiState(roles = roles, loading = false)
                }
        }
    }

    /** 切换单个角色的启用状态（内置默认角色也可停用，只是不可删除）。 */
    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                roleDao.setEnabledById(id, enabled, System.currentTimeMillis())
            }
        }
    }

    /** 批量启停。 */
    fun setEnabledBatch(ids: List<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                roleDao.setEnabled(ids, enabled, System.currentTimeMillis())
            }
        }
    }

    /** 删除单个角色（内置角色不可删，调用方已禁用入口，这里兜底过滤）。 */
    fun delete(role: Role) {
        if (role.isBuiltin) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { roleDao.deleteById(role.id) }
        }
    }

    /** 批量删除：只删非内置角色。 */
    fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val deletable = roleDao.getAllOnce()
                    .filter { !it.isBuiltin && it.id in ids }
                    .map { it.id }
                if (deletable.isNotEmpty()) roleDao.deleteByIds(deletable)
            }
        }
    }
}
