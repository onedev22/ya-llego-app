package com.amurayada.yallego

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.amurayada.yallego.data.AppPreferences
import com.amurayada.yallego.ui.theme.YaLlegoTheme

class TutorialActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            navigateToLogin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        
        removeActionBar()

        appPreferences = AppPreferences(this)

        setContent {
            YaLlegoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModernTutorialScreen(
                        appPreferences = appPreferences,
                        onRequestLocationPermissions = { requestLocationPermissions() },
                        onSkipTutorial = { navigateToLogin() }
                    )
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun removeActionBar() {
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val decorView = window.decorView
        val uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        decorView.systemUiVisibility = uiOptions

        
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        val uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        decorView.systemUiVisibility = uiOptions
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun requestLocationPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (hasLocationPermissions()) {
            navigateToLogin()
        } else {
            locationPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun navigateToLogin() {
        appPreferences.setTutorialShown()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun ModernTutorialScreen(
    appPreferences: AppPreferences,
    onRequestLocationPermissions: () -> Unit,
    onSkipTutorial: () -> Unit
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(0) }

    val totalSteps = tutorialSteps.size

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onConfirm = onSkipTutorial
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onSkipTutorial,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Saltar")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TutorialPage(
                step = tutorialSteps[currentStep],
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            PagerIndicators(
                pageCount = totalSteps,
                currentPage = currentStep,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ActionButtons(
                currentPage = currentStep,
                totalPages = totalSteps,
                onPrevious = {
                    if (currentStep > 0) currentStep--
                },
                onNext = {
                    if (currentStep < totalSteps - 1) currentStep++
                },
                onRequestPermissions = onRequestLocationPermissions,
                onContinueWithoutLocation = { showPermissionDialog = true }
            )
        }
    }
}

@Composable
fun TutorialPage(
    step: TutorialStep,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedIcon(
            icon = step.icon,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (step.requiresPermission) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Recomendado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                0.7f at 500 with LinearEasing
                1.0f at 1000 with LinearEasing
                0.7f at 1500 with LinearEasing
            },
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = CircleShape,
        modifier = modifier.scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(24.dp)
                .size(72.dp)
        )
    }
}

@Composable
fun PagerIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(pageCount) { index ->
            val width by animateDpAsState(
                targetValue = if (index == currentPage) 32.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.6f)
            )

            Surface(
                color = if (index == currentPage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
                shape = CircleShape,
                modifier = Modifier
                    .size(height = 8.dp, width = width)
                    .clip(CircleShape)
            ) {}
        }
    }
}

@Composable
fun ActionButtons(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRequestPermissions: () -> Unit,
    onContinueWithoutLocation: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = onPrevious,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Anterior")
                    Spacer(Modifier.width(8.dp))
                    Text("Anterior")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (currentPage < totalPages - 1) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Siguiente")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = "Siguiente")
                }
            }
        }

        if (currentPage == totalPages - 1) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Ubicación")
                    Spacer(Modifier.width(12.dp))
                    Text("Permitir Ubicación")
                }

                TextButton(
                    onClick = onContinueWithoutLocation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continuar sin ubicación")
                }
            }
        }
    }
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("📍 Ubicación Opcional")
        },
        text = {
            Column {
                Text("Puedes usar YaLlego sin permisos de ubicación, pero algunas funciones estarán limitadas:")
                Spacer(modifier = Modifier.height(16.dp))
                FeatureItem("🚗", "Búsqueda manual de ubicaciones")
                FeatureItem("🗺️", "Navegación básica sin seguimiento")
                FeatureItem("⚡", "Experiencia de usuario reducida")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entendido, continuar")
            }
        }
    )
}

@Composable
fun FeatureItem(emoji: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, modifier = Modifier.width(32.dp))
        Text(text)
    }
}


private val tutorialSteps = listOf(
    TutorialStep(
        icon = Icons.Default.PinDrop,
        title = "Elige tu Destino",
        description = "Selecciona fácilmente tu ubicación actual y el lugar al que quieres llegar con nuestra interfaz intuitiva."
    ),
    TutorialStep(
        icon = Icons.Default.DirectionsCar,
        title = "Conductores Cercanos",
        description = "Conecta instantáneamente con conductores verificados disponibles en tu zona."
    ),
    TutorialStep(
        icon = Icons.Default.Security,
        title = "Pago Seguro",
        description = "Disfruta de transacciones protegidas con múltiples métodos de pago y total transparencia."
    ),
    TutorialStep(
        icon = Icons.Default.LocationSearching,
        title = "Ubicación en Tiempo Real",
        description = "Permite el acceso a tu ubicación para una experiencia optimizada: rutas más rápidas, conductores más cercanos y navegación precisa.",
        requiresPermission = true
    )
)

data class TutorialStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val requiresPermission: Boolean = false
)