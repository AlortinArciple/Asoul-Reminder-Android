package com.alortinarciple.asoulreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alortinarciple.asoulreminder.data.AppRelease
import com.alortinarciple.asoulreminder.ui.theme.Muted

/**
 * 发现新版本后的对话框：展示更新说明，点「下载并安装」后显示进度，
 * 下载完成立刻交给系统安装器（用户仍需在系统界面确认安装）。
 */
@Composable
internal fun UpdateDialog(
    release: AppRelease,
    currentVersion: String,
    downloading: Boolean,
    progress: Int,
    message: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 ${release.tag}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "当前版本 v$currentVersion → 最新版本 ${release.versionText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Text(
                    "${release.assetName}（${release.sizeText}）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Text("正在下载 $progress%", style = MaterialTheme.typography.bodySmall, color = Muted)
                } else if (message.isNotEmpty()) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !downloading) {
                Text(if (message.isEmpty()) "下载并安装" else "重试")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) { Text("稍后") }
        },
    )
}
