package com.alortinarciple.asoulreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alortinarciple.asoulreminder.data.AppRelease
import com.alortinarciple.asoulreminder.data.AppSettings
import com.alortinarciple.asoulreminder.data.ReminderRepository
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val repository: ReminderRepository,
) : ViewModel() {
    val state = repository.state

    fun refreshAll() = viewModelScope.launch { repository.refreshAll(forceSchedule = true) }
    fun refreshOnLaunch() = viewModelScope.launch { repository.refreshAll(forceSchedule = false) }
    fun refreshSchedule() = viewModelScope.launch { repository.refreshSchedule() }
    fun refreshDynamics() = viewModelScope.launch { repository.refreshDynamics() }
    fun refreshLive() = viewModelScope.launch { repository.refreshLiveStatuses() }

    fun updateSettings(settings: AppSettings) = repository.updateSettings(settings)
    fun importDesktopConfig(text: String) = repository.importDesktopConfig(text)

    fun rememberScheduledReminders(items: List<com.alortinarciple.asoulreminder.data.ScheduledReminder>) {
        repository.addScheduledReminders(items)
    }

    /**
     * 检查更新。[manual] 为 false 时是启动静默检查：24 小时内查过就跳过。
     * 回调参数依次是新版本（没有则为 null）与错误信息（成功则为 null）。
     */
    fun checkUpdate(manual: Boolean, onResult: (AppRelease?, String?) -> Unit) = viewModelScope.launch {
        try {
            onResult(repository.checkUpdate(silent = !manual), null)
        } catch (error: Throwable) {
            onResult(null, error.message ?: "检查更新失败")
        }
    }

    /** 下载新版本 APK；回调依次是 0..100 的进度、成品文件与错误信息。 */
    fun downloadUpdate(
        release: AppRelease,
        onProgress: (Int) -> Unit,
        onFinished: (File?, String?) -> Unit,
    ) = viewModelScope.launch {
        try {
            onFinished(repository.downloadUpdate(release, onProgress), null)
        } catch (error: Throwable) {
            onFinished(null, error.message ?: "下载失败")
        }
    }

    class Factory(
        private val repository: ReminderRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(repository) as T
        }
    }
}
