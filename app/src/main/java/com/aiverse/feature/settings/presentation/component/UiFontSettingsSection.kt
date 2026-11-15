package com.aiverse.feature.settings.presentation.component

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.aiverse.R
import com.aiverse.core.font.UiFontManager
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Download
import compose.icons.feathericons.Trash2
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 界面字体子页面：从「外观设置」进入，「系统默认」+ 已导入字体列表 + 导入按钮，
 * 每项可选中、可删除，实时持久化并全局生效。
 *
 * 顶栏标题与返回由 [com.aiverse.feature.settings.presentation.component.SettingsScreen]
 * 的 Scaffold 统一提供（见 [SettingsSection.AppearanceFont]），本 Composable 只负责内容体，
 * 结构与 [DensitySettingsSection] 等设置子页面一致（可滚动 Column + SettingsGroup）。
 */
@Composable
internal fun UiFontSettingsSection(
    currentPath: String,
    onSetUiFont: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fonts by remember { mutableStateOf(UiFontManager.listFonts(context)) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) { UiFontManager.importFont(context, uri) }
            if (imported == null) {
                Toast.makeText(
                    context,
                    R.string.settings_appearance_ui_font_import_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                fonts = UiFontManager.listFonts(context)
                onSetUiFont(imported.path)
            }
        }
    }

    val t = LocalUIDensityTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(t.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_ui_font))
        SettingsGroup {
            UiFontChoiceRow(
                title = stringResource(R.string.settings_appearance_ui_font_default),
                previewFamily = FontFamily.Default,
                selected = currentPath.isBlank(),
                onClick = { onSetUiFont("") }
            )
            fonts.forEach { font ->
                SettingsDivider()
                UiFontChoiceRow(
                    title = font.displayName,
                    previewFamily = rememberUiFontFamily(font.path),
                    selected = currentPath == font.path,
                    onClick = { onSetUiFont(font.path) },
                    onDelete = {
                        UiFontManager.deleteFont(font.path)
                        fonts = UiFontManager.listFonts(context)
                        if (font.path == currentPath) onSetUiFont("")
                    }
                )
            }
        }

        // 导入入口独立成组（无标题，与外观页底部“恢复默认”单行组一致）。
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Download,
                title = stringResource(R.string.settings_appearance_ui_font_import),
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )
        }
    }
}

/** 单个字体选择行：「Aa」预览 + 名称 + 选中勾 + 可选删除按钮，整行点击选中。 */
@Composable
private fun UiFontChoiceRow(
    title: String,
    previewFamily: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.lg, vertical = t.scale(11)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Aa",
            fontFamily = previewFamily,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(t.scale(32))
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = FeatherIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(t.scale(18))
            )
        }
        if (onDelete != null) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = FeatherIcons.Trash2,
                contentDescription = stringResource(R.string.settings_appearance_ui_font_delete),
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier
                    .size(t.scale(18))
                    .clickable { onDelete() }
            )
        }
    }
}

/** 把字体路径解析成预览用 FontFamily，路径无效时回落系统默认字体。 */
@Composable
private fun rememberUiFontFamily(path: String): FontFamily = remember(path) {
    runCatching { FontFamily(Font(File(path))) }.getOrDefault(FontFamily.Default)
}
