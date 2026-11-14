package com.aiverse.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Settings
import compose.icons.feathericons.User
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.PressableSurface
import com.aiverse.feature.agent.domain.model.AvatarKind
import com.aiverse.feature.agent.domain.model.Role
import java.io.File

/**
 * 角色切换底部抽屉（issue #110）：列出默认角色 + 所有启用角色，
 * 当前会话绑定项高亮勾选，点击即切换会话 [Role]（切回默认则置 null）。
 *
 * @param roles 候选角色（默认角色置顶，见 [com.aiverse.feature.agent.presentation.RoleSwitcherViewModel.sheetRoles]）。
 * @param currentSessionRoleId 当前会话绑定的原始 roleId（null = 默认角色），用于高亮。
 * @param onSelectRole 点击某角色：目标角色 id 或 null（切回默认）。
 * @param onManageRoles 「管理角色」跳设置页角色管理。
 * @param onDismiss 关闭抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoleSheet(
    roles: List<Role>,
    currentSessionRoleId: String?,
    onSelectRole: (Role) -> Unit,
    onManageRoles: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    val sheetState = rememberModalBottomSheetState()
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = t.lg)
                .padding(bottom = t.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_role_switcher),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onManageRoles(); onDismiss() }) {
                    Icon(
                        imageVector = FeatherIcons.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(t.scale(16))
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.chat_role_manage),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            if (roles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = t.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chat_role_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = t.scale(420)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(roles, key = { it.id }) { role ->
                        RoleSheetRow(
                            role = role,
                            selected = role.id == (currentSessionRoleId ?: Role.DEFAULT_ROLE_ID),
                            onClick = { onSelectRole(role) }
                        )
                    }
                }
            }
        }
    }
}

/** 角色列表行：头像 + 名称 + 简述；当前绑定项高亮并在右侧勾选。 */
@Composable
private fun RoleSheetRow(
    role: Role,
    selected: Boolean,
    onClick: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(t.rMd),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        contentPadding = PaddingValues(t.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.md)
        ) {
            RoleAvatar(role = role, size = t.scale(40))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = role.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (role.description.isNotBlank()) {
                    Text(
                        text = role.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = FeatherIcons.Check,
                    contentDescription = stringResource(R.string.chat_role_current),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(t.scale(20))
                )
            }
        }
    }
}

/**
 * 角色头像：按 [Role.avatarKind] 呈现。
 * - EMOJI：居中渲染 emoji 字符；
 * - IMAGE：coil 异步加载本地图片（缩略圆角）；
 * - NONE：通用用户图标。
 * 供顶栏角色入口与抽屉行共用。
 */
@Composable
internal fun RoleAvatar(
    role: Role?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    when (role?.avatarKind) {
        AvatarKind.EMOJI -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (role.avatarValue.isNotBlank()) {
                    Text(
                        text = role.avatarValue,
                        fontSize = with(LocalDensity.current) { (size * 0.62f).toSp() },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AvatarKind.IMAGE -> {
            if (role.avatarValue.isNotBlank()) {
                AsyncImage(
                    model = File(role.avatarValue),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .size(size)
                        .clip(RoundedCornerShape(Radius.sm))
                )
            }
        }
        else -> {
            Icon(
                imageVector = FeatherIcons.User,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.size(size)
            )
        }
    }
}
