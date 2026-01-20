package com.amurayada.yallego

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color as ComposeColor
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class NavigationActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var osrmClient: OSRMClient
    private var currentLocation: Location? = null

    
    private lateinit var textToSpeech: TextToSpeech
    private var isTTSInitialized = false

    
    private var destinationName: String = ""
    private var destinationLat: Double = 0.0
    private var destinationLon: Double = 0.0
    private var startLat: Double = 0.0
    private var startLon: Double = 0.0
    private var tripAmount: Double = 0.0

    // Nuevas variables para el flujo de viaje
    private var navMode: String = "NORMAL" // "NORMAL", "PICKUP", "TRIP"
    private var finalDestLat: Double = 0.0
    private var finalDestLon: Double = 0.0
    private var finalDestName: String = ""
    private var showStartTripButton: Boolean by mutableStateOf(false)

    
    private var currentRoute: OSRMRoute? = null
    private var currentRoutePoints: List<LatLng> = emptyList()
    private var currentSteps: List<OSRMStep> = emptyList()
    private var lastRecalculationTime: Long = 0
    private val MIN_RECALCULATION_INTERVAL = 30000L
    private var isRecalculating: Boolean by mutableStateOf(false)
    private var isOffRoute: Boolean by mutableStateOf(false)

    
    private var currentStepIndex: Int by mutableStateOf(-1)
    private var distanceToNextStep: Double by mutableStateOf(0.0)
    private var lastInstructionUpdate: Long = 0
    private var lastSpokenInstruction: String = ""
    private var lastStepIndexSpoken: Int = -1

    
    private var distance: String by mutableStateOf("Calculando...")
    private var duration: String by mutableStateOf("Calculando...")
    private var arrivalTime: String by mutableStateOf("--:--")
    private var nextInstruction: String by mutableStateOf("Bienvenido al modo navegación...")
    private var currentStreet: String by mutableStateOf("")
    private var nextStreet: String by mutableStateOf("")
    
    private var currentManeuverType: String by mutableStateOf("")
    private var currentManeuverModifier: String by mutableStateOf("")
    private var distanceToManeuver: Double by mutableStateOf(0.0)
    private var maneuverStreetName: String by mutableStateOf("")
    
    private var tripProgress: Float by mutableStateOf(0f)
    private var totalDistance: Double = 0.0
    private var distanceTraveled: Double = 0.0
    
    private var currentSpeed: Float by mutableStateOf(0f)
    private var speedLimit: Int by mutableStateOf(50)
    
    private var isNightMode: Boolean by mutableStateOf(false)
    
    private var lastProgressUpdate: Long = 0
    private val PROGRESS_UPDATE_INTERVAL = 2000L

    
    private var previousLocation: Location? = null
    private var lastRouteCheck: Long = 0
    private val ROUTE_CHECK_INTERVAL = 5000L

    
    private var isMapReady: Boolean by mutableStateOf(false)

    
    private var isFollowingLocation: Boolean by mutableStateOf(true)
    private var isUserInteracting: Boolean by mutableStateOf(false)
    private var lastUserInteractionTime: Long = 0
    private val AUTO_FOLLOW_DELAY = 10000L

    
    private var userLocationMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var currentBearing: Float = 0f
    private var isUserMoving: Boolean by mutableStateOf(false)

    
    private var smoothedBearing: Float = 0f
    private var smoothedLocation: Location? = null
    private var lastLocationUpdate: Long = 0
    private val LOCATION_SMOOTHING_DELAY = 150L
    private val BEARING_SMOOTHING_FACTOR = 0.3f

    
    private val PERMANENT_TILT = 90.0 
    private val PERMANENT_ZOOM = 17.5 
    private val MIN_ZOOM = 10.0       
    private val MAX_ZOOM = 20.0       

    
    private val locationCallback = object : LocationCallback() {
        private var lastValidLocation: Location? = null
        private var consecutiveErrors = 0

        override fun onLocationResult(result: LocationResult) {
            try {
                val newLocation = result.lastLocation ?: return

                if (!isValidLocation(newLocation)) {
                    consecutiveErrors++
                    if (consecutiveErrors > 3) {
                        nextInstruction = "Buscando señal GPS..."
                    }
                    return
                }

                consecutiveErrors = 0

                
                val speed = newLocation.speed
                isUserMoving = speed > 1.0f
                currentSpeed = speed * 3.6f
                
                checkAndUpdateNightMode()

                
                val bearing = calculateBearing(newLocation)

                
                smoothedBearing = smoothBearing(bearing, smoothedBearing)

                previousLocation = currentLocation
                currentLocation = newLocation
                lastValidLocation = newLocation

                
                if (maplibreMap != null && isMapReady) {
                    updateUserLocationMarkerSmooth(newLocation, smoothedBearing)

                    
                    if (isFollowingLocation && !isUserInteracting) {
                        updateModernCameraPosition(newLocation, smoothedBearing)
                    }
                }

                
                updateNavigationInfo()

                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL) {
                    updateTripProgress()
                    lastProgressUpdate = currentTime
                }

                
                if (currentTime - lastRouteCheck > ROUTE_CHECK_INTERVAL) {
                    updateRealTimeInstruction(newLocation)
                    checkIfOffRoute(newLocation)
                    lastRouteCheck = currentTime
                }

                // Verificar proximidad en modo PICKUP
                if (navMode == "PICKUP") {
                    val distanceToPassenger = calculateDistance(
                        LatLng(newLocation.latitude, newLocation.longitude),
                        LatLng(destinationLat, destinationLon)
                    )
                    
                    // Mostrar botón si está a menos de 10 metros (umbral ajustado)
                    if (distanceToPassenger <= 10.0 && !showStartTripButton) {
                        showStartTripButton = true
                        speakInstruction("Ha llegado al punto de recogida. Inicie el viaje cuando el pasajero suba.")
                        Toast.makeText(this@NavigationActivity, "📍 Llegaste al punto de recogida", Toast.LENGTH_LONG).show()
                    } else if (distanceToPassenger > 20.0 && showStartTripButton) {
                         // Ocultar si se aleja demasiado (opcional, para evitar parpadeo)
                         showStartTripButton = false
                    }
                }

            } catch (e: Exception) {
                Log.e("NavigationActivity", "Error en location callback: ${e.message}")
            }
        }

        private fun isValidLocation(location: Location): Boolean {
            return location.latitude != 0.0 &&
                    location.longitude != 0.0 &&
                    location.accuracy < 50.0f &&
                    System.currentTimeMillis() - location.time < 30000
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Se necesitan permisos de ubicación", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        
        MapLibre.getInstance(this)

        setupFullScreen()

        
        textToSpeech = TextToSpeech(this, this)

        
        try {
            destinationName = intent.getStringExtra("DESTINATION_NAME") ?: "Destino"
            destinationLat = intent.getDoubleExtra("DESTINATION_LAT", 0.0)
            destinationLon = intent.getDoubleExtra("DESTINATION_LON", 0.0)
            startLat = intent.getDoubleExtra("START_LAT", 0.0)
            startLon = intent.getDoubleExtra("START_LON", 0.0)
            tripAmount = intent.getDoubleExtra("TRIP_AMOUNT", 0.0)

            // Leer extras del flujo de viaje
            navMode = intent.getStringExtra("NAV_MODE") ?: "NORMAL"
            finalDestLat = intent.getDoubleExtra("FINAL_DEST_LAT", 0.0)
            finalDestLon = intent.getDoubleExtra("FINAL_DEST_LON", 0.0)
            finalDestName = intent.getStringExtra("FINAL_DEST_NAME") ?: ""

            Log.d("Navigation", "🚀 Modo de navegación: $navMode")

            
            if (!isValidCoordinate(destinationLat, destinationLon)) {
                Log.e("Navigation", "Coordenadas de destino inválidas desde intent")
                
                destinationLat = 4.5981
                destinationLon = -74.0758
                destinationName = "Centro de Bogotá"
            }

        } catch (e: Exception) {
            Log.e("Navigation", "Error obteniendo datos del intent: ${e.message}")
            
            destinationName = "Destino"
            destinationLat = 4.5981
            destinationLon = -74.0758
            startLat = 4.5709
            startLon = -74.2973
        }

        nextInstruction = "Bienvenido al modo navegación. Inicializando sistema..."

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        osrmClient = OSRMClient()

        setContent {
            NavigationScreen(
                destinationName = destinationName,
                distance = distance,
                duration = duration,
                arrivalTime = arrivalTime,
                nextInstruction = nextInstruction,
                currentStreet = currentStreet,
                nextStreet = nextStreet,
                isRecalculating = isRecalculating,
                isOffRoute = isOffRoute,
                isFollowingLocation = isFollowingLocation,
                isUserMoving = isUserMoving,
                showStartTripButton = showStartTripButton,
                onBackClick = { finish() },
                onStartTripClick = {
                    startTripToDestination()
                },
                onStopNavigation = {
                    speakInstruction("Navegación finalizada")
                    finish()
                },
                onCenterLocation = {
                    centerCameraWithModernEffect()
                    isFollowingLocation = true
                    isUserInteracting = false
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onMapViewReady = { view ->
                    mapView = view
                },
                onMapReady = { map ->
                    maplibreMap = map
                    initializeMapComponents()
                },
                onUserMapInteraction = { interacting ->
                    isUserInteracting = interacting
                    if (interacting) {
                        isFollowingLocation = false
                        lastUserInteractionTime = System.currentTimeMillis()
                    }
                }
            )
        }

        
        if (hasLocationPermissions()) {
            startLocationUpdates()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    
    private fun calculateBearing(newLocation: Location): Float {
        previousLocation?.let { prevLocation ->
            val bearing = prevLocation.bearingTo(newLocation)
            if (!bearing.isNaN() && abs(bearing) > 1.0) {
                return bearing
            }
        }
        return currentBearing
    }

    
    private fun smoothBearing(newBearing: Float, currentSmoothedBearing: Float): Float {
        val normalizedNew = normalizeBearing(newBearing)
        val normalizedCurrent = normalizeBearing(currentSmoothedBearing)

        var diff = normalizedNew - normalizedCurrent
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        return normalizedCurrent + diff * BEARING_SMOOTHING_FACTOR
    }

    private fun normalizeBearing(bearing: Float): Float {
        var normalized = bearing % 360
        if (normalized < 0) normalized += 360
        return normalized
    }

    
    private fun initializeMapComponents() {
        maplibreMap?.let { map ->
            
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                isMapReady = true

                
                setupCameraLimits(map)

                
                currentLocation?.let { location ->
                    val initialLatLng = LatLng(location.latitude, location.longitude)
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialLatLng)
                        .zoom(PERMANENT_ZOOM)
                        .tilt(PERMANENT_TILT)
                        .bearing(0.0)
                        .build()
                }

                
                setupMapInteractionListeners(map)

                
                startOSRMNavigation()
            }
        }
    }

    

    private fun setupCameraLimits(map: MapLibreMap) {
        try {
            
            map.setMinZoomPreference(MIN_ZOOM)
            map.setMaxZoomPreference(MAX_ZOOM)

            
            

            Log.d("Navigation", "📏 Límites de cámara configurados: Zoom $MIN_ZOOM-$MAX_ZOOM")

        } catch (e: Exception) {
            Log.e("Navigation", "❌ Error configurando límites de cámara: ${e.message}")
        }
    }

    
    private fun setupMapInteractionListeners(map: MapLibreMap) {
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE ||
                reason == MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
                isUserInteracting = true
                isFollowingLocation = false
                lastUserInteractionTime = System.currentTimeMillis()
            }
        }

        map.addOnCameraIdleListener {
            isUserInteracting = false
        }

        map.addOnMapClickListener { point ->
            isFollowingLocation = false
            isUserInteracting = true
            lastUserInteractionTime = System.currentTimeMillis()
            false
        }
    }

    
    private fun createRotatedUserIcon(bearing: Float, isMoving: Boolean): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 3f

        
        val baseColor = if (isMoving)
            android.graphics.Color.parseColor("#4CAF50")
        else
            android.graphics.Color.parseColor("#2196F3")

        
        paint.color = baseColor
        canvas.drawCircle(centerX, centerY, radius, paint)

        
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, radius - 1.5f, paint)

        
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE

        
        canvas.save()
        canvas.rotate(bearing, centerX, centerY)

        val path = android.graphics.Path()
        path.moveTo(centerX, centerY - radius * 0.6f)
        path.lineTo(centerX - radius * 0.3f, centerY + radius * 0.1f)
        path.lineTo(centerX + radius * 0.3f, centerY + radius * 0.1f)
        path.close()
        canvas.drawPath(path, paint)

        canvas.restore()

        return bitmap
    }

    
    private fun createDestinationIcon(): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        val centerX = size / 2f
        val centerY = size / 2f

        
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.parseColor("#FF4081")
        canvas.drawCircle(centerX, centerY - 10f, 15f, paint)

        
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY - 10f, 14f, paint)

        
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.parseColor("#FF4081")
        val path = android.graphics.Path()
        path.moveTo(centerX, centerY + 15f)
        path.lineTo(centerX - 12f, centerY - 5f)
        path.lineTo(centerX + 12f, centerY - 5f)
        path.close()
        canvas.drawPath(path, paint)

        return bitmap
    }

    
    private fun updateUserLocationMarkerSmooth(location: Location, bearing: Float) {
        val map = maplibreMap ?: return
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLocationUpdate < LOCATION_SMOOTHING_DELAY) {
            return
        }

        lastLocationUpdate = currentTime

        val currentLatLng = LatLng(location.latitude, location.longitude)

        
        val userIcon = createRotatedUserIcon(bearing, isUserMoving)

        if (userLocationMarker == null) {
            userLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(currentLatLng)
                    .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(userIcon))
            )
        } else {
            userLocationMarker?.position = currentLatLng
            userLocationMarker?.setIcon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(userIcon))
        }
    }

    
    private fun updateModernCameraPosition(location: Location, bearing: Float = 0f, forceCenter: Boolean = false) {
        val map = maplibreMap ?: return

        if (!isFollowingLocation && !forceCenter) {
            return
        }

        val currentLatLng = LatLng(location.latitude, location.longitude)

        map.easeCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(PERMANENT_ZOOM)
                    .tilt(PERMANENT_TILT)
                    .bearing(if (isUserMoving) bearing.toDouble() else map.cameraPosition.bearing)
                    .build()
            ), 1500
        )
    }

    
    private fun centerCameraWithModernEffect() {
        val map = maplibreMap ?: return
        val location = currentLocation ?: return

        val currentLatLng = LatLng(location.latitude, location.longitude)

        map.easeCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(PERMANENT_ZOOM)
                    .tilt(PERMANENT_TILT)
                    .bearing(if (isUserMoving) smoothedBearing.toDouble() else 0.0)
                    .build()
            ), 800
        )

        isFollowingLocation = true
        isUserInteracting = false

        Toast.makeText(this, "📍 Vista 3D activada", Toast.LENGTH_SHORT).show()
    }

    private fun updateRealTimeInstruction(currentLocation: Location) {
        if (isRecalculating || isOffRoute || currentSteps.isEmpty()) {
            return
        }

        val currentPoint = LatLng(currentLocation.latitude, currentLocation.longitude)

        val (foundStepIndex, distanceToStep) = findCurrentStep(currentPoint)

        if (foundStepIndex != -1) {
            val currentStep = currentSteps[foundStepIndex]

            if (shouldUpdateStep(foundStepIndex, distanceToStep)) {
                currentStepIndex = foundStepIndex
                distanceToNextStep = distanceToStep
                
                currentManeuverType = currentStep.maneuver.type
                currentManeuverModifier = currentStep.maneuver.modifier ?: ""
                distanceToManeuver = distanceToStep
                maneuverStreetName = currentStep.name ?: ""

                updateStreetInfo(currentStep, foundStepIndex)

                val newInstruction = generateSmartInstruction(currentStep, distanceToStep, foundStepIndex)
                if (newInstruction != nextInstruction) {
                    nextInstruction = newInstruction
                    lastInstructionUpdate = System.currentTimeMillis()
                }
                
                val progressiveVoice = getProgressiveVoiceInstruction(currentStep, distanceToStep, foundStepIndex)
                if (progressiveVoice != null && progressiveVoice != lastSpokenInstruction) {
                    speakInstruction(progressiveVoice)
                    lastSpokenInstruction = progressiveVoice
                }
            }
        } else {
            nextInstruction = "Siga la ruta hacia $destinationName"
        }
    }

    
    private fun findCurrentStep(currentPoint: LatLng): Pair<Int, Double> {
        var bestStepIndex = -1
        var minDistance = Double.MAX_VALUE

        val startIndex = max(0, currentStepIndex)
        val endIndex = min(currentSteps.size - 1, currentStepIndex + 3)

        for (i in startIndex..endIndex) {
            val step = currentSteps[i]
            val stepLocation = step.maneuver.location
            val stepPoint = LatLng(stepLocation[1], stepLocation[0])

            val distanceToStep = calculateDistance(currentPoint, stepPoint)

            if (distanceToStep < minDistance) {
                minDistance = distanceToStep
                bestStepIndex = i
            }
        }

        return Pair(bestStepIndex, minDistance)
    }

    
    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0].toDouble()
    }

    private fun shouldUpdateStep(newStepIndex: Int, distance: Double): Boolean {
        if (newStepIndex != currentStepIndex) return true
        if (abs(distance - distanceToNextStep) > 20.0) return true
        return System.currentTimeMillis() - lastInstructionUpdate > 10000
    }

    private fun updateStreetInfo(currentStep: OSRMStep, stepIndex: Int) {
        currentStreet = currentStep.name ?: "Calle principal"

        nextStreet = ""
        for (i in stepIndex + 1 until min(stepIndex + 2, currentSteps.size)) {
            val nextStep = currentSteps[i]
            if (nextStep.name != null && nextStep.name != currentStreet) {
                nextStreet = nextStep.name ?: ""
                break
            }
        }
    }

    private fun generateSmartInstruction(step: OSRMStep, distance: Double, stepIndex: Int): String {
        val isLastStep = stepIndex == currentSteps.size - 1
        val distText = formatDistanceToManeuver(distance)
        val streetName = step.name ?: "la siguiente calle"

        return when {
            isLastStep -> "✅ Ha llegado a su destino: $destinationName"
            step.maneuver.type == "depart" -> "Inicie el viaje hacia $destinationName"
            step.maneuver.type == "arrive" -> "✅ Ha llegado a su destino: $destinationName"
            step.maneuver.type == "turn" -> {
                val direction = when (step.maneuver.modifier) {
                    "left" -> "a la izquierda"
                    "right" -> "a la derecha"
                    "sharp left" -> "pronunciadamente a la izquierda"
                    "sharp right" -> "pronunciadamente a la derecha"
                    "slight left" -> "ligeramente a la izquierda"
                    "slight right" -> "ligeramente a la derecha"
                    "uturn" -> "en U"
                    else -> ""
                }
                "En $distText, gire $direction en $streetName"
            }
            step.maneuver.type == "continue" -> "Continúe recto por $streetName"
            step.maneuver.type == "roundabout" -> "En $distText, tome la rotonda"
            step.maneuver.type == "fork" -> "En $distText, tome el desvío"
            step.maneuver.type == "merge" -> "En $distText, incorpórese a la vía"
            else -> "Siga por $streetName"
        }
    }

    private fun generateVoiceInstruction(step: OSRMStep, distance: Double): String {
        return when (step.maneuver.type) {
            "turn" -> when (step.maneuver.modifier) {
                "left" -> "Gire a la izquierda"
                "right" -> "Gire a la derecha"
                "sharp left" -> "Gire pronunciadamente a la izquierda"
                "sharp right" -> "Gire pronunciadamente a la derecha"
                "slight left" -> "Gire ligeramente a la izquierda"
                "slight right" -> "Gire ligeramente a la derecha"
                "uturn" -> "Dé la vuelta"
                else -> "Gire"
            }
            "continue" -> "Continúe recto"
            "roundabout" -> "Tome la rotonda"
            "arrive" -> "Ha llegado a su destino"
            else -> "Siga la ruta"
        }
    }
    
    private fun getProgressiveVoiceInstruction(step: OSRMStep, distance: Double, stepIndex: Int): String? {
        if (stepIndex == lastStepIndexSpoken && distance > 30) return null
        if (System.currentTimeMillis() - lastVoiceTime < 3000) return null
        
        val basicManeuver = getBasicManeuverText(step)
        val streetName = step.name ?: "la próxima calle"
        
        return when {
            distance in 480.0..520.0 && lastStepIndexSpoken != stepIndex -> {
                lastStepIndexSpoken = stepIndex
                "En 500 metros, $basicManeuver"
            }
            distance in 180.0..220.0 && lastSpokenInstruction != "200m_$stepIndex" -> {
                lastSpokenInstruction = "200m_$stepIndex"
                "En 200 metros, $basicManeuver en $streetName"
            }
            distance in 80.0..120.0 && lastSpokenInstruction != "100m_$stepIndex" -> {
                lastSpokenInstruction = "100m_$stepIndex"
                "Prepárese para $basicManeuver"
            }
            distance <= 30.0 && lastSpokenInstruction != "now_$stepIndex" -> {
                lastSpokenInstruction = "now_$stepIndex"
                "Ahora, $basicManeuver"
            }
            step.maneuver.type == "arrive" && distance < 50 -> {
                "Ha llegado a su destino"
            }
            else -> null
        }
    }
    
    private fun getBasicManeuverText(step: OSRMStep): String {
        return when (step.maneuver.type) {
            "turn" -> when (step.maneuver.modifier) {
                "left" -> "gire a la izquierda"
                "right" -> "gire a la derecha"
                "sharp left" -> "gire pronunciadamente a la izquierda"
                "sharp right" -> "gire pronunciadamente a la derecha"
                "slight left" -> "gire ligeramente a la izquierda"
                "slight right" -> "gire ligeramente a la derecha"
                else -> "gire"
            }
            "continue" -> "continúe recto"
            "roundabout" -> "tome la rotonda"
            "fork" -> "tome el desvío"
            "merge" -> "incorpórese"
            else -> "siga"
        }
    }

    private fun shouldSpeakInstruction(step: OSRMStep, distance: Double, stepIndex: Int): Boolean {
        if (stepIndex == lastStepIndexSpoken) return false
        if (System.currentTimeMillis() - lastVoiceTime < 3000) return false

        val speakDistance = when (step.maneuver.type) {
            "turn" -> 100.0
            "roundabout" -> 120.0
            "fork" -> 100.0
            "exit" -> 150.0
            "arrive" -> 50.0
            else -> 150.0
        }
        return distance <= speakDistance || step.maneuver.type == "arrive"
    }

    
    private var lastVoiceTime: Long = 0
    private var isSpeaking = false
    private val voiceQueue = mutableListOf<String>()

    private fun speakInstruction(instruction: String) {
        if (instruction.isBlank() || !isTTSInitialized) return

        val currentTime = System.currentTimeMillis()

        if (isSpeaking || currentTime - lastVoiceTime < 3000) {
            if (!voiceQueue.contains(instruction)) {
                voiceQueue.add(instruction)
            }
            return
        }

        isSpeaking = true
        lastVoiceTime = currentTime

        try {
            textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, "navigation")

            android.os.Handler(mainLooper).postDelayed({
                isSpeaking = false
                processNextVoiceInstruction()
            }, 2500)
        } catch (e: Exception) {
            Log.e("TTS", "Error al hablar: ${e.message}")
            isSpeaking = false
        }
    }

    private fun processNextVoiceInstruction() {
        if (voiceQueue.isNotEmpty() && !isSpeaking) {
            val nextInstruction = voiceQueue.removeAt(0)
            speakInstruction(nextInstruction)
        }
    }

    
    private fun checkIfOffRoute(currentLocation: Location) {
        if (currentRoutePoints.isEmpty() || isRecalculating) return

        val currentPoint = LatLng(currentLocation.latitude, currentLocation.longitude)
        val minDistanceToRoute = calculateMinDistanceToRoute(currentPoint)

        val isCurrentlyOffRoute = minDistanceToRoute > 200.0

        if (isCurrentlyOffRoute && !isOffRoute) {
            Log.w("Navigation", "Desvío detectado: ${minDistanceToRoute.roundToInt()} metros")
            isOffRoute = true
            nextInstruction = "Se ha desviado de la ruta. Recalculando..."
            speakInstruction("Se ha desviado de la ruta. Recalculando.")
            recalculateRouteFromCurrentLocation()
        } else if (!isCurrentlyOffRoute && isOffRoute) {
            Log.d("Navigation", "Volvió a la ruta")
            isOffRoute = false
        }
    }

    private fun calculateMinDistanceToRoute(point: LatLng): Double {
        var minDistance = Double.MAX_VALUE

        val startIndex = max(0, currentStepIndex - 5)
        val endIndex = min(currentRoutePoints.size - 1, currentStepIndex + 10)

        for (i in startIndex until endIndex) {
            if (i + 1 >= currentRoutePoints.size) break

            val segmentStart = currentRoutePoints[i]
            val segmentEnd = currentRoutePoints[i + 1]
            val distanceToSegment = calculateDistanceToSegment(point, segmentStart, segmentEnd)

            if (distanceToSegment < minDistance) {
                minDistance = distanceToSegment
            }
        }

        return minDistance
    }

    private fun calculateDistanceToSegment(point: LatLng, segmentStart: LatLng, segmentEnd: LatLng): Double {
        val A = point.latitude - segmentStart.latitude
        val B = point.longitude - segmentStart.longitude
        val C = segmentEnd.latitude - segmentStart.latitude
        val D = segmentEnd.longitude - segmentStart.longitude

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        var param = -1.0

        if (lenSq != 0.0) {
            param = dot / lenSq
        }

        val xx: Double
        val yy: Double

        if (param < 0) {
            xx = segmentStart.latitude
            yy = segmentStart.longitude
        } else if (param > 1) {
            xx = segmentEnd.latitude
            yy = segmentEnd.longitude
        } else {
            xx = segmentStart.latitude + param * C
            yy = segmentStart.longitude + param * D
        }

        val dx = point.latitude - xx
        val dy = point.longitude - yy
        return sqrt(dx * dx + dy * dy) * 111319.0
    }

    
    private fun recalculateRouteFromCurrentLocation() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastRecalculationTime < MIN_RECALCULATION_INTERVAL) {
            Log.d("Navigation", "Recálculo demasiado frecuente, ignorando...")
            return
        }

        isRecalculating = true
        lastRecalculationTime = currentTime

        CoroutineScope(Dispatchers.Main).launch {
            try {
                currentLocation?.let { location ->
                    recalculateRoute(location)
                } ?: run {
                    nextInstruction = "Esperando ubicación para recalcular..."
                    android.os.Handler(mainLooper).postDelayed({
                        isRecalculating = false
                        isOffRoute = false
                    }, 3000)
                }
            } catch (e: Exception) {
                Log.e("Navigation", "Error en recálculo: ${e.message}")
                nextInstruction = "Error en recálculo. Continúe hacia el destino."
                isRecalculating = false
                isOffRoute = false
            }
        }
    }

    private suspend fun recalculateRoute(currentLocation: Location) {
        try {
            val currentPoint = LatLng(currentLocation.latitude, currentLocation.longitude)
            val destinationPoint = LatLng(destinationLat, destinationLon)

            Log.d("Navigation", "Recalculando ruta desde: $currentPoint hacia: $destinationPoint")

            val routeResponse = osrmClient.getRoute(currentPoint, destinationPoint)

            if (routeResponse != null && routeResponse.routes.isNotEmpty()) {
                val newRoute = routeResponse.routes[0]
                currentRoute = newRoute
                currentRoutePoints = osrmClient.decodePolylineToLatLng(newRoute.geometry)
                currentSteps = newRoute.legs.firstOrNull()?.steps ?: emptyList()
                currentStepIndex = 0
                lastStepIndexSpoken = -1

                showRealRouteOnMap(newRoute)
                updateRealNavigationInfo(newRoute)

                Log.d("Navigation", "Ruta recalculada: ${currentSteps.size} steps, ${currentRoutePoints.size} puntos")

                speakInstruction("Nueva ruta calculada. Siga las indicaciones.")
                Toast.makeText(this@NavigationActivity, "🔄 Ruta actualizada", Toast.LENGTH_SHORT).show()
                isOffRoute = false
            } else {
                throw Exception("No se pudo calcular la ruta")
            }
        } catch (e: Exception) {
            Log.e("Navigation", "Error recalculando ruta: ${e.message}")
            nextInstruction = "Error recalculando. Continúe hacia el destino."
            speakInstruction("Error al recalcular la ruta. Continúe hacia el destino.")
        } finally {
            isRecalculating = false
        }
    }

    
    private fun startOSRMNavigation() {
        nextInstruction = "Calculando la mejor ruta hacia $destinationName..."
        speakInstruction("Calculando la mejor ruta hacia $destinationName")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!isValidCoordinate(destinationLat, destinationLon)) {
                    Log.e("Navigation", "❌ Coordenadas de destino inválidas: $destinationLat, $destinationLon")
                    nextInstruction = "Error: Ubicación de destino no válida"
                    speakInstruction("Error en la ubicación del destino")
                    showErrorOnMap("Destino no válido")
                    return@launch
                }

                val currentLoc = getValidCurrentLocation()
                if (currentLoc == null) {
                    Log.e("Navigation", "❌ No se pudo obtener ubicación actual")
                    nextInstruction = "Buscando su ubicación..."
                    speakInstruction("Buscando su ubicación actual")
                    startLat = 4.5709
                    startLon = -74.2973
                } else {
                    startLat = currentLoc.latitude
                    startLon = currentLoc.longitude
                    Log.d("Navigation", "📍 Usando ubicación actual: $startLat, $startLon")
                }

                if (!isValidCoordinate(startLat, startLon)) {
                    Log.e("Navigation", "❌ Coordenadas de inicio inválidas: $startLat, $startLon")
                    startLat = 4.5709
                    startLon = -74.2973
                }

                val startPoint = LatLng(startLat, startLon)
                val destinationPoint = LatLng(destinationLat, destinationLon)

                Log.d("Navigation", "📍 Calculando ruta desde: ($startLat, $startLon)")
                Log.d("Navigation", "🎯 Hacia destino: ($destinationLat, $destinationLon) - $destinationName")

                val routeResponse = osrmClient.getRoute(startPoint, destinationPoint, "driving")

                if (routeResponse != null && routeResponse.routes.isNotEmpty()) {
                    val route = routeResponse.routes[0]
                    currentRoute = route
                    currentRoutePoints = osrmClient.decodePolylineToLatLng(route.geometry)
                    currentSteps = route.legs.firstOrNull()?.steps ?: emptyList()
                    currentStepIndex = 0
                    lastStepIndexSpoken = -1

                    Log.d("Navigation", "✅ Ruta calculada: ${route.distance}m, ${route.duration}s, ${currentSteps.size} pasos")

                    showRealRouteOnMap(route)
                    updateRealNavigationInfo(route)

                    nextInstruction = "Ruta lista. Inicie el viaje hacia $destinationName."
                    speakInstruction("Ruta lista. Inicie el viaje hacia $destinationName.")

                } else {
                    Log.w("Navigation", "⚠️ No se pudo calcular ruta con OSRM, usando fallback")
                    createFallbackRoute()
                    nextInstruction = "Navegando hacia $destinationName"
                    speakInstruction("Navegando hacia $destinationName")
                }

            } catch (e: Exception) {
                Log.e("Navigation", "❌ Error en navegación: ${e.message}")
                e.printStackTrace()
                createFallbackRoute()
                nextInstruction = "Navegando hacia $destinationName"
                speakInstruction("Navegación activada hacia $destinationName")
            }
        }
    }

    
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0 &&
                latitude != 0.0 && longitude != 0.0
    }

    
    private fun getValidCurrentLocation(): Location? {
        return currentLocation?.takeIf {
            isValidCoordinate(it.latitude, it.longitude)
        }
    }

    
    private fun showErrorOnMap(errorMessage: String) {
        val map = maplibreMap ?: return

        map.getStyle { style ->
            val bogota = LatLng(4.5709, -74.2973)

            map.easeCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(bogota)
                        .zoom(5.0)
                        .build()
                ), 1000
            )

            map.addMarker(
                MarkerOptions()
                    .position(bogota)
                    .title("Error de Navegación")
                    .snippet(errorMessage)
            )
        }
    }

    
    private fun showRealRouteOnMap(route: OSRMRoute) {
        val map = maplibreMap ?: return

        Log.d("Navigation", "🎯 Intentando mostrar ruta en el mapa...")

        map.getStyle { style ->
            try {
                Log.d("Navigation", "🗺️ Estilo del mapa cargado, agregando ruta...")

                val destinationPoint = LatLng(destinationLat, destinationLon)
                if (destinationMarker == null) {
                    destinationMarker = map.addMarker(
                        MarkerOptions()
                            .position(destinationPoint)
                            .title(destinationName)
                            .snippet("Destino final")
                            .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createDestinationIcon()))
                    )
                    Log.d("Navigation", "📍 Marcador de destino agregado: $destinationPoint")
                }

                val routeGeoJSON = createRouteGeoJSON(route.geometry)
                Log.d("Navigation", "📄 GeoJSON creado (${routeGeoJSON.length} caracteres)")

                val routeSourceId = "route-source"
                val routeLayerId = "route-layer"

                cleanPreviousRoute(style, routeSourceId, routeLayerId)

                val routeSource = GeoJsonSource(routeSourceId)
                routeSource.setGeoJson(routeGeoJSON)
                style.addSource(routeSource)
                Log.d("Navigation", "✅ Fuente de ruta agregada: $routeSourceId")

                val routeLayer = LineLayer(routeLayerId, routeSourceId).withProperties(
                    PropertyFactory.lineColor("#2196F3"),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineCap(LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(LINE_JOIN_ROUND)
                )
                style.addLayer(routeLayer)
                Log.d("Navigation", "✅ Capa de ruta agregada: $routeLayerId")

                val points = osrmClient.decodePolylineToLatLng(route.geometry)
                if (points.isNotEmpty()) {
                    try {
                        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                        points.forEach { point ->
                            bounds.include(point)
                        }
                        val latLngBounds = bounds.build()

                        map.easeCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                                latLngBounds, 100
                            ), 1000
                        )

                        
                        android.os.Handler(mainLooper).postDelayed({
                            currentLocation?.let { location ->
                                updateModernCameraPosition(location, smoothedBearing, true)
                            }
                        }, 1200)

                    } catch (e: Exception) {
                        Log.e("Navigation", "❌ Error ajustando cámara: ${e.message}")
                        val midPoint = points[points.size / 2]
                        map.easeCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(midPoint)
                                    .zoom(PERMANENT_ZOOM)
                                    .tilt(PERMANENT_TILT)
                                    .build()
                            ), 1000
                        )
                    }
                }

                Log.d("Navigation", "🎉 ¡Ruta debería ser visible ahora!")

            } catch (e: Exception) {
                Log.e("Navigation", "❌ Error mostrando ruta: ${e.message}")
                e.printStackTrace()
                showRouteAlternative(route)
            }
        }
    }

    
    private fun cleanPreviousRoute(style: Style, sourceId: String, layerId: String) {
        try {
            style.getLayer(layerId)?.let { layer ->
                style.removeLayer(layer)
                Log.d("Navigation", "🗑️ Capa anterior removida: $layerId")
            }
        } catch (e: Exception) {
            Log.d("Navigation", "ℹ️ No había capa anterior o ya fue removida: $layerId")
        }

        try {
            style.getSourceAs<GeoJsonSource>(sourceId)?.let { source ->
                style.removeSource(source)
                Log.d("Navigation", "🗑️ Fuente anterior removida: $sourceId")
            }
        } catch (e: Exception) {
            Log.d("Navigation", "ℹ️ No había fuente anterior o ya fue removida: $sourceId")
        }
    }

    
    private fun showRouteAlternative(route: OSRMRoute) {
        val map = maplibreMap ?: return

        try {
            currentLocation?.let { location ->
                val startPoint = LatLng(location.latitude, location.longitude)
                val endPoint = LatLng(destinationLat, destinationLon)

                userLocationMarker?.remove()
                destinationMarker?.remove()

                userLocationMarker = map.addMarker(
                    MarkerOptions()
                        .position(startPoint)
                        .title("Tu ubicación")
                        .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createRotatedUserIcon(smoothedBearing, isUserMoving)))
                )

                destinationMarker = map.addMarker(
                    MarkerOptions()
                        .position(endPoint)
                        .title(destinationName)
                        .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createDestinationIcon()))
                )

                Log.d("Navigation", "✅ Mostrando ruta alternativa con marcadores")
            }
        } catch (e: Exception) {
            Log.e("Navigation", "❌ Error en método alternativo: ${e.message}")
        }
    }

    
    private fun createRouteGeoJSON(encodedPolyline: String): String {
        val points = osrmClient.decodePolylineToLatLng(encodedPolyline)

        Log.d("Navigation", "📍 Puntos decodificados: ${points.size}")

        val validPoints = points
            .filter { point ->
                point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
            }

        if (validPoints.size < 2) {
            Log.w("Navigation", "⚠️ No hay suficientes puntos válidos, usando ruta simple")
            return createSimpleRouteGeoJSON()
        }

        Log.d("Navigation", "🔍 DEBUG COORDENADAS:")
        Log.d("Navigation", "   Primera: (${validPoints.first().latitude}, ${validPoints.first().longitude})")
        Log.d("Navigation", "   Última: (${validPoints.last().latitude}, ${validPoints.last().longitude})")

        val coordinates = validPoints.joinToString(",") {
            "[${it.longitude},${it.latitude}]"
        }

        val geoJSON = """
    {
        "type": "Feature",
        "geometry": {
            "type": "LineString",
            "coordinates": [$coordinates]
        },
        "properties": {
            "name": "Ruta hacia $destinationName",
            "stroke": "#2196F3",
            "stroke-width": 6
        }
    }
    """.trimIndent()

        Log.d("Navigation", "📍 GeoJSON creado con ${validPoints.size} puntos")

        return geoJSON
    }

    
    private fun createSimpleRouteGeoJSON(): String {
        val startPoint = LatLng(4.5709, -74.2973)
        val endPoint = LatLng(destinationLat, destinationLon)

        val validEndPoint = if (destinationLat in -90.0..90.0 && destinationLon in -180.0..180.0) {
            endPoint
        } else {
            LatLng(4.5981, -74.0758)
        }

        val coordinates = "[${startPoint.longitude},${startPoint.latitude}],[${validEndPoint.longitude},${validEndPoint.latitude}]"

        return """
    {
        "type": "Feature",
        "geometry": {
            "type": "LineString",
            "coordinates": [$coordinates]
        },
        "properties": {
            "name": "Ruta simple",
            "stroke": "#FF0000",
            "stroke-width": 4
        }
    }
    """.trimIndent()
    }

    
    private fun createFallbackRoute() {
        Log.d("Navigation", "🔄 Creando ruta fallback...")

        val validStartLat = if (isValidCoordinate(startLat, startLon)) startLat else 4.5709
        val validStartLon = if (isValidCoordinate(startLat, startLon)) startLon else -74.2973
        val validDestLat = if (isValidCoordinate(destinationLat, destinationLon)) destinationLat else 4.5981
        val validDestLon = if (isValidCoordinate(destinationLat, destinationLon)) destinationLon else -74.0758

        val startPoint = LatLng(validStartLat, validStartLon)
        val destinationPoint = LatLng(validDestLat, validDestLon)

        currentRoutePoints = listOf(startPoint, destinationPoint)

        currentSteps = listOf(
            OSRMStep(
                geometry = "",
                distance = calculateDistance(startPoint, destinationPoint),
                duration = calculateDistance(startPoint, destinationPoint) / 10.0,
                name = "Hacia $destinationName",
                maneuver = OSRMManeuver(
                    type = "depart",
                    modifier = null,
                    location = listOf(validStartLon, validStartLat)
                )
            ),
            OSRMStep(
                geometry = "",
                distance = 0.0,
                duration = 0.0,
                name = destinationName,
                maneuver = OSRMManeuver(
                    type = "arrive",
                    modifier = null,
                    location = listOf(validDestLon, validDestLat)
                )
            )
        )

        currentStepIndex = 0
        lastStepIndexSpoken = -1

        showFallbackRouteOnMap()
        updateFallbackNavigationInfo()

        Log.d("Navigation", "✅ Ruta fallback creada con coordenadas válidas")
    }

    private fun showFallbackRouteOnMap() {
        val map = maplibreMap ?: return

        map.getStyle { style ->
            val destinationPoint = LatLng(destinationLat, destinationLon)
            if (destinationMarker == null) {
                destinationMarker = map.addMarker(
                    MarkerOptions()
                        .position(destinationPoint)
                        .title(destinationName)
                        .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createDestinationIcon()))
                )
            }
        }
    }

    
    private fun updateFallbackNavigationInfo() {
        val startPoint = LatLng(startLat, startLon)
        val destinationPoint = LatLng(destinationLat, destinationLon)
        val distanceMeters = calculateDistance(startPoint, destinationPoint)

        distance = if (distanceMeters < 1000) {
            "${distanceMeters.roundToInt()} m"
        } else {
            "%.1f km".format(distanceMeters / 1000)
        }

        val durationSeconds = distanceMeters / 5.0
        duration = when {
            durationSeconds < 60 -> "< 1 min"
            durationSeconds < 3600 -> "${(durationSeconds / 60).toInt()} min"
            else -> {
                val hours = (durationSeconds / 3600).toInt()
                val minutes = ((durationSeconds % 3600) / 60).toInt()
                if (minutes == 0) "$hours h" else "$hours h $minutes min"
            }
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, durationSeconds.toInt())
        arrivalTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
    }

    private fun updateRealNavigationInfo(route: OSRMRoute) {
        val distanceMeters = route.distance
        val durationSeconds = route.duration

        distance = if (distanceMeters < 1000) {
            "${distanceMeters.roundToInt()} m"
        } else {
            "%.1f km".format(distanceMeters / 1000)
        }

        duration = when {
            durationSeconds < 60 -> "< 1 min"
            durationSeconds < 3600 -> "${(durationSeconds / 60).toInt()} min"
            else -> {
                val hours = (durationSeconds / 3600).toInt()
                val minutes = ((durationSeconds % 3600) / 60).toInt()
                if (minutes == 0) "$hours h" else "$hours h $minutes min"
            }
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, durationSeconds.toInt())
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        arrivalTime = dateFormat.format(calendar.time)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(true)
            .build()

        fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it
                
                updateModernCameraPosition(it, currentBearing, true)
                updateNavigationInfo()
            }
        }
    }

    private fun updateNavigationInfo() {
        currentLocation?.let { current ->
            val destination = Location("destination").apply {
                latitude = destinationLat
                longitude = destinationLon
            }
            updateNavigationInfo(current, destination)
        }
    }

    private fun updateNavigationInfo(current: Location, destination: Location) {
        val distanceMeters = current.distanceTo(destination).toFloat()
        val distanceKm = distanceMeters / 1000.0

        distance = if (distanceKm < 1) {
            "${distanceMeters.roundToInt()} m"
        } else {
            "%.1f km".format(distanceKm)
        }

        val speedKmh = 30.0
        val durationHours = distanceKm / speedKmh
        val durationMinutes = (durationHours * 60).toInt()

        duration = when {
            durationMinutes < 1 -> "< 1 min"
            durationMinutes < 60 -> "$durationMinutes min"
            else -> {
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60
                if (minutes == 0) "$hours h" else "$hours h $minutes min"
            }
        }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, durationMinutes)
        arrivalTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
    }
    
    private fun checkAndUpdateNightMode() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        isNightMode = hour < 6 || hour >= 19
    }
    
    private fun updateTripProgress() {
        if (currentRoute == null || currentSteps.isEmpty()) return
        
        totalDistance = currentRoute!!.distance.toDouble()
        val remainingDist = calculateRemainingDistance()
        
        distanceTraveled = totalDistance - remainingDist
        tripProgress = if (totalDistance > 0) {
            (distanceTraveled / totalDistance).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    private fun calculateRemainingDistance(): Double {
        var remaining = 0.0
        for (i in currentStepIndex until currentSteps.size) {
            remaining += currentSteps[i].distance
        }
        return remaining
    }
    
    private fun formatDistanceToManeuver(distance: Double): String {
        return when {
            distance > 1000 -> "${(distance / 1000).roundToInt()} km"
            distance > 500 -> "${(distance / 100).roundToInt() * 100} m"
            distance > 100 -> "${(distance / 50).roundToInt() * 50} m"
            else -> "${distance.roundToInt()} m"
        }
    }

    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Idioma español no soportado")
            } else {
                isTTSInitialized = true
            }
        }
    }

    
    @SuppressLint("ObsoleteSdkInt")
    private fun setupFullScreen() {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility =
            (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e("NavigationActivity", "Error removiendo location updates", e)
        }
        userLocationMarker?.remove()
        destinationMarker?.remove()
        mapView?.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    private fun startTripToDestination() {
        navMode = "TRIP"
        showStartTripButton = false
        
        // Actualizar destino
        destinationLat = finalDestLat
        destinationLon = finalDestLon
        destinationName = finalDestName
        
        // Actualizar marcador de destino en el mapa
        maplibreMap?.let { map ->
            destinationMarker?.let { marker ->
                marker.position = LatLng(destinationLat, destinationLon)
                marker.title = destinationName
                marker.snippet = "Destino final"
            }
        }
        
        speakInstruction("Iniciando viaje hacia $destinationName")
        Toast.makeText(this, "🚀 Iniciando viaje...", Toast.LENGTH_SHORT).show()
        
        // Forzar recálculo inmediato
        recalculateRouteFromCurrentLocation()
    }

    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    
    @Composable
    fun NavigationScreen(
        destinationName: String,
        distance: String,
        duration: String,
        arrivalTime: String,
        nextInstruction: String,
        currentStreet: String,
        nextStreet: String,
        isRecalculating: Boolean,
        isOffRoute: Boolean,
        isFollowingLocation: Boolean,
        isUserMoving: Boolean,
        showStartTripButton: Boolean = false,
        onBackClick: () -> Unit,
        onStartTripClick: () -> Unit = {},
        onStopNavigation: () -> Unit,
        onCenterLocation: () -> Unit,
        onMapViewReady: (MapView) -> Unit,
        onMapReady: (MapLibreMap) -> Unit,
        onUserMapInteraction: (Boolean) -> Unit
    ) {
        val cardColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        onCreate(Bundle())
                        getMapAsync { map ->
                            onMapReady(map)
                        }
                        onMapViewReady(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOffRoute) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    else cardColor
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Destino",
                                tint = if (isOffRoute) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = destinationName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isOffRoute) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isUserMoving) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "En movimiento",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            if (isOffRoute) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Desviado",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            if (isRecalculating) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    
                    Text(
                        text = when {
                            isRecalculating -> "🔄 Recalculando ruta..."
                            isOffRoute -> "⚠️ Buscando nueva ruta..."
                            else -> nextInstruction
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = when {
                            isOffRoute -> MaterialTheme.colorScheme.onErrorContainer
                            isRecalculating -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    
                    if (currentStreet.isNotEmpty() && !isRecalculating && !isOffRoute) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "En: $currentStreet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    if (nextStreet.isNotEmpty() && !isRecalculating && !isOffRoute) {
                        Text(
                            text = "Próximo: $nextStreet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        NavigationInfoItem("📏 Distancia", distance, MaterialTheme.colorScheme.primary)
                        NavigationInfoItem("⏱️ Tiempo", duration, MaterialTheme.colorScheme.primary)
                        NavigationInfoItem("🕒 Llegada", arrivalTime, MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        
                        Button(
                            onClick = onCenterLocation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowingLocation)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "Centrar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isFollowingLocation) "Siguiendo" else "Centrar",
                                fontSize = 14.sp
                            )
                        }

                        if (showStartTripButton) {
                            Button(
                                onClick = onStartTripClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50) // Verde
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = "Iniciar Viaje",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Iniciar Viaje", fontSize = 14.sp)
                            }
                        }

                        
                        Button(
                            onClick = onStopNavigation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Finalizar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Finalizar", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NavigationInfoItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}