package com.alortinarciple.asoulreminder.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alortinarciple.asoulreminder.R
import com.alortinarciple.asoulreminder.BuildConfig
import com.alortinarciple.asoulreminder.background.BackgroundScheduler
import com.alortinarciple.asoulreminder.data.AppRelease
import com.alortinarciple.asoulreminder.data.AppSettings
import com.alortinarciple.asoulreminder.data.AppState
import com.alortinarciple.asoulreminder.data.DynamicItem
import com.alortinarciple.asoulreminder.data.LiveEvent
import com.alortinarciple.asoulreminder.data.LiveRoomStatus
import com.alortinarciple.asoulreminder.data.MemberConfig
import com.alortinarciple.asoulreminder.notifications.NotificationTargets
import com.alortinarciple.asoulreminder.data.weekdayText
import com.alortinarciple.asoulreminder.ui.theme.BrandPink
import com.alortinarciple.asoulreminder.ui.theme.Canvas
import com.alortinarciple.asoulreminder.ui.theme.FreshGreen
import com.alortinarciple.asoulreminder.ui.theme.Ink
import com.alortinarciple.asoulreminder.ui.theme.Muted
import com.alortinarciple.asoulreminder.ui.theme.SunYellow
import com.alortinarciple.asoulreminder.ui.theme.SurfacePressed
import com.alortinarciple.asoulreminder.ui.theme.SurfaceSoft
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val BeijingZone = ZoneId.of("Asia/Shanghai")
private val PublishFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(BeijingZone)
private val EventTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(BeijingZone)
private val FullEventFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(BeijingZone)
private val CardShape = RoundedCornerShape(22.dp)

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Dynamics("动态", Icons.Rounded.Forum),
    Schedule("日程", Icons.Rounded.CalendarMonth),
    Live("直播间", Icons.Rounded.LiveTv),
    Settings("设置", Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsoulApp(
    viewModel: MainViewModel,
    requestNotificationPermission: () -> Unit,
    isNotificationPermissionGranted: () -> Boolean = { true },
    openAppNotificationSettings: () -> Unit = {},
    openCookieLogin: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(AppTab.Schedule) }
    var selectedEvent by remember { mutableStateOf<LiveEvent?>(null) }
    var cookieLoginLaunched by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentVersion = remember { BuildConfig.VERSION_NAME }
    var updatableRelease by remember { mutableStateOf<AppRelease?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateMessage by remember { mutableStateOf("") }
    var checkingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshOnLaunch()
    }

    // 启动后静默检查一次更新：24 小时内已查过就跳过，发现新版才弹窗。
    LaunchedEffect(Unit) {
        viewModel.checkUpdate(manual = false) { release, error ->
            if (release != null) {
                updateMessage = ""
                updatableRelease = release
            } else if (error != null) {
                Toast.makeText(context, "检查更新失败：$error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val startUpdateCheck: (Boolean) -> Unit = { manual ->
        if (checkingUpdate) {
            // 已在上一次请求中，忽略重复点击。
        } else {
            checkingUpdate = true
            viewModel.checkUpdate(manual = manual) { release, error ->
                checkingUpdate = false
                when {
                    error != null -> Toast.makeText(context, "检查更新失败：$error", Toast.LENGTH_LONG).show()
                    release == null && manual ->
                        Toast.makeText(context, "当前已是最新版本 v$currentVersion", Toast.LENGTH_SHORT).show()
                    release != null -> {
                        updateMessage = ""
                        updatableRelease = release
                    }
                }
            }
        }
    }

    // Cookie 失效时自动进入登录页；登录成功后状态变为未失效，允许下次再自动弹出。
    LaunchedEffect(state.cookieExpired) {
        if (!state.cookieExpired) {
            cookieLoginLaunched = false
            return@LaunchedEffect
        }
        if (!cookieLoginLaunched) {
            cookieLoginLaunched = true
            openCookieLogin()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas),
                title = {
                    Column {
                        Text(currentTab.label, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            state.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = !state.refreshing,
                        onClick = {
                            when (currentTab) {
                                AppTab.Dynamics -> viewModel.refreshDynamics()
                                AppTab.Schedule -> viewModel.refreshSchedule()
                                AppTab.Live -> viewModel.refreshLive()
                                AppTab.Settings -> viewModel.refreshAll()
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新${currentTab.label}")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceSoft) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.refreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when (currentTab) {
                AppTab.Dynamics -> DynamicsScreen(state, viewModel::refreshDynamics)
                AppTab.Schedule -> ScheduleScreen(state, viewModel::refreshSchedule) { selectedEvent = it }
                AppTab.Live -> LiveScreen(state, viewModel::refreshLive)
                AppTab.Settings -> SettingsScreen(
                    state = state,
                    onSettingsChange = viewModel::updateSettings,
                    onImport = viewModel::importDesktopConfig,
                    requestNotificationPermission = requestNotificationPermission,
                    isNotificationPermissionGranted = isNotificationPermissionGranted,
                    openAppNotificationSettings = openAppNotificationSettings,
                    openCookieLogin = openCookieLogin,
                    currentVersion = currentVersion,
                    checkingUpdate = checkingUpdate,
                    onCheckUpdate = { startUpdateCheck(true) },
                )
            }
        }
    }

    selectedEvent?.let { event ->
        EventActionsSheet(
            event = event,
            settings = state.settings,
            onDismiss = { selectedEvent = null },
            onRememberReminders = viewModel::rememberScheduledReminders,
        )
    }

    updatableRelease?.let { release ->
        UpdateDialog(
            release = release,
            currentVersion = currentVersion,
            downloading = updateDownloading,
            progress = updateProgress,
            message = updateMessage,
            onDownload = {
                if (updateDownloading) {
                    // 下载中重复点击无意义，进度条自身已在反馈。
                } else {
                    updateDownloading = true
                    updateProgress = 0
                    updateMessage = ""
                    viewModel.downloadUpdate(
                        release = release,
                        onProgress = { updateProgress = it },
                        onFinished = { file, error ->
                            updateDownloading = false
                            if (file != null) {
                                updatableRelease = null
                                ExternalActions.installApk(context, file)
                            } else {
                                updateMessage = error ?: "下载失败"
                            }
                        },
                    )
                }
            },
            onDismiss = { updatableRelease = null },
        )
    }
}

@Composable
private fun DynamicsScreen(state: AppState, onRefresh: () -> Unit) {
    val members = state.settings.members.associateBy(MemberConfig::id)
    if (state.cache.dynamics.isEmpty()) {
        EmptyState(
            title = if (state.hasCookie) "还没有动态缓存" else "需要导入 B 站 Cookie",
            detail = if (state.hasCookie) "刷新后会读取每位成员最近的动态。" else "请在设置中导入桌面 config.json。",
            action = if (state.hasCookie) "刷新动态" else null,
            onAction = onRefresh,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.cache.dynamics, key = DynamicItem::id) { item ->
            DynamicCard(item, members[item.memberId])
        }
    }
}

@Composable
private fun DynamicCard(item: DynamicItem, member: MemberConfig?) {
    val context = LocalContext.current
    val accent = parseColor(member?.color)
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(item.memberId, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.memberName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        item.publishedEpochMillis?.let {
                            val published = Instant.ofEpochMilli(it)
                            "发布 ${PublishFormatter.format(published)} ${weekdayText(published)}"
                        } ?: "发布时间未知",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                DynamicBody(item)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { ExternalActions.openBilibili(context, item.url) }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("打开")
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicBody(item: DynamicItem) {
    val segments = item.richText.ifEmpty {
        listOf(com.alortinarciple.asoulreminder.data.DynamicTextSegment("text", item.text))
    }
    val annotated = buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            if (segment.type == "emoji" && segment.url.isNotBlank()) {
                appendInlineContent("emoji-$index", segment.text)
            } else {
                append(segment.text)
            }
        }
    }
    val inlineContent = segments.mapIndexedNotNull { index, segment ->
        if (segment.type != "emoji" || segment.url.isBlank()) return@mapIndexedNotNull null
        "emoji-$index" to InlineTextContent(
            Placeholder(38.sp, 38.sp, PlaceholderVerticalAlign.TextCenter),
        ) {
            AsyncImage(
                model = segment.url,
                contentDescription = segment.text,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }.toMap()
    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyLarge,
        color = Ink,
    )
}

@Composable
private fun ScheduleScreen(
    state: AppState,
    onRefresh: () -> Unit,
    onSelect: (LiveEvent) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) }
    val today = LocalDate.now(BeijingZone)
    val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val visible = state.cache.events.filter { event ->
        val date = Instant.ofEpochMilli(event.startEpochMillis).atZone(BeijingZone).toLocalDate()
        event.status != "rescheduled" && if (mode == 0) date == today else date in today..weekEnd
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ModeSwitch(
            first = "今日",
            second = "本周",
            selected = mode,
            onSelected = { mode = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (visible.isEmpty()) {
            EmptyState("没有待显示的日程", "已过去的日期和已改期条目不会显示。", "刷新日程", onRefresh)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = LiveEvent::id) { event -> ScheduleCard(event) { onSelect(event) } }
            }
        }
    }
}

@Composable
private fun ModeSwitch(
    first: String,
    second: String,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SurfacePressed,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Row(Modifier.padding(4.dp)) {
            listOf(first, second).forEachIndexed { index, label ->
                Surface(
                    color = if (selected == index) SurfaceSoft else Color.Transparent,
                    shadowElevation = if (selected == index) 3.dp else 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(index) },
                ) {
                    Text(
                        label,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(event: LiveEvent, onClick: () -> Unit) {
    val accent = parseColor(event.color)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        EventTimeFormatter.format(Instant.ofEpochMilli(event.startEpochMillis)),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(event.status)
                }
                if (event.description.isNotBlank()) {
                    Text(event.description, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                val startInstant = Instant.ofEpochMilli(event.startEpochMillis)
                Text(
                    "${FullEventFormatter.format(startInstant)} ${weekdayText(startInstant)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "查看操作",
                tint = Muted,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (text, color) = when (status) {
        "cancelled" -> "已取消" to Muted
        "live" -> "直播中" to FreshGreen
        else -> "已发布" to BrandPink
    }
    Surface(color = color, shape = RoundedCornerShape(10.dp)) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LiveScreen(state: AppState, onRefresh: () -> Unit) {
    val members = state.settings.members.filter { it.enabled && it.monitorLive }
    if (members.isEmpty()) {
        EmptyState("没有启用直播监控的成员", "可以在设置中开启成员。", null, onRefresh)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(members, key = MemberConfig::id) { member ->
            LiveRoomCard(member, state.cache.liveStatuses[member.id])
        }
    }
}

@Composable
private fun LiveRoomCard(member: MemberConfig, status: LiveRoomStatus?) {
    val context = LocalContext.current
    val accent = parseColor(member.color)
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(member.id, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(member.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    val label = when (status?.liveStatus) {
                        1 -> "直播中"
                        0, 2 -> "未开播"
                        else -> "未检查"
                    }
                    Surface(
                        color = if (status?.isLive == true) FreshGreen else Muted,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    status?.title?.takeIf { it.isNotBlank() } ?: "尚未读取直播间状态",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (status != null) {
                    val checkedInstant = Instant.ofEpochMilli(status.checkedEpochMillis)
                    Text(
                        "检查 ${PublishFormatter.format(checkedInstant)} ${weekdayText(checkedInstant)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { ExternalActions.openBilibili(context, member.liveUrl) }) {
                        Icon(Icons.Rounded.LiveTv, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("打开直播间")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppState,
    onSettingsChange: (AppSettings) -> Unit,
    onImport: (String) -> Unit,
    requestNotificationPermission: () -> Unit,
    isNotificationPermissionGranted: () -> Boolean,
    openAppNotificationSettings: () -> Unit,
    openCookieLogin: () -> Unit,
    currentVersion: String,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    var showBilibiliTargetSheet by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取文件")
        }
        text.onSuccess(onImport)
            .onFailure { Toast.makeText(context, "配置导入失败：${it.message}", Toast.LENGTH_LONG).show() }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.imePadding(),
    ) {
        item {
            SettingsCard("提醒与后台") {
                SettingSwitchRow(
                    Icons.Rounded.Notifications,
                    "Windows 通知对应功能",
                    "Android 13 以上还需要系统通知权限",
                    state.settings.notificationsEnabled,
                ) { enabled ->
                    onSettingsChange(state.settings.copy(notificationsEnabled = enabled))
                    if (enabled) requestNotificationPermission()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ActionSettingRow(
                    Icons.Rounded.Notifications,
                    "通知权限",
                    if (isNotificationPermissionGranted()) {
                        "已允许，新动态与开播提醒会进入通知中心"
                    } else {
                        "未允许，所有通知都不会显示"
                    },
                    "去授权",
                ) { requestNotificationPermission() }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ActionSettingRow(
                    Icons.Rounded.Settings,
                    "系统通知设置",
                    "多次请求无效，请到系统设置手动开启",
                    "打开",
                ) { openAppNotificationSettings() }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Text(
                    "悬浮通知",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                SettingSwitchRow(
                    Icons.Rounded.LiveTv,
                    "开播提醒悬浮",
                    "开播前提醒与开播通知显示为横幅",
                    state.settings.headsUpLive,
                ) { enabled ->
                    onSettingsChange(state.settings.copy(headsUpLive = enabled))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                SettingSwitchRow(
                    Icons.Rounded.Forum,
                    "动态提醒悬浮",
                    "新动态显示为横幅",
                    state.settings.headsUpDynamic,
                ) { enabled ->
                    onSettingsChange(state.settings.copy(headsUpDynamic = enabled))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                SettingSwitchRow(
                    Icons.Rounded.Event,
                    "日程提醒悬浮",
                    "日程新增/变动显示为横幅",
                    state.settings.headsUpSchedule,
                ) { enabled ->
                    onSettingsChange(state.settings.copy(headsUpSchedule = enabled))
                }
                Text(
                    "悬浮开关只影响横幅，不影响是否收到通知；若仍不悬浮，请到系统通知设置里打开对应渠道的「横幅」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Text(
                    "首次抓取成员动态只建立基线，之后的新动态才会提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                SettingSwitchRow(
                    Icons.Rounded.LiveTv,
                    "后台监控",
                    "通知栏常驻运行：自动刷新日程、动态与直播间（推荐开启）",
                    state.settings.enhancedMonitoringEnabled,
                ) { enabled ->
                    onSettingsChange(state.settings.copy(enhancedMonitoringEnabled = enabled))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Text(
                    "请勿在系统设置里「强行停止」本应用；若长时间收不到刷新，请在系统设置中允许自启动/后台运行（vivo 在 i管家-应用管理 中允许后台高耗电与自启动）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ActionSettingRow(
                    Icons.Rounded.Alarm,
                    "精确提醒权限",
                    if (BackgroundScheduler.canScheduleExact(context)) "已允许" else "未允许，开播监控会降级",
                    "打开设置",
                ) { ExternalActions.requestExactAlarmAccess(context) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ActionSettingRow(
                    Icons.Rounded.LiveTv,
                    "哔哩哔哩打开方式",
                    bilibiliTargetDetail(context, state.settings.bilibiliPackage),
                    "选择",
                ) { showBilibiliTargetSheet = true }
            }
        }
        item {
            SettingsCard("配置与数据") {
                ActionSettingRow(
                    Icons.Rounded.AccountCircle,
                    "B 站账号",
                    when {
                        state.cookieExpired -> "Cookie 已失效，需要重新登录"
                        state.hasCookie -> "已登录，Cookie 已加密保存"
                        else -> "未登录，动态检查需要先登录"
                    },
                    "扫码登录",
                ) { openCookieLogin() }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ActionSettingRow(
                    Icons.Rounded.ImportExport,
                    "导入桌面配置",
                    if (state.hasCookie) "B 站 Cookie 已加密保存" else "当前没有 B 站 Cookie",
                    "选择文件",
                ) { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ActionSettingRow(
                    Icons.Rounded.SystemUpdate,
                    "检查更新",
                    if (checkingUpdate) "正在检查..." else "当前 v$currentVersion",
                    "检查",
                ) { onCheckUpdate() }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("添加到日历的默认时长", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.settings.defaultEventDurationMinutes.toString(),
                        onValueChange = { raw ->
                            raw.toIntOrNull()?.takeIf { it in 15..720 }?.let { minutes ->
                                onSettingsChange(state.settings.copy(defaultEventDurationMinutes = minutes))
                            }
                        },
                        suffix = { Text("分钟") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Text("成员", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp))
        }
        items(state.settings.members, key = MemberConfig::id) { member ->
            MemberSettingsCard(member) { updated ->
                onSettingsChange(
                    state.settings.copy(
                        members = state.settings.members.map { if (it.id == updated.id) updated else it },
                    ),
                )
            }
        }
        item {
            SettingsCard("运行参数") {
                ParameterRow("日程", "每 ${state.settings.scheduleIntervalMinutes.coerceAtLeast(30)} 分钟")
                ParameterRow("动态", "每 ${state.settings.dynamicIntervalMinutes.coerceAtLeast(15)} 分钟")
                ParameterRow("开播窗口", "每 ${state.settings.liveOpenIntervalSeconds} 秒")
                ParameterRow("下播状态", "每 ${state.settings.liveCloseIntervalMinutes} 分钟")
                ParameterRow(
                    "下播后动态",
                    "每 ${state.settings.dynamicHighFrequencyIntervalMinutes} 分钟，最多 ${state.settings.dynamicHighFrequencyMaxMinutes} 分钟",
                )
            }
        }
        state.cache.lastError?.let { error ->
            item {
                SettingsCard("最近错误") {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (showBilibiliTargetSheet) {
        BilibiliTargetSheet(
            currentPackage = state.settings.bilibiliPackage,
            onDismiss = { showBilibiliTargetSheet = false },
            onSelect = { packageName ->
                onSettingsChange(state.settings.copy(bilibiliPackage = packageName))
                showBilibiliTargetSheet = false
            },
        )
    }
}

private fun bilibiliTargetDetail(context: Context, packageName: String): String {
    if (packageName.isBlank()) return "自动（优先哔哩哔哩客户端）"
    val label = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()
    return if (label.isNullOrBlank()) packageName else "$label（$packageName）"
}

private data class BilibiliTargetOption(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BilibiliTargetSheet(
    currentPackage: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val candidates = remember {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(NotificationTargets.PROBE_URL))
            context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo }
                .distinctBy { it.packageName }
                .map { info ->
                    BilibiliTargetOption(
                        packageName = info.packageName,
                        label = runCatching { info.loadLabel(context.packageManager).toString() }
                            .getOrDefault(info.packageName),
                    )
                }
                .sortedBy { it.label }
        }.getOrDefault(emptyList())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceSoft) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("哔哩哔哩打开方式", style = MaterialTheme.typography.headlineSmall)
            Text(
                "点开动态或直播间时默认使用的应用；双开时选择你常用的那个实例。",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            TargetOptionRow(
                title = "自动",
                subtitle = "优先哔哩哔哩客户端，找不到时交给系统",
                selected = currentPackage.isBlank(),
            ) { onSelect("") }
            candidates.forEach { option ->
                TargetOptionRow(
                    title = option.label,
                    subtitle = option.packageName,
                    selected = currentPackage == option.packageName,
                ) { onSelect(option.packageName) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TargetOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = BrandPink)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BrandPink)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BrandPink)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Text(action, color = BrandPink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MemberSettingsCard(member: MemberConfig, onChange: (MemberConfig) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(member.id, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleLarge)
                    Text(member.id.replaceFirstChar(Char::uppercase), color = Muted)
                }
                Switch(checked = member.enabled, onCheckedChange = { onChange(member.copy(enabled = it)) })
            }
            OutlinedTextField(
                value = member.name,
                onValueChange = { onChange(member.copy(name = it.take(30))) },
                label = { Text("显示名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CompactSwitch("动态", member.monitorDynamic) { onChange(member.copy(monitorDynamic = it)) }
                CompactSwitch("直播", member.monitorLive) { onChange(member.copy(monitorLive = it)) }
            }
        }
    }
}

@Composable
private fun CompactSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun ParameterRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = Muted, modifier = Modifier.width(100.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventActionsSheet(
    event: LiveEvent,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onRememberReminders: (List<com.alortinarciple.asoulreminder.data.ScheduledReminder>) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceSoft) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.headlineSmall)
            val sheetStartInstant = Instant.ofEpochMilli(event.startEpochMillis)
            Text(
                "${FullEventFormatter.format(sheetStartInstant)} ${weekdayText(sheetStartInstant)}",
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            event.liveUrl?.let { url ->
                ActionRow(Icons.Rounded.LiveTv, "打开直播间") { ExternalActions.openBilibili(context, url) }
            }
            event.dynamicUrl?.let { url ->
                ActionRow(Icons.Rounded.Forum, "打开相关动态") { ExternalActions.openBilibili(context, url) }
            }
            ActionRow(Icons.Rounded.Event, "添加到系统日历") {
                ExternalActions.addToCalendar(context, event, settings)
            }
            ActionRow(Icons.Rounded.Alarm, "创建系统闹钟") {
                ExternalActions.createSystemAlarm(context, event)
            }
            ActionRow(Icons.Rounded.Timer, "创建应用内提醒（${settings.remindBeforeMinutes.joinToString("/")} 分钟）") {
                if (!BackgroundScheduler.canScheduleExact(context)) {
                    ExternalActions.requestExactAlarmAccess(context)
                    Toast.makeText(context, "允许精确提醒后，请再次点击创建", Toast.LENGTH_LONG).show()
                } else {
                    val reminders = ExternalActions.scheduleInAppReminders(context, event, settings)
                    onRememberReminders(reminders)
                    val message = if (reminders.isEmpty()) "提醒时间已经过去" else "已创建 ${reminders.size} 个提醒"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = SurfacePressed, shape = CircleShape) {
            Icon(icon, contentDescription = null, tint = BrandPink, modifier = Modifier.padding(10.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Muted)
    }
}

@Composable
private fun MemberAvatar(memberId: String, size: androidx.compose.ui.unit.Dp) {
    val resource = when (memberId) {
        "diana" -> R.drawable.avatar_diana
        "eileen" -> R.drawable.avatar_eileen
        "bella" -> R.drawable.avatar_bella
        "fiona" -> R.drawable.avatar_fiona
        "gladys" -> R.drawable.avatar_gladys
        else -> R.drawable.ic_launcher_foreground
    }
    androidx.compose.foundation.Image(
        painter = painterResource(resource),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfacePressed),
    )
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    action: String?,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = SurfacePressed, shape = CircleShape) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = BrandPink,
                modifier = Modifier.padding(18.dp).size(34.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (action != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 18.dp)) {
                Text(action)
            }
        }
    }
}

private fun parseColor(value: String?): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(value ?: "#E95492"))
    }.getOrDefault(BrandPink)
}
