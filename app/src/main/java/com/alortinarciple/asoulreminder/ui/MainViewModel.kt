package com.alortinarciple.asoulreminder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alortinarciple.asoulreminder.data.AppSettings
import com.alortinarciple.asoulreminder.data.ReminderRepository
import kotlinx.coroutines.launch

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
