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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

        if (!cameraGranted || !audioGranted) {
            val perms = mutableListOf<String>()
            if (!cameraGranted) perms.add(Manifest.permission.CAMERA)
            if (!audioGranted) perms.add(Manifest.permission.RECORD_AUDIO)
            permissionLauncher.launch(perms.toTypedArray())
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

@Composable
fun PoseAINavHost(
    onViewModelCreated: (ShootingViewModel) -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val onboardingCompleted by PoseAIApp.getStoreManager().onboardingCompleted.collectAsState(initial = false)
    val startDestination = if (onboardingCompleted) "shooting" else "onboarding"

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
            ProScreen(
                isProUnlocked = false,
                onPurchase = { },
                onRestore = { }
            )
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
