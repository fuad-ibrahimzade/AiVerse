package com.aiverse.core.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import compose.icons.FeatherIcons
import compose.icons.feathericons.Upload

/** 选择按钮内图标/加载圈基准尺寸（dp 数，组件内按 UI 密度缩放）。 */
private const val PICKER_ACTION_ICON_BASE_DP = 16

/**
 * 相册选图字段：选择 → 落盘 → 回传本地路径，并在字段内显示所选预览。
 *
 * 收敛了「新对话快捷入口图标」「角色头像」等多处重复实现的同一套交互：`PickVisualMedia` 系统
 * 相册选择器 + 保存为 App 私有目录绝对路径（content URI 授权会过期，不能直接存）+ 保存中状态
 * + 失败提示。
 *
 * 落盘本身由调用方注入（[onSaveImage]，如设置页各自的 repository），组件只负责选择流程与状态
 * 呈现；预览槽 [preview] 也由调用方渲染，以便复用各自的图标/头像样式（快捷入口的图标槽、
 * 角色头像卡）。
 *
 * @param path 当前图片本地路径，空表示尚未选择。
 * @param onPathChange 落盘成功后回传绝对路径；调用方据此更新自身状态。
 * @param onSaveImage 把所选图片落盘，回调返回绝对路径或 null（失败）。
 * @param saveErrorRes 落盘失败提示文案资源（各功能用自己的失败语）。
 * @param previewSize 预览槽边长（dp 数，组件内按 UI 密度缩放）。
 * @param preview 预览槽内容，仅在 [path] 非空时渲染；size 为缩放后的边长。
 */
@Composable
fun ImagePickerField(
    path: String,
    onPathChange: (String) -> Unit,
    onSaveImage: (Uri, (String?) -> Unit) -> Unit,
    saveErrorRes: Int,
    previewSize: Dp = 64.dp,
    modifier: Modifier = Modifier.fillMaxWidth(),
    preview: (@Composable (path: String, size: Dp) -> Unit)? = null
) {
    val context = LocalContext.current
    val t = LocalUIDensityTokens.current
    var uploading by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        onSaveImage(uri) { saved ->
            uploading = false
            if (saved != null) {
                onPathChange(saved)
            } else {
                Toast.makeText(context, context.getString(saveErrorRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(t.md)
    ) {
        val previewDp = t.scale(previewSize.value.roundToInt())
        if (path.isNotBlank()) preview?.invoke(path, previewDp)
        Button(
            onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !uploading,
            modifier = Modifier.fillMaxWidth()
        ) {
            val actionSize = t.scale(PICKER_ACTION_ICON_BASE_DP)
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(actionSize), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = FeatherIcons.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(actionSize)
                )
            }
            Spacer(Modifier.width(t.sm))
            Text(
                stringResource(
                    if (uploading) R.string.common_image_picker_uploading
                    else R.string.common_image_picker_pick
                )
            )
        }
    }
}
