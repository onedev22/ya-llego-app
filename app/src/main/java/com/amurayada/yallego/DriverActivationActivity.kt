package com.amurayada.yallego

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DriverActivationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RealDriverActivationScreen(
                onActivationComplete = {
                    
                    val intent = Intent(this, DriverHomeActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealDriverActivationScreen(onActivationComplete: () -> Unit) {
    var currentStatus by remember { mutableStateOf("Verificando...") }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    
    LaunchedEffect(Unit) {
        delay(3000) 

        
        

        val isApproved = true 

        if (isApproved) {
            currentStatus = "✅ ¡Cuenta Aprobada!"
            
            val prefs = context.getSharedPreferences("driver_data", Context.MODE_PRIVATE)
            prefs.edit().putString("driver_status", "activo").apply()

            delay(1500)
            onActivationComplete()
        } else {
            currentStatus = "⏳ Pendiente de Aprobación"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Activación de Conductor") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = "Esperando",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Estado de tu Cuenta",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(60.dp))
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                currentStatus,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = when {
                    currentStatus.contains("✅") -> MaterialTheme.colorScheme.primary
                    currentStatus.contains("⏳") -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isLoading && currentStatus.contains("Pendiente")) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Tu cuenta está en revisión. Te notificaremos cuando sea aprobada.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Button(
                        onClick = {
                            
                            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "message/rfc822"
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("soporte@yallego.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "Solicitud de Activación - Conductor")
                            }
                            try {
                                context.startActivity(Intent.createChooser(emailIntent, "Contactar soporte"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No hay aplicaciones de email", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContactSupport, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Contactar Soporte")
                    }

                    TextButton(
                        onClick = {
                            
                            context.getSharedPreferences("driver_data", Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            (context as? ComponentActivity)?.finish()
                        }
                    ) {
                        Text("Cerrar Sesión")
                    }
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Verificando tu información...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}