package com.amurayada.yallego

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.amurayada.yallego.matching.repository.RealTripMatchingRepository
import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amurayada.yallego.data.AppPreferences
import com.amurayada.yallego.viewmodel.AuthViewModel
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val userType: String = "usuario",
    val memberSince: String = "",
    val totalTrips: Int = 0,
    val rating: Double = 0.0,
    val monthlyTrips: Int = 0,
    val totalSpent: Double = 0.0
)


data class PlaceResult(
    val name: String,
    val lat: Double,
    val lon: Double,
    val address: String = "",
    val type: String = "location"
)

data class RouteState(
    val selectedDestinations: List<PlaceResult> = emptyList(),
    val currentSearchResults: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false
)

data class UserContributedPlace(
    val id: String = "",
    val name: String,
    val lat: Double,
    val lon: Double,
    val address: String = "",
    val type: String,
    val category: String,
    val description: String = "",
    val userId: String = "",
    val userName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending"
)

data class ContributionState(
    val contributedPlaces: List<UserContributedPlace> = emptyList(),
    val isAddingPlace: Boolean = false,
    val tempPlace: UserContributedPlace? = null,
    val selectedPosition: LatLng? = null,
    val editingPlace: UserContributedPlace? = null,
    val showManagePlaces: Boolean = false
)

val placeCategories = listOf(
    "Pueblo", "Ciudad", "Municipio", "Corregimiento", "Vereda", "Restaurante",
    "Cafetería", "Hotel", "Farmacia", "Supermercado", "Hospital", "Escuela",
    "Universidad", "Parque", "Estación de Servicio", "Banco", "Centro Comercial",
    "Tienda", "Gimnasio", "Museo", "Teatro", "Biblioteca", "Iglesia", "Estadio",
    "Aeropuerto", "Terminal", "Plaza", "Monumento", "Otro"
)

class UserPreferences(private val context: Context) {
    private val sharedPref = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
    private val contributionsPref = context.getSharedPreferences("user_contributions", Context.MODE_PRIVATE)

    
    fun saveUserData(name: String, email: String, phone: String) {
        with(sharedPref.edit()) {
            putString("user_name", name)
            putString("user_email", email)
            putString("user_phone", phone)
            
            if (sharedPref.getString("member_since", "").isNullOrBlank()) {
                putString("member_since", SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()))
            }
            apply()
        }
    }

    fun getUserData(): UserProfile {
        return UserProfile(
            name = sharedPref.getString("user_name", "") ?: "",
            email = sharedPref.getString("user_email", "") ?: "",
            phone = sharedPref.getString("user_phone", "") ?: "",
            memberSince = sharedPref.getString("member_since", "") ?: "",
            totalTrips = sharedPref.getInt("total_trips", 0),
            rating = sharedPref.getFloat("rating", 0.0f).toDouble(),
            monthlyTrips = sharedPref.getInt("monthly_trips", 0),
            totalSpent = sharedPref.getFloat("total_spent", 0f).toDouble()
        )
    }

    fun updateTripStats(amount: Double) {
        val prefs = sharedPref
        val currentTrips = prefs.getInt("total_trips", 0)
        val currentMonthly = prefs.getInt("monthly_trips", 0)
        val currentTotal = prefs.getFloat("total_spent", 0f)

        with(prefs.edit()) {
            putInt("total_trips", currentTrips + 1)
            putInt("monthly_trips", currentMonthly + 1)
            putFloat("total_spent", currentTotal + amount.toFloat())
            apply()
        }
    }

    
    fun saveContribution(place: UserContributedPlace) {
        val contributions = getContributions().toMutableList()
        val existingIndex = contributions.indexOfFirst { it.id == place.id }
        if (existingIndex != -1) {
            contributions[existingIndex] = place
        } else {
            contributions.add(place)
        }
        saveContributionsToPrefs(contributions)
    }

    fun deleteContribution(placeId: String) {
        val contributions = getContributions().toMutableList()
        contributions.removeAll { it.id == placeId }
        saveContributionsToPrefs(contributions)
    }

    private fun saveContributionsToPrefs(contributions: List<UserContributedPlace>) {
        val jsonArray = JSONArray()
        contributions.forEach { contribution ->
            val jsonObject = JSONObject().apply {
                put("id", contribution.id)
                put("name", contribution.name)
                put("lat", contribution.lat)
                put("lon", contribution.lon)
                put("address", contribution.address)
                put("type", contribution.type)
                put("category", contribution.category)
                put("description", contribution.description)
                put("userId", contribution.userId)
                put("userName", contribution.userName)
                put("timestamp", contribution.timestamp)
                put("status", contribution.status)
            }
            jsonArray.put(jsonObject)
        }
        contributionsPref.edit().putString("user_contributions", jsonArray.toString()).apply()
    }

    fun getContributions(): List<UserContributedPlace> {
        val jsonString = contributionsPref.getString("user_contributions", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val contributions = mutableListOf<UserContributedPlace>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            contributions.add(
                UserContributedPlace(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    lat = jsonObject.getDouble("lat"),
                    lon = jsonObject.getDouble("lon"),
                    address = jsonObject.getString("address"),
                    type = jsonObject.getString("type"),
                    category = jsonObject.getString("category"),
                    description = jsonObject.getString("description"),
                    userId = jsonObject.getString("userId"),
                    userName = jsonObject.getString("userName"),
                    timestamp = jsonObject.getLong("timestamp"),
                    status = jsonObject.getString("status")
                )
            )
        }
        return contributions.sortedByDescending { it.timestamp }
    }
}


private suspend fun searchPlacesRealTime(query: String, context: Context): List<PlaceResult> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=15&countrycodes=co"
            val connection = URL(url).openConnection()
            connection.setRequestProperty("User-Agent", "YaLlegoApp/1.0")
            val jsonText = connection.getInputStream().bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonText)
            val results = mutableListOf<PlaceResult>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val name = item.getString("display_name").split(",").firstOrNull() ?: "Lugar desconocido"
                val lat = item.getDouble("lat")
                val lon = item.getDouble("lon")
                val address = item.getString("display_name")
                results.add(PlaceResult(name, lat, lon, address))
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            getLocalSearchResults(query)
        }
    }
}

private fun getLocalSearchResults(query: String): List<PlaceResult> {
    val lowerQuery = query.lowercase()
    val colombiaPlaces = listOf(
        PlaceResult("Universidad de Sucre - Sincelejo", 9.3046, -75.3978, "Sincelejo, Sucre"),
        PlaceResult("Universidad Nacional de Colombia - Bogotá", 4.6373, -74.0840, "Bogotá, Cundinamarca"),
        PlaceResult("Universidad de Antioquia - Medellín", 6.2676, -75.5690, "Medellín, Antioquia"),
        PlaceResult("Universidad del Valle - Cali", 3.3750, -76.5320, "Cali, Valle del Cauca"),
        PlaceResult("Centro Comercial Santafé - Bogotá", 4.6824, -74.0462, "Bogotá"),
        PlaceResult("Aeropuerto El Dorado - Bogotá", 4.7016, -74.1469, "Bogotá"),
        PlaceResult("Hospital San Ignacio - Bogotá", 4.6283, -74.0656, "Bogotá"),
        PlaceResult("Monserrate - Bogotá", 4.6047, -74.0557, "Bogotá"),
        PlaceResult("Centro Comercial Aventura - Sincelejo", 9.2950, -75.3978, "Sincelejo, Sucre"),
        PlaceResult("Terminal de Sincelejo", 9.3019, -75.3978, "Sincelejo")
    )
    return colombiaPlaces.filter {
        it.name.lowercase().contains(lowerQuery) || it.address.lowercase().contains(lowerQuery)
    }
}

class HomeActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userPreferences: UserPreferences
    private lateinit var authViewModel: AuthViewModel
    private var currentLocation: Location? = null
    private var isFollowingLocation = true
    private var areLocationUpdatesRunning = false

    
    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private var userLocationMarker: Marker? = null
    private var destinationMarkers: MutableList<Marker> = mutableListOf()
    private var contributionMarkers: MutableList<Marker> = mutableListOf()
    private var tempPositionMarker: Marker? = null

    private var contributionState by mutableStateOf(ContributionState())
    private val firestoreService = com.amurayada.yallego.service.FirestoreService()

    
    private lateinit var tripMatchingManager: com.amurayada.yallego.matching.TripMatchingManager
    private lateinit var notificationService: com.amurayada.yallego.services.NotificationService
    private var isMatching by mutableStateOf(false)
    private var availableDrivers by mutableStateOf<List<com.amurayada.yallego.models.DriverMatch>>(emptyList())
    private var selectedDriver by mutableStateOf<com.amurayada.yallego.models.DriverMatch?>(null)
    private var showDriverSelection by mutableStateOf(false)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentLocation = location
            if (isFollowingLocation) {
                updateUserLocation(location)
            }
            updateUserLocationMarker(location)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        removeActionBar()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        userPreferences = UserPreferences(this)
        authViewModel = AuthViewModel()

        
        notificationService = com.amurayada.yallego.services.NotificationService(this)
        tripMatchingManager = com.amurayada.yallego.matching.TripMatchingManager(this).apply {
            
            onDriversFound = { drivers ->
                availableDrivers = drivers
                showDriverSelection = true
                isMatching = false

                
                notificationService.showDriversFoundNotification(drivers.size)
            }

            onBestDriverSelected = { driver ->
                selectedDriver = driver
                showDriverSelection = false

                
                notificationService.showDriverAssignedNotification(driver)

                Toast.makeText(
                    this@HomeActivity,
                    "✅ Conductor asignado: ${driver.driverName}",
                    Toast.LENGTH_LONG
                ).show()
            }

            onMatchingTimeout = {
                isMatching = false
                showDriverSelection = false
                Toast.makeText(
                    this@HomeActivity,
                    "⏰ Tiempo agotado. No se encontraron conductores",
                    Toast.LENGTH_LONG
                ).show()
            }

            onMatchingError = { error ->
                isMatching = false
                showDriverSelection = false
                Toast.makeText(this@HomeActivity, "❌ $error", Toast.LENGTH_LONG).show()
            }

            onDriverLocationUpdate = { driver ->
                
                selectedDriver = driver
                Log.d("DriverTracking", "Conductor actualizado: ${driver.driverName} a ${driver.distanceToPassenger} km")
            }
            lifecycleScope.launch {
                delay(5000) 
                Log.d("DEBUG_PASAJERO", "=== INICIANDO DEBUG DESDE PASAJERO ===")
                val realRepo = RealTripMatchingRepository()
                realRepo.debugDriverLocation("ZT0Y6UH3IkO5HnaGLTUOku2sNd83")
            }

            loadUserData()
        }


        loadUserContributions()

        setContent {
            val userData = remember {
                userPreferences.getUserData()
            }

            YaLlegoApp(
                onLocateClick = {
                    isFollowingLocation = true
                    centerOnUser()
                },
                onRequestTripClick = { selectedDestinations ->
                    if (selectedDestinations.isNotEmpty()) {
                        requestTrip(selectedDestinations)
                    }
                },
                onLogout = { logout() },
                userData = userData,
                onUpdateUserData = { newName, newEmail, newPhone ->
                    userPreferences.saveUserData(newName, newEmail, newPhone)
                    
                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        lifecycleScope.launch {
                            firestoreService.saveUserWithCompleteInfo(
                                userId = currentUser.uid,
                                name = newName,
                                email = newEmail,
                                telefono = newPhone,
                                userType = "usuario"
                            )
                        }
                    }
                },
                onMapViewCreated = { view ->
                    mapView = view
                },
                onMapReady = { map ->
                    maplibreMap = map
                    currentLocation?.let { location ->
                        updateUserLocationMarker(location)
                    }
                    showAllContributedPlaces()
                },
                contributionState = contributionState,
                onContributionStateChange = { newState ->
                    contributionState = newState
                },
                onSaveContributedPlace = { place ->
                    saveContributedPlace(place)
                },
                onDeleteContributedPlace = { placeId ->
                    deleteContributedPlace(placeId)
                },
                onEditContributedPlace = { place ->
                    startEditContributedPlace(place)
                },
                onAddTempPositionMarker = { latLng ->
                    addTempPositionMarker(latLng)
                },
                onClearTempPositionMarker = {
                    clearTempPositionMarker()
                },
                
                isMatching = isMatching,
                availableDrivers = availableDrivers,
                selectedDriver = selectedDriver,
                showDriverSelection = showDriverSelection,
                onStartMatching = { destinations ->
                    startDriverMatching(destinations)
                },
                onSelectDriver = { driver ->
                    selectDriver(driver)
                },
                onCancelMatching = {
                    cancelDriverMatching()
                }

            )
        }
    }

    private fun loadUserContributions() {
        val contributions = userPreferences.getContributions()
        contributionState = contributionState.copy(contributedPlaces = contributions)
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

    override fun onResume() {
        super.onResume()
        restoreFullScreenUI()
        if (!areLocationUpdatesRunning) {
            startLocationUpdates()
        }
        mapView?.onResume()
    }

    private fun restoreFullScreenUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        mapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        userLocationMarker?.remove()
        destinationMarkers.forEach { it.remove() }
        contributionMarkers.forEach { it.remove() }
        tempPositionMarker?.remove()
        mapView?.onDestroy()
        tripMatchingManager.cleanup()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        areLocationUpdatesRunning = true
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
            fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    if (isFollowingLocation) {
                        updateUserLocation(it)
                    }
                    updateUserLocationMarker(it)
                }
            }
        } catch (e: SecurityException) {
            areLocationUpdatesRunning = false
            Toast.makeText(this, "Error: Permisos de ubicación no disponibles", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopLocationUpdates() {
        areLocationUpdatesRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateUserLocation(location: Location) {
        maplibreMap?.let { map ->
            if (isFollowingLocation) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                map.easeCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(currentLatLng)
                            .zoom(15.0)
                            .build()
                    ), 500
                )
            }
        }
    }

    
    private fun createUserLocationIcon(): Bitmap {
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

        paint.color = android.graphics.Color.parseColor("#4285F4")
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, radius - 1.5f, paint)
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, radius * 0.3f, paint)

        return bitmap
    }

    private fun createDestinationIcon(): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }

        val centerX = size / 2f
        val centerY = size / 2f

        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.parseColor("#EA4335")
        canvas.drawCircle(centerX, centerY, 20f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, 19f, paint)

        return bitmap
    }

    private fun createContributedPlaceIcon(): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f

        paint.color = android.graphics.Color.parseColor("#34A853")
        canvas.drawCircle(centerX, centerY, 20f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, 19f, paint)

        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        val starSize = 12f
        canvas.drawRect(centerX - 2f, centerY - starSize, centerX + 2f, centerY + starSize, paint)
        canvas.drawRect(centerX - starSize, centerY - 2f, centerX + starSize, centerY + 2f, paint)

        return bitmap
    }

    private fun createTempPositionIcon(): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f

        paint.color = android.graphics.Color.parseColor("#FBBC05")
        canvas.drawCircle(centerX, centerY, 18f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, 17f, paint)

        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 24f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("?", centerX, centerY + 8f, paint)

        return bitmap
    }

    private fun updateUserLocationMarker(location: Location) {
        val map = maplibreMap ?: return
        val userLatLng = LatLng(location.latitude, location.longitude)

        if (userLocationMarker == null) {
            userLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(userLatLng)
                    .title("Tu ubicación")
                    .snippet("Lat: ${location.latitude}, Lng: ${location.longitude}")
                    .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createUserLocationIcon()))
            )
        } else {
            userLocationMarker?.position = userLatLng
        }
    }

    private fun addDestinationMarker(place: PlaceResult) {
        val map = maplibreMap ?: return
        val destinationLatLng = LatLng(place.lat, place.lon)

        val marker = map.addMarker(
            MarkerOptions()
                .position(destinationLatLng)
                .title(place.name)
                .snippet(place.address)
                .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createDestinationIcon()))
        )

        destinationMarkers.add(marker)
    }

    private fun clearDestinationMarkers() {
        destinationMarkers.forEach { it.remove() }
        destinationMarkers.clear()
    }

    private fun addContributedPlaceMarker(place: UserContributedPlace) {
        val map = maplibreMap ?: return
        val placeLatLng = LatLng(place.lat, place.lon)

        val marker = map.addMarker(
            MarkerOptions()
                .position(placeLatLng)
                .title("${place.name} 🎯")
                .snippet("${place.category} • Contribuido por ${place.userName}")
                .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createContributedPlaceIcon()))
        )

        contributionMarkers.add(marker)
    }

    private fun showAllContributedPlaces() {
        contributionState.contributedPlaces.forEach { place ->
            addContributedPlaceMarker(place)
        }
    }

    private fun removeContributedPlaceMarker(placeId: String) {
        val markerToRemove = contributionMarkers.find {
            it.title?.contains(placeId) == true ||
                    contributionState.contributedPlaces.any { place ->
                        place.id == placeId &&
                                place.lat == it.position.latitude &&
                                place.lon == it.position.longitude
                    }
        }
        markerToRemove?.remove()
        contributionMarkers.remove(markerToRemove)
    }

    private fun addTempPositionMarker(latLng: LatLng) {
        val map = maplibreMap ?: return
        clearTempPositionMarker()

        tempPositionMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Nueva ubicación")
                .snippet("Toca para agregar detalles del lugar")
                .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createTempPositionIcon()))
        )
    }

    private fun clearTempPositionMarker() {
        tempPositionMarker?.remove()
        tempPositionMarker = null
    }

    private fun saveContributedPlace(place: UserContributedPlace) {
        val newPlace = if (place.id.isBlank()) {
            place.copy(
                id = "place_${System.currentTimeMillis()}",
                userId = "user_${System.currentTimeMillis()}",
                userName = userPreferences.getUserData().name.ifEmpty { "Usuario YaLlego" },
                timestamp = System.currentTimeMillis(),
                status = "pending"
            )
        } else {
            place.copy(
                timestamp = System.currentTimeMillis()
            )
        }

        userPreferences.saveContribution(newPlace)

        if (place.id.isBlank()) {
            addContributedPlaceMarker(newPlace)
        } else {
            removeContributedPlaceMarker(place.id)
            addContributedPlaceMarker(newPlace)
        }

        contributionState = contributionState.copy(
            contributedPlaces = userPreferences.getContributions(),
            isAddingPlace = false,
            tempPlace = null,
            selectedPosition = null,
            editingPlace = null
        )

        clearTempPositionMarker()

        val message = if (place.id.isBlank()) "✅ Lugar agregado: ${place.name}" else "✏️ Lugar actualizado: ${place.name}"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.d("Contribution", "Lugar ${if (place.id.isBlank()) "agregado" else "actualizado"}: ${place.name} (${place.lat}, ${place.lon})")
    }

    private fun deleteContributedPlace(placeId: String) {
        userPreferences.deleteContribution(placeId)
        removeContributedPlaceMarker(placeId)

        contributionState = contributionState.copy(
            contributedPlaces = userPreferences.getContributions()
        )

        Toast.makeText(this, "🗑️ Lugar eliminado", Toast.LENGTH_LONG).show()
        Log.d("Contribution", "Lugar eliminado: $placeId")
    }

    private fun startEditContributedPlace(place: UserContributedPlace) {
        contributionState = contributionState.copy(
            editingPlace = place,
            isAddingPlace = true
        )

        val placeLatLng = LatLng(place.lat, place.lon)
        maplibreMap?.easeCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(placeLatLng)
                    .zoom(16.0)
                    .build()
            ), 500
        )

        Toast.makeText(this, "✏️ Editando: ${place.name}", Toast.LENGTH_SHORT).show()
    }

    private fun centerOnUser() {
        currentLocation?.let {
            isFollowingLocation = true
            updateUserLocation(it)
        } ?: run {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    isFollowingLocation = true
                    updateUserLocation(it)
                }
            }
        }
    }

    

    
    private fun startDriverMatching(selectedDestinations: List<PlaceResult>) {
        if (selectedDestinations.isEmpty()) {
            Toast.makeText(this, "Selecciona un destino primero", Toast.LENGTH_SHORT).show()
            return
        }

        val startLocation = currentLocation ?: run {
            Toast.makeText(this, "Obteniendo tu ubicación...", Toast.LENGTH_SHORT).show()
            return
        }

        isMatching = true
        showDriverSelection = false
        availableDrivers = emptyList()
        selectedDriver = null

        
        val tripRequest = com.amurayada.yallego.models.TripRequest(
            passengerId = userPreferences.getUserData().name.ifEmpty { "user_${System.currentTimeMillis()}" },
            passengerName = userPreferences.getUserData().name.ifEmpty { "Usuario YaLlego" },
            startLocation = LatLng(startLocation.latitude, startLocation.longitude),
            endLocation = LatLng(selectedDestinations.first().lat, selectedDestinations.first().lon),
            startAddress = "Tu ubicación actual",
            endAddress = selectedDestinations.first().address,
            distance = calculateDistance(startLocation, selectedDestinations.first()),
            estimatedPrice = calculateTripAmount(startLocation, selectedDestinations.first())
        )

        
        tripMatchingManager.findDriversForTrip(tripRequest)

        Toast.makeText(this, "🔍 Buscando conductores cercanos...", Toast.LENGTH_SHORT).show()
        Log.d("Matching", "Iniciando búsqueda de conductores para: ${selectedDestinations.first().name}")
    }

    
    private fun selectDriver(driver: com.amurayada.yallego.models.DriverMatch) {
        selectedDriver = driver
        showDriverSelection = false

        
        val currentDestinations = listOf(PlaceResult(
            name = "Destino seleccionado",
            lat = currentLocation?.latitude ?: 0.0,
            lon = currentLocation?.longitude ?: 0.0
        ))

        val tripRequest = com.amurayada.yallego.models.TripRequest(
            passengerId = userPreferences.getUserData().name,
            passengerName = userPreferences.getUserData().name,
            startLocation = LatLng(currentLocation?.latitude ?: 0.0, currentLocation?.longitude ?: 0.0),
            endLocation = LatLng(currentDestinations.first().lat, currentDestinations.first().lon)
        )

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tripMatchingManager.notifyDriver(driver.driverId, tripRequest)
        }

        notificationService.showDriverAssignedNotification(driver)
        Toast.makeText(this, "✅ ${driver.driverName} asignado", Toast.LENGTH_LONG).show()
    }

    
    private fun cancelDriverMatching() {
        isMatching = false
        showDriverSelection = false
        availableDrivers = emptyList()
        selectedDriver = null
        tripMatchingManager.cancelMatching()
        Toast.makeText(this, "❌ Búsqueda cancelada", Toast.LENGTH_SHORT).show()
    }

    private fun requestTrip(selectedDestinations: List<PlaceResult>) {
        val start = currentLocation ?: return

        if (selectedDestinations.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un destino", Toast.LENGTH_SHORT).show()
            return
        }

        val destination = selectedDestinations.first()
        val tripAmount = calculateTripAmount(start, destination)
        userPreferences.updateTripStats(tripAmount)

        
        startDriverMatching(selectedDestinations)

        Log.d("Trip", "Solicitando viaje a: ${destination.name}, Monto: $tripAmount")
    }

    private fun calculateTripAmount(start: Location, destination: PlaceResult): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            destination.lat, destination.lon,
            results
        )
        val distanceKm = results[0].toDouble() / 1000.0
        return distanceKm * 2500.0
    }

    private fun calculateDistance(start: Location, destination: PlaceResult): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            destination.lat, destination.lon,
            results
        )
        return results[0].toDouble() / 1000.0
    }

    private fun logout() {
        val appPreferences = AppPreferences(this)
        appPreferences.clearUserData() 

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    
    private fun loadUserData() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        lifecycleScope.launch {
            try {
                val userData = firestoreService.getUserData(user.uid)
                userData?.let {
                    userPreferences.saveUserData(
                        name = it.name,
                        email = it.email,
                        phone = it.phone
                    )
                    Log.d("Home", "✅ Datos de usuario cargados de Firestore: ${it.name}")
                }
            } catch (e: Exception) {
                Log.e("Home", "❌ Error cargando datos usuario: ${e.message}")
            }
        }
    }

    @Composable
    fun YaLlegoApp(
        onLocateClick: () -> Unit,
        onRequestTripClick: (List<PlaceResult>) -> Unit,
        onLogout: () -> Unit,
        userData: UserProfile,
        onUpdateUserData: (String, String, String) -> Unit,
        onMapViewCreated: (MapView) -> Unit,
        onMapReady: (MapLibreMap) -> Unit,
        contributionState: ContributionState,
        onContributionStateChange: (ContributionState) -> Unit,
        onSaveContributedPlace: (UserContributedPlace) -> Unit,
        onDeleteContributedPlace: (String) -> Unit,
        onEditContributedPlace: (UserContributedPlace) -> Unit,
        onAddTempPositionMarker: (LatLng) -> Unit,
        onClearTempPositionMarker: () -> Unit,
        
        isMatching: Boolean,
        availableDrivers: List<com.amurayada.yallego.models.DriverMatch>,
        selectedDriver: com.amurayada.yallego.models.DriverMatch?,
        showDriverSelection: Boolean,
        onStartMatching: (List<PlaceResult>) -> Unit,
        onSelectDriver: (com.amurayada.yallego.models.DriverMatch) -> Unit,
        onCancelMatching: () -> Unit
    ) {
        val navController = rememberNavController()

        Scaffold(
            bottomBar = {
                UserBottomNavigationBar(navController = navController)
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "user_map",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                
                composable("user_map") {
                    MapScreen(
                        onLocateClick = onLocateClick,
                        onRequestTripClick = { destinations ->
                            onStartMatching(destinations) 
                        },
                        currentLocation = currentLocation,
                        isFollowingLocation = isFollowingLocation,
                        onFollowingLocationChange = { isFollowingLocation = it },
                        onMapViewCreated = onMapViewCreated,
                        onMapReady = onMapReady,
                        onAddDestinationMarker = { place ->
                            addDestinationMarker(place)
                        },
                        onClearDestinationMarkers = {
                            clearDestinationMarkers()
                        },
                        contributionState = contributionState,
                        onContributionStateChange = onContributionStateChange,
                        onSaveContributedPlace = onSaveContributedPlace,
                        onDeleteContributedPlace = onDeleteContributedPlace,
                        onEditContributedPlace = onEditContributedPlace,
                        onAddTempPositionMarker = onAddTempPositionMarker,
                        onClearTempPositionMarker = onClearTempPositionMarker,
                        userData = userData,
                        
                        isMatching = isMatching,
                        availableDrivers = availableDrivers,
                        selectedDriver = selectedDriver,
                        showDriverSelection = showDriverSelection,
                        onSelectDriver = onSelectDriver,
                        onCancelMatching = onCancelMatching
                    )
                }

                
                composable("user_history") {
                    TripHistoryScreen(userData = userData)
                }

                
                composable("profile") {
                    ProfileScreen(
                        onLogout = onLogout,
                        userData = userData,
                        onUpdateUserData = onUpdateUserData
                    )
                }
            }
        }
    }

    
    @Composable
    fun UserBottomNavigationBar(navController: NavController) {
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route

        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
                label = { Text("Mapa") },
                selected = currentDestination == "user_map",
                onClick = { navController.navigate("user_map") }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                label = { Text("Historial") },
                selected = currentDestination == "user_history",
                onClick = { navController.navigate("user_history") }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
                label = { Text("Perfil") },
                selected = currentDestination == "profile",
                onClick = { navController.navigate("profile") }
            )
        }
    }

    
    @Composable
    fun TripHistoryScreen(userData: UserProfile) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Historial de Viajes",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${userData.totalTrips}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Total Viajes", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${userData.monthlyTrips}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Este Mes", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "★ ${"%.1f".format(userData.rating)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Calificación", fontSize = 12.sp)
                    }
                }
            }

            if (userData.totalTrips > 0) {
                Text("Lista de viajes aparecerá aquí")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Sin historial",
                            modifier = Modifier.size(64.dp),
                            tint = ComposeColor.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No hay viajes registrados",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ComposeColor.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Realiza tu primer viaje para ver el historial aquí",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ComposeColor.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    
    @Composable
    fun ProfileScreen(
        onLogout: () -> Unit,
        userData: UserProfile,
        onUpdateUserData: (String, String, String) -> Unit
    ) {
        var isEditing by remember { mutableStateOf(false) }
        var editedName by remember { mutableStateOf("") }
        var editedEmail by remember { mutableStateOf("") }
        var editedPhone by remember { mutableStateOf("") }

        if (editedName.isEmpty()) {
            editedName = userData.name
            editedEmail = userData.email
            editedPhone = userData.phone
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Usuario",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = editedName.ifEmpty { "Usuario" },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = editedEmail.ifEmpty { "email@ejemplo.com" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "👤 Usuario",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Editar Perfil")
            }

            
            UserProfileCard(userData = userData)

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }

            
            if (isEditing) {
                AlertDialog(
                    onDismissRequest = { isEditing = false },
                    title = { Text("Editar Perfil") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                label = { Text("Nombre") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editedEmail,
                                onValueChange = { editedEmail = it },
                                label = { Text("Email") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editedPhone,
                                onValueChange = { editedPhone = it },
                                label = { Text("Teléfono") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            onUpdateUserData(editedName, editedEmail, editedPhone)
                            isEditing = false
                        }) {
                            Text("Guardar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }

    
    @Composable
    fun UserProfileCard(userData: UserProfile) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Información del Usuario",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ProfileItem("Teléfono", userData.phone.ifEmpty { "No registrado" })
                ProfileItem("Miembro desde", userData.memberSince.ifEmpty { "No especificado" })
                ProfileItem("Viajes realizados", "${userData.totalTrips} viajes")
                ProfileItem("Total gastado", "$${"%.0f".format(userData.totalSpent)}")
                ProfileItem("Calificación", "★ ${"%.1f".format(userData.rating)}")
            }
        }
    }

    
    @Composable
    fun ProfileItem(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }

    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MapScreen(
        onLocateClick: () -> Unit,
        onRequestTripClick: (List<PlaceResult>) -> Unit,
        currentLocation: Location?,
        isFollowingLocation: Boolean,
        onFollowingLocationChange: (Boolean) -> Unit,
        onMapViewCreated: (MapView) -> Unit,
        onMapReady: (MapLibreMap) -> Unit,
        onAddDestinationMarker: (PlaceResult) -> Unit,
        onClearDestinationMarkers: () -> Unit,
        contributionState: ContributionState,
        onContributionStateChange: (ContributionState) -> Unit,
        onSaveContributedPlace: (UserContributedPlace) -> Unit,
        onDeleteContributedPlace: (String) -> Unit,
        onEditContributedPlace: (UserContributedPlace) -> Unit,
        onAddTempPositionMarker: (LatLng) -> Unit,
        onClearTempPositionMarker: () -> Unit,
        userData: UserProfile,
        
        isMatching: Boolean,
        availableDrivers: List<com.amurayada.yallego.models.DriverMatch>,
        selectedDriver: com.amurayada.yallego.models.DriverMatch?,
        showDriverSelection: Boolean,
        onSelectDriver: (com.amurayada.yallego.models.DriverMatch) -> Unit,
        onCancelMatching: () -> Unit
    ) {
        val context = LocalContext.current
        var routeState by remember { mutableStateOf(RouteState()) }
        val scope = rememberCoroutineScope()

        
        val availableStyles = listOf(
            "Estándar" to "https://tiles.openfreemap.org/styles/liberty",
            "Satélite" to "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
            "Topográfico" to "https://tiles.openfreemap.org/styles/humanitarian",
            "Oscuro" to "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
        )
        var currentMapStyle by remember { mutableStateOf(availableStyles[0].second) }
        var showSearchModal by remember { mutableStateOf(false) }
        var searchText by rememberSaveable { mutableStateOf("") }
        var showMapStyleMenu by remember { mutableStateOf(false) }
        var showAddPlaceModal by remember { mutableStateOf(false) }
        var showManagePlacesModal by remember { mutableStateOf(false) }

        
        val mapViewInstance = remember {
            MapView(context).apply {
                onCreate(Bundle())
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri(currentMapStyle)) {
                        
                        val defaultLatLng = LatLng(4.5709, -74.2973)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(defaultLatLng)
                            .zoom(6.0)
                            .build()

                        
                        map.addOnCameraIdleListener {
                            onFollowingLocationChange(false)
                        }

                        
                        map.addOnMapLongClickListener { point ->
                            scope.launch {
                                onAddTempPositionMarker(point)
                                onContributionStateChange(
                                    contributionState.copy(
                                        isAddingPlace = true,
                                        selectedPosition = point,
                                        editingPlace = null
                                    )
                                )
                                showAddPlaceModal = true
                            }
                            true
                        }
                    }
                    onMapReady(map)
                }
                onMapViewCreated(this)
            }
        }

        
        LaunchedEffect(currentLocation, isFollowingLocation) {
            currentLocation?.let { location ->
                val userLatLng = LatLng(location.latitude, location.longitude)

                if (isFollowingLocation) {
                    maplibreMap?.easeCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(userLatLng)
                                .zoom(15.0)
                                .build()
                        ), 500
                    )
                }
            }
        }

        
        LaunchedEffect(currentMapStyle) {
            maplibreMap?.setStyle(Style.Builder().fromUri(currentMapStyle))
        }

        
        LaunchedEffect(contributionState.editingPlace) {
            if (contributionState.editingPlace != null) {
                showAddPlaceModal = true
            }
        }

        fun centerOnCurrentLocation() {
            currentLocation?.let { location ->
                val point = LatLng(location.latitude, location.longitude)

                maplibreMap?.easeCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(point)
                            .zoom(15.0)
                            .build()
                    ), 500
                )
                onFollowingLocationChange(true)

                Toast.makeText(context, "Centrado en tu ubicación actual", Toast.LENGTH_SHORT).show()

            } ?: run {
                onLocateClick()
                Toast.makeText(context, "Obteniendo tu ubicación...", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(searchText) {
            if (searchText.length >= 2) {
                routeState = routeState.copy(isSearching = true)
                val results = searchPlacesRealTime(searchText, context)
                routeState = routeState.copy(
                    currentSearchResults = results,
                    isSearching = false
                )
            } else {
                routeState = routeState.copy(currentSearchResults = emptyList())
            }
        }

        
        fun changeMapStyle(newStyle: String) {
            currentMapStyle = newStyle
            showMapStyleMenu = false
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapViewInstance },
                modifier = Modifier.fillMaxSize()
            )

            
            if (routeState.selectedDestinations.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .fillMaxWidth(0.9f),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Destino seleccionado:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            TextButton(onClick = {
                                routeState = routeState.copy(selectedDestinations = emptyList())
                                onClearDestinationMarkers()
                            }) {
                                Text("Limpiar", fontSize = 12.sp)
                            }
                        }
                        Text(
                            routeState.selectedDestinations.first().name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentLocation != null)
                        ComposeColor(0xFFE3F2FD)
                    else
                        ComposeColor(0xFFFFEBEE)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (currentLocation != null) Icons.Default.LocationOn else Icons.Default.LocationOff,
                        contentDescription = "Ubicación",
                        tint = if (currentLocation != null) ComposeColor(0xFF1976D2) else ComposeColor(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (currentLocation != null) "Ubicación activa" else "Buscando ubicación",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (currentLocation != null) ComposeColor(0xFF1976D2) else ComposeColor(0xFFD32F2F)
                        )
                        if (currentLocation != null) {
                            Text(
                                "${"%.4f".format(currentLocation.latitude)}, ${"%.4f".format(currentLocation.longitude)}",
                                fontSize = 10.sp,
                                color = ComposeColor.Gray
                            )
                        }
                    }
                }
            }

            
            if (isMatching) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .fillMaxWidth(0.9f),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFFFF9C4))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ComposeColor(0xFFF57C00),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Buscando conductores cercanos...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = ComposeColor(0xFFF57C00)
                            )
                            Text(
                                "Por favor espera",
                                fontSize = 12.sp,
                                color = ComposeColor(0xFFF57C00)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancelMatching) {
                            Text("Cancelar", color = ComposeColor(0xFFF57C00))
                        }
                    }
                }
            }

            
            selectedDriver?.let { driver ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .fillMaxWidth(0.9f),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFE8F5E8))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🚗 Conductor asignado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ComposeColor(0xFF388E3C)
                            )
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Asignado",
                                tint = ComposeColor(0xFF388E3C)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            driver.driverName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "VEHÍCULO",
                                    fontSize = 10.sp,
                                    color = ComposeColor.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${driver.vehicleInfo.color} ${driver.vehicleInfo.model}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    driver.vehicleInfo.plate,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ComposeColor.Black,
                                    modifier = Modifier.background(ComposeColor(0xFFFFF9C4)).padding(horizontal = 4.dp)
                                )
                            }
                        }

                        Text(
                            "⭐ ${"%.1f".format(driver.rating)} • ${driver.totalTrips} viajes • Llega en ${driver.estimatedArrivalTime} min",
                            fontSize = 12.sp,
                            color = ComposeColor.Gray
                        )

                        Spacer(Modifier.height(8.dp))

                        val context = LocalContext.current
                        
                        Button(
                            onClick = {
                                Log.d("ShareTrip", "=== COMPARTIR VIAJE (PASAJERO) ===")
                                Log.d("ShareTrip", "driver: $driver")
                                Log.d("ShareTrip", "driverName: ${driver.driverName}")
                                Log.d("ShareTrip", "vehicleColor: ${driver.vehicleInfo.color}")
                                Log.d("ShareTrip", "vehicleModel: ${driver.vehicleInfo.model}")
                                Log.d("ShareTrip", "vehiclePlate: ${driver.vehicleInfo.plate}")
                                
                                val shareText = "🚖 Voy en un viaje con YaLlego\n" +
                                    "👤 Conductor: ${driver.driverName}\n" +
                                    "🚗 Vehículo: ${driver.vehicleInfo.color} ${driver.vehicleInfo.model}\n" +
                                    "🔢 Placa: ${driver.vehicleInfo.plate}\n" +
                                    "🛡️ Viaje seguro monitoreado."
                                
                                Log.d("ShareTrip", "Texto completo: $shareText")
                                
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Compartir Viaje - YaLlego")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir datos del viaje..."))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir Datos del Viaje")
                        }
                    }
                }
            }

            
            if (contributionState.isAddingPlace) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isMatching || selectedDriver != null) 140.dp else 80.dp)
                        .fillMaxWidth(0.9f),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFFFF9C4))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (contributionState.editingPlace != null) Icons.Default.Edit else Icons.Default.AddLocation,
                            contentDescription = "Agregar/Editar lugar",
                            tint = ComposeColor(0xFFF57C00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (contributionState.editingPlace != null)
                                "Modo: Editando lugar\nMantén presionado en el mapa para cambiar ubicación"
                            else
                                "Modo: Agregar nuevo lugar\nMantén presionado en el mapa para seleccionar ubicación",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ComposeColor(0xFFF57C00)
                        )
                    }
                }
            }

            
            if (isFollowingLocation) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFE8F5E8))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Siguiendo ubicación",
                            tint = ComposeColor(0xFF388E3C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Siguiendo ubicación",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ComposeColor(0xFF388E3C)
                        )
                    }
                }
            }

            
            if (routeState.selectedDestinations.isNotEmpty() && !isMatching && selectedDriver == null) {
                Button(
                    onClick = { onRequestTripClick(routeState.selectedDestinations) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF4285F4)
                    )
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SOLICITAR VIAJE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                
                FloatingActionButton(
                    onClick = {
                        showManagePlacesModal = true
                    },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = "Gestionar mis lugares")
                }

                
                FloatingActionButton(
                    onClick = {
                        onContributionStateChange(
                            contributionState.copy(
                                isAddingPlace = !contributionState.isAddingPlace,
                                editingPlace = null
                            )
                        )
                        if (!contributionState.isAddingPlace) {
                            onClearTempPositionMarker()
                        }
                    },
                    containerColor = if (contributionState.isAddingPlace) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        if (contributionState.isAddingPlace) Icons.Default.Close else Icons.Default.AddLocation,
                        contentDescription = "Agregar nuevo lugar",
                        tint = if (contributionState.isAddingPlace) ComposeColor.White else ComposeColor.Black
                    )
                }

                FloatingActionButton(
                    onClick = { showSearchModal = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar destinos")
                }

                FloatingActionButton(
                    onClick = { showMapStyleMenu = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Cambiar estilo del mapa")
                }

                
                FloatingActionButton(
                    onClick = {
                        centerOnCurrentLocation()
                    },
                    containerColor = if (isFollowingLocation) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Centrar en mi ubicación",
                        tint = if (isFollowingLocation) ComposeColor.White else ComposeColor.Black
                    )
                }
            }

            
            if (showDriverSelection && availableDrivers.isNotEmpty()) {
                DriverSelectionModal(
                    drivers = availableDrivers,
                    onSelectDriver = onSelectDriver,
                    onCancel = onCancelMatching
                )
            }

            
            if (showMapStyleMenu) {
                AlertDialog(
                    onDismissRequest = { showMapStyleMenu = false },
                    title = { Text("Estilo del Mapa") },
                    text = {
                        Column {
                            availableStyles.forEach { (styleName, styleUri) ->
                                Card(
                                    onClick = {
                                        changeMapStyle(styleUri)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (currentMapStyle == styleUri)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            when (styleName) {
                                                "Satélite" -> Icons.Default.SatelliteAlt
                                                "Topográfico" -> Icons.Default.Terrain
                                                "Oscuro" -> Icons.Default.DarkMode
                                                else -> Icons.Default.Map
                                            },
                                            contentDescription = styleName,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = styleName,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMapStyleMenu = false }) {
                            Text("Cerrar")
                        }
                    }
                )
            }

            
            if (showSearchModal) {
                SearchModal(
                    searchText = searchText,
                    onSearchTextChange = { newText ->
                        searchText = newText
                    },
                    searchResults = routeState.currentSearchResults,
                    isLoading = routeState.isSearching,
                    onResultClick = { result ->
                        routeState = routeState.copy(
                            selectedDestinations = listOf(result)
                        )
                        onAddDestinationMarker(result)

                        
                        val destinationPoint = LatLng(result.lat, result.lon)
                        maplibreMap?.easeCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(destinationPoint)
                                    .zoom(14.0)
                                    .build()
                            ), 1000
                        )
                        onFollowingLocationChange(false)

                        showSearchModal = false
                        searchText = ""

                        Toast.makeText(
                            context,
                            "Destino agregado: ${result.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDismiss = {
                        showSearchModal = false
                        searchText = ""
                        routeState = routeState.copy(currentSearchResults = emptyList())
                    }
                )
            }

            
            if (showAddPlaceModal) {
                AddPlaceModal(
                    placeToEdit = contributionState.editingPlace,
                    position = contributionState.selectedPosition,
                    onSavePlace = { place ->
                        onSaveContributedPlace(place)
                        showAddPlaceModal = false
                    },
                    onDismiss = {
                        showAddPlaceModal = false
                        onClearTempPositionMarker()
                        onContributionStateChange(
                            contributionState.copy(
                                isAddingPlace = false,
                                selectedPosition = null,
                                editingPlace = null
                            )
                        )
                    },
                    userData = userData
                )
            }

            
            if (showManagePlacesModal) {
                ManagePlacesModal(
                    contributedPlaces = contributionState.contributedPlaces,
                    onEditPlace = { place ->
                        onEditContributedPlace(place)
                        showManagePlacesModal = false
                    },
                    onDeletePlace = { placeId ->
                        onDeleteContributedPlace(placeId)
                    },
                    onDismiss = {
                        showManagePlacesModal = false
                    }
                )
            }
        }
    }

    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DriverSelectionModal(
        drivers: List<com.amurayada.yallego.models.DriverMatch>,
        onSelectDriver: (com.amurayada.yallego.models.DriverMatch) -> Unit,
        onCancel: () -> Unit
    ) {
        ModalBottomSheet(
            onDismissRequest = onCancel,
            sheetState = rememberModalBottomSheetState(),
            containerColor = ComposeColor.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Conductores Disponibles",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "Selecciona un conductor para tu viaje:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ComposeColor.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(drivers) { driver ->
                        DriverCard(
                            driver = driver,
                            onSelect = { onSelectDriver(driver) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFFF5F5F5),
                        contentColor = ComposeColor.Black
                    )
                ) {
                    Text("Cancelar Búsqueda")
                }
            }
        }
    }

    

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DriverCard(
        driver: com.amurayada.yallego.models.DriverMatch,
        onSelect: () -> Unit
    ) {
        Card(
            onClick = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                ComposeColor(0xFF4285F4),
                                shape = RoundedCornerShape(25.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            driver.driverName.take(2).uppercase(),
                            color = ComposeColor.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            driver.driverName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${driver.vehicleInfo.color} ${driver.vehicleInfo.model} • ${driver.vehicleInfo.plate}",
                            fontSize = 14.sp,
                            color = ComposeColor.Gray
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "⭐ ${"%.1f".format(driver.rating)}",
                            fontWeight = FontWeight.Bold,
                            color = ComposeColor(0xFFF57C00)
                        )
                        Text(
                            "${driver.totalTrips} viajes",
                            fontSize = 12.sp,
                            color = ComposeColor.Gray
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(
                        icon = Icons.Default.Schedule,
                        text = "${driver.estimatedArrivalTime} min"
                    )
                    InfoChip(
                        icon = Icons.Default.DirectionsCar,
                        text = "${"%.1f".format(driver.distanceToPassenger)} km"
                    )
                    InfoChip(
                        icon = Icons.Default.Star,
                        text = driver.vehicleInfo.type
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF34A853)
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Seleccionar este conductor")
                }
            }
        }
    }

    
    @Composable
    fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ComposeColor.Gray
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                fontSize = 12.sp,
                color = ComposeColor.Gray
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchModal(
        searchText: String,
        onSearchTextChange: (String) -> Unit,
        searchResults: List<PlaceResult>,
        isLoading: Boolean,
        onResultClick: (PlaceResult) -> Unit,
        onDismiss: () -> Unit
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(),
            containerColor = ComposeColor.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                TextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    placeholder = { Text("Buscar destino en Colombia...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(35.dp)),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ComposeColor(0xFFF8F9FA),
                        unfocusedContainerColor = ComposeColor(0xFFF8F9FA),
                        focusedIndicatorColor = ComposeColor.Transparent,
                        unfocusedIndicatorColor = ComposeColor.Transparent
                    )
                )

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (searchResults.isNotEmpty()) {
                    Text(
                        "Resultados encontrados:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(searchResults) { result ->
                            Card(
                                onClick = { onResultClick(result) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Ubicación",
                                        tint = ComposeColor(0xFF4285F4),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.name,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = result.address,
                                            color = ComposeColor.Gray,
                                            fontSize = 12.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (searchText.length >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No se encontraron resultados para \"$searchText\"",
                            color = ComposeColor.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column {
                        Text(
                            "Sugerencias de búsqueda:",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val suggestions = listOf(
                            "Restaurante",
                            "Hotel",
                            "Farmacia",
                            "Supermercado",
                            "Banco",
                            "Parque",
                            "Escuela",
                            "Estación de servicio"
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(suggestions) { suggestion ->
                                TextButton(
                                    onClick = { onSearchTextChange(suggestion) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = suggestion)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AddPlaceModal(
        placeToEdit: UserContributedPlace?,
        position: LatLng?,
        onSavePlace: (UserContributedPlace) -> Unit,
        onDismiss: () -> Unit,
        userData: UserProfile
    ) {
        val context = LocalContext.current

        var placeName by remember { mutableStateOf(placeToEdit?.name ?: "") }
        var placeCategory by remember { mutableStateOf(placeToEdit?.category ?: "") }
        var placeDescription by remember { mutableStateOf(placeToEdit?.description ?: "") }
        var placeAddress by remember { mutableStateOf(placeToEdit?.address ?: "") }

        
        val currentPosition = placeToEdit?.let { LatLng(it.lat, it.lon) } ?: position

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (placeToEdit != null) Icons.Default.Edit else Icons.Default.AddLocation,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (placeToEdit != null) "Editar Lugar" else "Agregar Nuevo Lugar")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFE3F2FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = ComposeColor(0xFF1976D2))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    if (placeToEdit != null) "Ubicación actual del lugar" else "Ubicación seleccionada",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    currentPosition?.let { "Lat: ${"%.6f".format(it.latitude)}, Lng: ${"%.6f".format(it.longitude)}" } ?: "No disponible",
                                    fontSize = 12.sp,
                                    color = ComposeColor.Gray
                                )
                                if (placeToEdit == null) {
                                    Text(
                                        "Mantén presionado en el mapa para cambiar la ubicación",
                                        fontSize = 10.sp,
                                        color = ComposeColor(0xFF1976D2),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Nombre del lugar *") },
                        placeholder = { Text("Ej: Mi Restaurante Favorito") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = placeName.isBlank()
                    )

                    
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = placeCategory,
                            onValueChange = { },
                            label = { Text("Categoría *") },
                            placeholder = { Text("Selecciona una categoría") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            isError = placeCategory.isBlank()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            placeCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        placeCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = placeAddress,
                        onValueChange = { placeAddress = it },
                        label = { Text("Dirección") },
                        placeholder = { Text("Ej: Carrera 10 #20-30") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = placeDescription,
                        onValueChange = { placeDescription = it },
                        label = { Text("Descripción adicional") },
                        placeholder = { Text("Ej: Restaurante con comida tradicional abierto hasta las 10pm") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 3
                    )

                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFF3E5F5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = ComposeColor(0xFF7B1FA2))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Contribuyente", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    userData.name.ifEmpty { "Usuario YaLlego" },
                                    fontSize = 12.sp,
                                    color = ComposeColor.Gray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (placeName.isNotBlank() && placeCategory.isNotBlank() && currentPosition != null) {
                            val place = UserContributedPlace(
                                id = placeToEdit?.id ?: "",
                                name = placeName,
                                lat = currentPosition.latitude,
                                lon = currentPosition.longitude,
                                address = placeAddress,
                                type = "user_contributed",
                                category = placeCategory,
                                description = placeDescription,
                                userName = userData.name.ifEmpty { "Usuario YaLlego" }
                            )
                            onSavePlace(place)
                        } else {
                            Toast.makeText(context, "Completa los campos obligatorios (*)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = placeName.isNotBlank() && placeCategory.isNotBlank() && currentPosition != null
                ) {
                    Text(if (placeToEdit != null) "Actualizar Lugar" else "Agregar Lugar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }

    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ManagePlacesModal(
        contributedPlaces: List<UserContributedPlace>,
        onEditPlace: (UserContributedPlace) -> Unit,
        onDeletePlace: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var placeToDelete by remember { mutableStateOf<UserContributedPlace?>(null) }

        
        if (showDeleteConfirmation && placeToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmation = false
                    placeToDelete = null
                },
                title = { Text("Eliminar Lugar") },
                text = { Text("¿Estás seguro de que quieres eliminar \"${placeToDelete?.name}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            placeToDelete?.id?.let { onDeletePlace(it) }
                            showDeleteConfirmation = false
                            placeToDelete = null
                        }
                    ) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            placeToDelete = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(),
            containerColor = ComposeColor.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mis Lugares Contribuidos",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${contributedPlaces.size} lugares",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ComposeColor.Gray
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (contributedPlaces.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddLocation,
                                contentDescription = "Sin lugares",
                                modifier = Modifier.size(64.dp),
                                tint = ComposeColor.Gray
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No has contribuido con lugares aún",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ComposeColor.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Usa el botón ➕ para agregar el primero",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ComposeColor.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                    ) {
                        items(contributedPlaces) { place ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = place.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = place.category,
                                                color = ComposeColor(0xFF34A853),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (place.address.isNotBlank()) {
                                                Text(
                                                    text = place.address,
                                                    color = ComposeColor.Gray,
                                                    fontSize = 12.sp,
                                                    maxLines = 2
                                                )
                                            }
                                            if (place.description.isNotBlank()) {
                                                Text(
                                                    text = place.description,
                                                    color = ComposeColor.Gray,
                                                    fontSize = 12.sp,
                                                    maxLines = 2
                                                )
                                            }
                                            Text(
                                                text = "Agregado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(place.timestamp))}",
                                                color = ComposeColor.Gray,
                                                fontSize = 10.sp
                                            )
                                        }

                                        
                                        Row {
                                            IconButton(
                                                onClick = { onEditPlace(place) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Editar",
                                                    tint = ComposeColor(0xFF1976D2),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    placeToDelete = place
                                                    showDeleteConfirmation = true
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Eliminar",
                                                    tint = ComposeColor(0xFFD32F2F),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}