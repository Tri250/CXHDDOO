package com.poseai.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)

            val batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            val isLow = batteryPct < 20
            val isHot = temp > 450

            // 通知 ViewModel
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasCameraPermission = cameraGranted
        hasAudioPermission = audioGranted

        // 检查并请求存储权限（版本适配）
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        // 检查通知权限（API 33+）
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val permsToRequest = mutableListOf<String>()
        if (!cameraGranted) permsToRequest.add(Manifest.permission.CAMERA)
        if (!audioGranted) permsToRequest.add(Manifest.permission.RECORD_AUDIO)
        if (!hasStoragePermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                permsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                permsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(batteryReceiver, filter)
        }

        // onboarding 状态在 Compose 中通过 Flow 异步读取
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
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
            text = "PoseAI 需要相机权限来进行姿势检测和拍照。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Text("授权相机")
        }
    }
}
