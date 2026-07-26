package com.poseai.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.poseai.app.ui.GalleryScreen
import com.poseai.app.ui.OOTDAnalysisScreen
import com.poseai.app.ui.OnboardingScreen
import com.poseai.app.ui.ProScreen
import com.poseai.app.ui.ShootingScreen
import com.poseai.app.ui.theme.PoseAITheme
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var hasStoragePermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var permissionRequestedOnce = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", -1)
            val temp = intent.getIntExtra("temperature", -1)

            val batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            val isLow = batteryPct < 20
            val isHot = temp > 450

            val viewModel = lastViewModel
            viewModel?.setBatteryLow(isLow)
            viewModel?.setHeatWarning(isHot)
        }
    }

    private var lastViewModel: ShootingViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasStoragePermission = permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            hasStoragePermission = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (!hasCameraPermission) {
            Toast.makeText(this, "相机权限被拒绝，将影响核心功能", Toast.LENGTH_LONG).show()
        }
        if (!hasAudioPermission) {
            Toast.makeText(this, "录音权限被拒绝，视频录制将无音频", Toast.LENGTH_SHORT).show()
        }
        if (!hasStoragePermission) {
            Toast.makeText(this, "存储权限被拒绝，无法保存照片和视频", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ edge-to-edge 适配
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // 初始化权限状态(不请求,仅检查)
        updatePermissionStates()

        setContent {
            PoseAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasCameraPermission) {
                        PoseAINavHost(
                            onViewModelCreated = { vm -> lastViewModel = vm }
                        )
                    } else {
                        PermissionRequestScreen {
                            requestCameraAndAudioPermissions()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 延迟权限请求到 onResume,避免 onCreate 中过早调用导致崩溃
        if (!permissionRequestedOnce && !hasCameraPermission) {
            permissionRequestedOnce = true
            checkAndRequestPermissions()
        }
        // 每次恢复时更新权限状态(用户可能从设置中改了权限)
        updatePermissionStates()
    }

    override fun onStart() {
        super.onStart()
        // 注册电池广播
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun updatePermissionStates() {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasStoragePermission = checkStoragePermission()
        hasNotificationPermission = checkNotificationPermission()
    }

    private fun checkAndRequestPermissions() {
        updatePermissionStates()

        val permsToRequest = buildPermissionRequestList(
            cameraGranted = hasCameraPermission,
            audioGranted = hasAudioPermission,
            storageGranted = hasStoragePermission,
            notificationGranted = hasNotificationPermission
        )

        if (permsToRequest.isNotEmpty()) {
            try {
                permissionLauncher.launch(permsToRequest.toTypedArray())
            } catch (e: Exception) {
                // 权限请求框架可能在某些设备上崩溃,捕获异常
                Toast.makeText(this, "权限请求失败,请在设置中手动授权", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun buildPermissionRequestList(
        cameraGranted: Boolean,
        audioGranted: Boolean,
        storageGranted: Boolean,
        notificationGranted: Boolean
    ): List<String> {
        val perms = mutableListOf<String>()

        if (!cameraGranted) perms.add(Manifest.permission.CAMERA)
        if (!audioGranted) perms.add(Manifest.permission.RECORD_AUDIO)

        if (!storageGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return perms
    }

    private fun requestCameraAndAudioPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        if (!hasStoragePermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("shooting", "拍摄", Icons.Default.CameraAlt),
    BottomNavItem("gallery", "相册", Icons.Default.PhotoLibrary),
    BottomNavItem("ootd", "穿搭", Icons.Default.Style),
    BottomNavItem("pro", "Pro", Icons.Default.WorkspacePremium)
)

@Composable
fun PoseAINavHost(
    onViewModelCreated: (ShootingViewModel) -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val onboardingCompleted by PoseAIApp.getStoreManager().onboardingCompleted.collectAsState(initial = false)
    val startDestination = if (onboardingCompleted) "shooting" else "onboarding"

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 600

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = com.poseai.app.ui.theme.SurfaceDark,
                    contentColor = com.poseai.app.ui.theme.TextPrimary,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = if (isCompactHeight) 10.sp else 11.sp
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = com.poseai.app.ui.theme.Accent,
                                selectedTextColor = com.poseai.app.ui.theme.Accent,
                                unselectedIconColor = com.poseai.app.ui.theme.TextSecondary,
                                unselectedTextColor = com.poseai.app.ui.theme.TextSecondary,
                                indicatorColor = com.poseai.app.ui.theme.Accent.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onComplete = {
                            scope.launch {
                                PoseAIApp.getStoreManager().setOnboardingCompleted(true)
                            }
                            navController.navigate("ai_activation") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }

                composable("ai_activation") {
                    val aiModelManager = PoseAIApp.getAIModelManager()
                    var activationStatus by remember { mutableStateOf("") }
                    var isActivating by remember { mutableStateOf(false) }
                    var isComplete by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        if (aiModelManager.isActivated) {
                            isComplete = true
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "AI 模型激活",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isComplete) {
                            Text(
                                text = "AI 模型已就绪",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = {
                                navController.navigate("shooting") {
                                    popUpTo("ai_activation") { inclusive = true }
                                }
                            }) {
                                Text("开始使用")
                            }
                        } else {
                            Text(
                                text = if (activationStatus.isNotEmpty()) activationStatus
                                else "正在激活 AI 模型...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            if (!isActivating) {
                                Button(onClick = {
                                    isActivating = true
                                    activationStatus = "正在激活..."
                                    scope.launch {
                                        val result = aiModelManager.activate()
                                        activationStatus = result.message
                                        isActivating = false
                                        isComplete = result.success
                                    }
                                }) {
                                    Text("激活 AI 模型")
                                }
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                composable("shooting") {
                    val viewModel: ShootingViewModel = viewModel()
                    LaunchedEffect(viewModel) { onViewModelCreated(viewModel) }
                    ShootingScreen(viewModel = viewModel)
                }

                composable("gallery") {
                    val viewModel: ShootingViewModel = viewModel()
                    GalleryScreen(viewModel = viewModel)
                }

                composable("ootd") {
                    val viewModel: ShootingViewModel = viewModel()
                    OOTDAnalysisScreen(viewModel = viewModel)
                }

                composable("pro") {
                    val proViewModel: ShootingViewModel = viewModel()
                    ProScreen(
                        isProUnlocked = false,
                        onPurchase = { planId ->
                            scope.launch {
                                PoseAIApp.getStoreManager().setProUnlocked(true)
                            }
                        },
                        onRestore = {
                            scope.launch {
                                PoseAIApp.getStoreManager().setProUnlocked(true)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "PoseAI 需要相机权限来进行姿势检测和拍照。同时还需要录音和存储权限以提供完整功能。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Text("授权权限")
        }
    }
}
