package com.alortinarciple.asoulreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alortinarciple.asoulreminder.ui.AsoulApp
import com.alortinarciple.asoulreminder.ui.MainViewModel
import com.alortinarciple.asoulreminder.ui.theme.AsoulReminderTheme

class MainActivity : ComponentActivity() {
    private val permissionGranted = mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted.value = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 只能在 onCreate 里读取权限：字段初始化阶段 base context 尚未 attach。
        permissionGranted.value = hasNotificationPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted.value) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = (application as AsoulApplication).container.repository
        setContent {
            AsoulReminderTheme {
                val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(repository))
                AsoulApp(
                    viewModel = mainViewModel,
                    requestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    isNotificationPermissionGranted = { permissionGranted.value },
                    openAppNotificationSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                        )
                    },
                    openCookieLogin = {
                        startActivity(Intent(this, BilibiliLoginActivity::class.java))
                    },
                )
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
