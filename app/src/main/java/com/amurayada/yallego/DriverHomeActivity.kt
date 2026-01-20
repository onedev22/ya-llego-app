package com.amurayada.yallego
import com.google.firebase.messaging.FirebaseMessaging  
import kotlinx.coroutines.tasks.await
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
import android.media.RingtoneManager
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.amurayada.yallego.data.RideRequest
import com.amurayada.yallego.data.Expense
import com.amurayada.yallego.service.*
import com.amurayada.yallego.matching.TripMatchingManager
import com.amurayada.yallego.models.TripRequest
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.text.SimpleDateFormat
import java.util.*


data class DriverProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val cedula: String = "",
    val tipoVehiculo: String = "",
    val placa: String = "",
    val modelo: String = "",
    val colorVehiculo: String = "",
    val estado: String = "pendiente",
    val disponible: Boolean = false,
    val isOnline: Boolean = false,
    val isAvailable: Boolean = false,
    val totalTrips: Int = 0,
    val rating: Double = 0.0,
    val monthlyEarnings: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val memberSince: String = ""
)

data class Trip(
    val id: String = "",
    val passengerName: String = "",
    val startAddress: String = "",
    val endAddress: String = "",
    val distance: Double = 0.0,
    val earnings: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "completed"
)

class DriverPreferences(private val context: Context) {
    private val sharedPref = context.getSharedPreferences("driver_data", Context.MODE_PRIVATE)
    private val tripsPref = context.getSharedPreferences("driver_trips", Context.MODE_PRIVATE)
    private val expensesPref = context.getSharedPreferences("driver_expenses", Context.MODE_PRIVATE)

    fun saveDriverData(
        name: String,
        email: String,
        phone: String,
        cedula: String,
        tipoVehiculo: String,
        placa: String,
        modelo: String,
        colorVehiculo: String,
        estado: String = "pendiente"
    ) {
        with(sharedPref.edit()) {
            putString("driver_name", name)
            putString("driver_email", email)
            putString("driver_phone", phone)
            putString("driver_cedula", cedula)
            putString("driver_tipo_vehiculo", tipoVehiculo)
            putString("driver_placa", placa)
            putString("driver_modelo", modelo)
            putString("driver_color", colorVehiculo)
            putString("driver_status", estado)
            putString("member_since", SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()))
            apply()
        }
    }

    fun setDriverActivationStatus(active: Boolean) {
        with(sharedPref.edit()) {
            putBoolean("driver_activation_status", active)
            putString("driver_status", if (active) "activo" else "inactivo")
            putBoolean("driver_available", active)
            putBoolean("driver_online", active)
            apply()
        }
    }

    fun getDriverActivationStatus(): Boolean {
        return sharedPref.getBoolean("driver_activation_status", false)
    }

    fun getDriverProfile(): DriverProfile {
        val isActive = getDriverActivationStatus()

        return DriverProfile(
            name = sharedPref.getString("driver_name", "") ?: "",
            email = sharedPref.getString("driver_email", "") ?: "",
            phone = sharedPref.getString("driver_phone", "") ?: "",
            cedula = sharedPref.getString("driver_cedula", "") ?: "",
            tipoVehiculo = sharedPref.getString("driver_tipo_vehiculo", "") ?: "",
            placa = sharedPref.getString("driver_placa", "") ?: "",
            modelo = sharedPref.getString("driver_modelo", "") ?: "",
            colorVehiculo = sharedPref.getString("driver_color", "") ?: "",
            estado = if (isActive) "activo" else "inactivo",
            disponible = sharedPref.getBoolean("driver_available", false) && isActive,
            isOnline = sharedPref.getBoolean("driver_online", false) && isActive,
            isAvailable = sharedPref.getBoolean("driver_available", false) && isActive,
            totalTrips = sharedPref.getInt("driver_total_trips", 0),
            rating = sharedPref.getFloat("driver_rating", 4.5f).toDouble(),
            monthlyEarnings = sharedPref.getFloat("driver_monthly_earnings", 0f).toDouble(),
            totalEarnings = sharedPref.getFloat("driver_total_earnings", 0f).toDouble(),
            monthlyExpenses = sharedPref.getFloat("driver_monthly_expenses", 0f).toDouble(),
            totalExpenses = sharedPref.getFloat("driver_total_expenses", 0f).toDouble(),
            memberSince = sharedPref.getString("member_since", "") ?: ""
        )
    }

    fun activateDriver() {
        with(sharedPref.edit()) {
            putString("driver_status", "activo")
            putBoolean("driver_available", true)
            putBoolean("driver_online", true)
            putBoolean("driver_activation_status", true)
            apply()
        }
    }

    fun deactivateDriver() {
        with(sharedPref.edit()) {
            putString("driver_status", "inactivo")
            putBoolean("driver_available", false)
            putBoolean("driver_online", false)
            putBoolean("driver_activation_status", false)
            apply()
        }
    }

    fun setDriverAvailability(available: Boolean) {
        sharedPref.edit().putBoolean("driver_available", available).apply()
    }

    fun setDriverOnline(online: Boolean) {
        sharedPref.edit().putBoolean("driver_online", online).apply()
    }

    fun addTrip(trip: Trip) {
        val trips = getTrips().toMutableList()
        val newTrip = if (trip.id.isBlank()) {
            trip.copy(id = "trip_${System.currentTimeMillis()}")
        } else {
            trip
        }
        trips.add(newTrip)
        saveTripsToPrefs(trips)
    }

    private fun saveTripsToPrefs(trips: List<Trip>) {
        val jsonArray = JSONArray()
        trips.forEach { trip ->
            val jsonObject = JSONObject().apply {
                put("id", trip.id)
                put("passengerName", trip.passengerName)
                put("startAddress", trip.startAddress)
                put("endAddress", trip.endAddress)
                put("distance", trip.distance)
                put("earnings", trip.earnings)
                put("timestamp", trip.timestamp)
                put("status", trip.status)
            }
            jsonArray.put(jsonObject)
        }
        tripsPref.edit().putString("driver_trips", jsonArray.toString()).apply()
    }

    fun getTrips(): List<Trip> {
        val jsonString = tripsPref.getString("driver_trips", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val trips = mutableListOf<Trip>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            trips.add(
                Trip(
                    id = jsonObject.getString("id"),
                    passengerName = jsonObject.getString("passengerName"),
                    startAddress = jsonObject.getString("startAddress"),
                    endAddress = jsonObject.getString("endAddress"),
                    distance = jsonObject.getDouble("distance"),
                    earnings = jsonObject.getDouble("earnings"),
                    timestamp = jsonObject.getLong("timestamp"),
                    status = jsonObject.getString("status")
                )
            )
        }
        return trips.sortedByDescending { it.timestamp }
    }

    fun addExpense(expense: Expense) {
        val expenses = getExpenses().toMutableList()
        val newExpense = if (expense.id.isBlank()) {
            expense.copy(id = "expense_${System.currentTimeMillis()}")
        } else {
            expense
        }
        expenses.add(newExpense)
        saveExpensesToPrefs(expenses)
    }

    fun deleteExpense(expenseId: String) {
        val expenses = getExpenses().toMutableList()
        expenses.removeAll { it.id == expenseId }
        saveExpensesToPrefs(expenses)
    }

    private fun saveExpensesToPrefs(expenses: List<Expense>) {
        val jsonArray = JSONArray()
        expenses.forEach { expense ->
            val jsonObject = JSONObject().apply {
                put("id", expense.id)
                put("type", expense.type)
                put("amount", expense.amount)
                put("description", expense.description)
                put("timestamp", expense.timestamp)
                put("category", expense.category)
            }
            jsonArray.put(jsonObject)
        }
        expensesPref.edit().putString("driver_expenses", jsonArray.toString()).apply()
    }

    fun getExpenses(): List<Expense> {
        val jsonString = expensesPref.getString("driver_expenses", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val expenses = mutableListOf<Expense>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            expenses.add(
                Expense(
                    id = jsonObject.getString("id"),
                    type = jsonObject.getString("type"),
                    amount = jsonObject.getDouble("amount"),
                    description = jsonObject.getString("description"),
                    timestamp = jsonObject.getLong("timestamp"),
                    category = jsonObject.getString("category")
                )
            )
        }
        return expenses.sortedByDescending { it.timestamp }
    }

    fun updateDriverStats(earnings: Double) {
        val prefs = sharedPref
        val currentTrips = prefs.getInt("driver_total_trips", 0)
        val currentMonthly = prefs.getFloat("driver_monthly_earnings", 0f)
        val currentTotal = prefs.getFloat("driver_total_earnings", 0f)

        with(prefs.edit()) {
            putInt("driver_total_trips", currentTrips + 1)
            putFloat("driver_monthly_earnings", currentMonthly + earnings.toFloat())
            putFloat("driver_total_earnings", currentTotal + earnings.toFloat())
            apply()
        }
    }
}

class DriverHomeActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var driverPreferences: DriverPreferences

    
    private lateinit var firestoreService: FirestoreService
    private lateinit var rideService: RideService
    private lateinit var tripMatchingManager: TripMatchingManager
    private lateinit var driverActivationService: DriverActivationService
    private lateinit var notificationService: com.amurayada.yallego.services.NotificationService

    private fun initializeFCMToken() {
        val driverId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d("FCMToken", "✅ Token FCM obtenido: ${token.take(10)}...")
                saveFCMTokenToFirestore(driverId, token)
            } catch (e: Exception) {
                Log.e("FCMToken", "❌ Error obteniendo token FCM: ${e.message}")
            }
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val newToken = task.result
                Log.d("FCMToken", "🔄 Token FCM actualizado: ${newToken.take(10)}...")

                FirebaseAuth.getInstance().currentUser?.uid?.let { driverId ->
                    CoroutineScope(Dispatchers.IO).launch {
                        saveFCMTokenToFirestore(driverId, newToken)
                    }
                }
            } else {
                Log.e("FCMToken", "❌ Error en listener de token: ${task.exception}")
            }
        }
    }

    private suspend fun saveFCMTokenToFirestore(driverId: String, token: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val driverRef = db.collection("drivers").document(driverId)

            val updateData = mapOf(
                "fcmToken" to token,
                "lastTokenUpdate" to com.google.firebase.Timestamp.now()
            )

            driverRef.update(updateData).await()
            Log.d("FCMToken", "✅ Token FCM guardado en Firestore para conductor: $driverId")

        } catch (e: Exception) {
            Log.e("FCMToken", "❌ Error guardando token FCM: ${e.message}")
        }
    }

    private var rideRequestListener: ListenerRegistration? = null
    private var driverStatusListener: ListenerRegistration? = null

    private var currentLocation: Location? = null
    private var isFollowingLocation = true
    private var areLocationUpdatesRunning = false

    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private var userLocationMarker: Marker? = null

    private var currentTripRequest by mutableStateOf<TripRequest?>(null)
    private var showTripRequestDialog by mutableStateOf(false)
    private var isAcceptingTrip by mutableStateOf(false)

    private var isOnTrip by mutableStateOf(false)
    private var showProximityAlert by mutableStateOf(false)
    private var proximityMessage by mutableStateOf("")

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("DriverHome", "✅ Permiso de notificaciones concedido")
        } else {
            Log.e("DriverHome", "❌ Permiso de notificaciones denegado")
            Toast.makeText(this, "Se requieren notificaciones para alertas de viaje", Toast.LENGTH_LONG).show()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentLocation = location

            
            FirebaseAuth.getInstance().currentUser?.uid?.let { driverId ->
                CoroutineScope(Dispatchers.IO).launch {
                    rideService.updateDriverLocation(
                        driverId,
                        LatLng(location.latitude, location.longitude)
                    )
                }
            }

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
        driverPreferences = DriverPreferences(this)

        
        firestoreService = FirestoreService()
        rideService = RideService()
        tripMatchingManager = TripMatchingManager(this)
        driverActivationService = DriverActivationService()
        notificationService = com.amurayada.yallego.services.NotificationService(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }



        setContent {
            val driverData = remember {
                driverPreferences.getDriverProfile()
            }

            DriverYaLlegoApp(
                onLocateClick = { logout() },
                onLogout = { logout() },
                driverData = driverData,
                onUpdateDriverData = { name, email, phone, cedula, tipoVehiculo, placa, modelo, colorVehiculo ->
                    driverPreferences.saveDriverData(name, email, phone, cedula, tipoVehiculo, placa, modelo, colorVehiculo)
                },
                onUpdateDriverStatus = { available, online ->
                    
                    FirebaseAuth.getInstance().currentUser?.uid?.let { driverId ->
                        CoroutineScope(Dispatchers.IO).launch {
                            driverActivationService.updateDriverAvailability(driverId, available)
                        }
                    }
                    driverPreferences.setDriverAvailability(available)
                    driverPreferences.setDriverOnline(online)
                },
                onActivateDriver = {
                    activateDriverReal()
                },
                onDeactivateDriver = {
                    deactivateDriverReal()
                },
                onMapViewCreated = { view ->
                    mapView = view
                },
                onMapReady = { map ->
                    maplibreMap = map
                    currentLocation?.let { location ->
                        updateUserLocationMarker(location)
                    }
                    
                    initializeRealTimeServices()
                    
                    debugRideRequests()
                },
                onAcceptTrip = { tripRequest ->
                    acceptTripRequest(tripRequest)
                },
                onRejectTrip = { tripRequest ->
                    rejectTripRequest(tripRequest)
                },
                onStartTrip = {
                    startTrip()
                },
                onCompleteTrip = {
                    completeTrip()
                },
                onAddExpense = { expense ->
                    driverPreferences.addExpense(expense)
                },
                onDeleteExpense = { expenseId ->
                    driverPreferences.deleteExpense(expenseId)
                },
                currentTripRequest = currentTripRequest,
                showTripRequestDialog = showTripRequestDialog,
                isOnTrip = isOnTrip,
                currentLocation = currentLocation,
                isFollowingLocation = isFollowingLocation,
                onFollowingLocationChange = { isFollowing ->
                    isFollowingLocation = isFollowing
                }
            )
        }
    }
    
    private fun initializeRealTimeServices() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            
            loadDriverRealData(user.uid)

            
            startListeningAssignedRides(user.uid)

            
            startListeningDriverStatus(user.uid)

            
            startRealTimeLocationUpdates(user.uid)

            
            if (driverPreferences.getDriverActivationStatus()) {
                startPassengerProximityListener(user.uid)
            }

            Log.d("DriverHome", "🎯 Servicios iniciados para conductor: ${user.uid}")
        }
    }

    
    private fun debugRideRequests() {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        Log.d("DebugRides", "🔍 Buscando ride_requests en Firestore...")

        db.collection("ride_requests")
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                Log.d("DebugRides", "📊 Total ride_requests en DB: ${result.size()}")
                if (result.isEmpty) {
                    Log.d("DebugRides", "📭 No hay solicitudes de viaje en la base de datos")
                } else {
                    for (document in result.documents) {
                        
                        val status = document.getString("status") ?: "null"
                        val driverId = document.getString("driverId") ?: "null"
                        val userName = document.getString("userName") ?: "null"
                        val userAddress = document.getString("userAddress") ?: "null"

                        Log.d("DebugRides", "   📄 ID: ${document.id}")
                        Log.d("DebugRides", "     Status: $status")
                        Log.d("DebugRides", "     DriverId: '$driverId'")
                        Log.d("DebugRides", "     Pasajero: $userName")
                        Log.d("DebugRides", "     Origen: $userAddress")

                        
                        val createdAt = document.get("createdAt")
                        if (createdAt != null) {
                            Log.d("DebugRides", "     createdAt tipo: ${createdAt.javaClass.simpleName}")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("DebugRides", "❌ Error leyendo ride_requests: ${e.message}")
            }
    }

    
    private fun loadDriverRealData(driverId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val driver = firestoreService.getDriverData(driverId)
                driver?.let {
                    
                    driverPreferences.saveDriverData(
                        name = it.name,
                        email = FirebaseAuth.getInstance().currentUser?.email ?: "",
                        phone = it.phone,
                        cedula = driverId,
                        tipoVehiculo = it.vehicleInfo.model,
                        placa = it.vehicleInfo.plate,
                        modelo = it.vehicleInfo.model,
                        colorVehiculo = it.vehicleInfo.color
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverHome", "Error cargando datos conductor: ${e.message}")
            }
        }
    }
    
    private fun startListeningAssignedRides(driverId: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        Log.d("RideListener", "🔔 Iniciando escucha de viajes para conductor: $driverId")
        Log.d("RideListener", "📊 Estado conductor - Available: ${driverPreferences.getDriverProfile().isAvailable}, OnTrip: $isOnTrip")
        
        rideRequestListener = db.collection("ride_requests")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", "searching_drivers")
            .addSnapshotListener { snapshots, error ->
                error?.let {
                    Log.e("RideListener", "❌ Error escuchando nuevas solicitudes: ${it.message}")
                    return@addSnapshotListener
                }

                snapshots?.let { querySnapshot ->
                    Log.d("RideListener", "📨 Nuevas solicitudes encontradas: ${querySnapshot.documents.size}")

                    for (document in querySnapshot.documents) {
                        Log.d("RideListener", "📄 Documento: ${document.id}")
                        Log.d("RideListener", "   Status: ${document.getString("status")}")
                        Log.d("RideListener", "   DriverId: ${document.getString("driverId")}")
                        
                        val userName = document.getString("passengerName") ?: document.getString("userName") ?: ""
                        val userAddress = document.get("pickupLocation") as? Map<*, *>
                        val destination = document.get("destination") as? Map<*, *>
                        val distance = document.getDouble("distance") ?: 0.0
                        val price = document.getDouble("estimatedPrice") ?: 0.0
                        
                        val userLat = (userAddress?.get("lat") as? Number)?.toDouble() ?: 0.0
                        val userLng = (userAddress?.get("lng") as? Number)?.toDouble() ?: 0.0
                        val userAddr = userAddress?.get("address") as? String ?: ""
                        
                        val destLat = (destination?.get("lat") as? Number)?.toDouble() ?: 0.0
                        val destLng = (destination?.get("lng") as? Number)?.toDouble() ?: 0.0
                        val destAddr = destination?.get("address") as? String ?: ""

                        val request = RideRequest().copy(
                            id = document.id,
                            userId = document.getString("passengerId") ?: "",
                            userName = userName,
                            passengerName = userName,
                            userAddress = userAddr,
                            userLocation = LatLng(userLat, userLng),
                            destination = LatLng(destLat, destLng),
                            destinationAddress = destAddr,
                            destinationLocation = LatLng(destLat, destLng),
                            distance = distance,
                            price = price,
                            estimatedPrice = price,
                            status = document.getString("status") ?: "",
                            driverId = document.getString("driverId") ?: "",
                            driverName = "",
                            timestamp = System.currentTimeMillis()
                        )
                        
                        Log.d("RideListener", "🎯 Nueva solicitud para este conductor!")
                        Log.d("RideListener", "   Pasajero: $userName")
                        Log.d("RideListener", "   Origen: $userAddr")
                        Log.d("RideListener", "   Destino: $destAddr")
                        
                        if (!isOnTrip) {
                            Log.d("RideListener", "✅ Mostrando solicitud al conductor")
                            showTripRequestToDriver(request)
                            
                            
                            notificationService.showTripRequestNotification(
                                passengerName = userName,
                                distance = String.format("%.1f km", distance)
                            )
                        } else {
                            Log.d("RideListener", "❌ Conductor ya está en viaje")
                        }
                    }
                }
            }

        
        val assignedListener = db.collection("ride_requests")
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf("driver_assigned", "accepted", "in_progress"))
            .addSnapshotListener { snapshots, error ->
                error?.let {
                    Log.e("RideListener", "❌ Error escuchando viajes asignados: ${it.message}")
                    return@addSnapshotListener
                }
                
                snapshots?.let { querySnapshot ->
                    Log.d("RideListener", "📋 Viajes asignados: ${querySnapshot.documents.size}")

                    for (document in querySnapshot.documents) {
                        val userName = document.getString("passengerName") ?: document.getString("userName") ?: ""
                        val userAddress = document.get("pickupLocation") as? Map<*, *>
                        val destination = document.get("destination") as? Map<*, *>
                        val distance = document.getDouble("distance") ?: 0.0
                        val price = document.getDouble("estimatedPrice") ?: document.getDouble("price") ?: 0.0
                        val status = document.getString("status") ?: ""
                        
                        val userLat = (userAddress?.get("lat") as? Number)?.toDouble() ?: 0.0
                        val userLng = (userAddress?.get("lng") as? Number)?.toDouble() ?: 0.0
                        val userAddr = userAddress?.get("address") as? String ?: ""
                        
                        val destLat = (destination?.get("lat") as? Number)?.toDouble() ?: 0.0
                        val destLng = (destination?.get("lng") as? Number)?.toDouble() ?: 0.0
                        val destAddr = destination?.get("address") as? String ?: ""

                        val request = RideRequest(
                            id = document.id,
                            userId = document.getString("passengerId") ?: "",
                            userName = userName,
                            passengerName = userName,
                            userAddress = userAddr,
                            userLocation = LatLng(userLat, userLng),
                            destination = LatLng(destLat, destLng),
                            destinationAddress = destAddr,
                            destinationLocation = LatLng(destLat, destLng),
                            distance = distance,
                            price = price,
                            status = status,
                            driverId = document.getString("driverId") ?: "",
                            driverName = ""
                        )
                        
                        Log.d("RideListener", "📍 Viaje asignado: ${request.id}, Estado: ${request.status}")

                        when (request.status) {
                            "driver_assigned", "accepted" -> {
                                if (currentTripRequest == null && !isOnTrip) {
                                    showTripAssignedNotification(request)
                                }
                            }
                            "in_progress" -> {
                                currentTripRequest = TripRequest(
                                    id = request.id,
                                    passengerName = request.passengerName.ifEmpty { request.userName }.ifEmpty { "Pasajero" },
                                    startAddress = request.userAddress,
                                    endAddress = request.destinationAddress,
                                    distance = request.distance,
                                    estimatedPrice = request.price,
                                    startLocation = request.userLocation,
                                    endLocation = request.destinationLocation
                                )
                                isOnTrip = true
                            }
                        }
                    }
                }
            }
    }
    
    private fun showTripRequestToDriver(rideRequest: RideRequest) {
        runOnUiThread {
            Log.d("RideListener", "🎯 Mostrando solicitud de viaje al conductor")

            val tripRequest = TripRequest(
                id = rideRequest.id,
                passengerName = rideRequest.passengerName.ifEmpty { rideRequest.userName },
                startAddress = rideRequest.userAddress,
                endAddress = rideRequest.destinationAddress,
                distance = rideRequest.distance,
                estimatedPrice = rideRequest.price,
                startLocation = rideRequest.userLocation,
                endLocation = rideRequest.destinationLocation
            )

            showTripRequest(tripRequest)

            
            playNotificationSound()
        }
    }

    
    private fun showTripAssignedNotification(rideRequest: RideRequest) {
        runOnUiThread {
            Log.d("RideListener", "✅ Viaje asignado al conductor")

            val tripRequest = TripRequest(
                id = rideRequest.id,
                passengerName = rideRequest.passengerName.ifEmpty { rideRequest.userName },
                startAddress = rideRequest.userAddress,
                endAddress = rideRequest.destinationAddress,
                distance = rideRequest.distance,
                estimatedPrice = rideRequest.price,
                startLocation = rideRequest.userLocation,
                endLocation = rideRequest.destinationLocation
            )

            currentTripRequest = tripRequest

            Toast.makeText(
                this@DriverHomeActivity,
                "🚗 Viaje asignado: ${rideRequest.userName} te está esperando",
                Toast.LENGTH_LONG
            ).show()

            playNotificationSound()
        }
    }

    
    private fun startPassengerProximityListener(driverId: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        db.collection("ride_requests")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { snapshots, error ->
                error?.let {
                    Log.e("ProximityListener", "Error: ${it.message}")
                    return@addSnapshotListener
                }

                snapshots?.let { querySnapshot ->
                    for (document in querySnapshot.documents) {
                        val rideRequest = document.toObject(RideRequest::class.java)
                        rideRequest?.let { request ->
                            
                            checkPassengerProximity(request, driverId)
                        }
                    }
                }
            }
    }

    private fun checkPassengerProximity(rideRequest: RideRequest, driverId: String) {
        currentLocation?.let { driverLocation ->
            val passengerLocation = android.location.Location("passenger").apply {
                latitude = rideRequest.userLocation.latitude
                longitude = rideRequest.userLocation.longitude
            }

            val distance = driverLocation.distanceTo(passengerLocation)

            
            if (distance < 200 && distance > 50) {
                showProximityNotification(rideRequest.userName, distance)
            } else if (distance <= 50) {
                showArrivalNotification(rideRequest.userName)
            }
        }
    }

    private fun showProximityNotification(passengerName: String, distance: Float) {
        runOnUiThread {
            Toast.makeText(
                this,
                "🔔 $passengerName está a ${"%.0f".format(distance)} metros",
                Toast.LENGTH_SHORT
            ).show()

            
            showProximityInUI(passengerName, distance)
        }
    }

    private fun showArrivalNotification(passengerName: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                "✅ Has llegado con $passengerName",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showProximityInUI(passengerName: String, distance: Float) {
        proximityMessage = "🔔 $passengerName está a ${"%.0f".format(distance)} metros"
        showProximityAlert = true

        
        CoroutineScope(Dispatchers.Main).launch {
            delay(5000)
            showProximityAlert = false
        }
    }

    
    private fun playNotificationSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            Log.e("Notification", "Error con sonido: ${e.message}")
        }
    }

    
    private fun startListeningDriverStatus(driverId: String) {
        driverStatusListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("drivers")
            .document(driverId)
            .addSnapshotListener { snapshot, error ->
                error?.let {
                    Log.e("DriverStatus", "Error escuchando estado: ${it.message}")
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val isAvailable = doc.getBoolean("isAvailable") ?: false
                    val estado = doc.getString("estado") ?: "pendiente"

                    
                    driverPreferences.setDriverAvailability(isAvailable)
                }
            }
    }

    
    private fun startRealTimeLocationUpdates(driverId: String) {
        Log.d("DriverHome", "📍 Iniciando actualizaciones de ubicación para: $driverId")

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                currentLocation = location

                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        
                        firestoreService.updateDriverLocationReal(driverId, location.latitude, location.longitude)

                        Log.d("LocationUpdate", "📍 Ubicación actualizada: ${location.latitude}, ${location.longitude}")

                    } catch (e: Exception) {
                        Log.e("LocationUpdate", "❌ Error actualizando ubicación: ${e.message}")
                    }
                }

                if (isFollowingLocation) {
                    updateUserLocation(location)
                }
                updateUserLocationMarker(location)
            }
        }

        
        areLocationUpdatesRunning = true
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
            fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)

            Log.d("LocationUpdate", "🟢 Actualizaciones de ubicación iniciadas cada 5 segundos")

        } catch (e: SecurityException) {
            areLocationUpdatesRunning = false
            Log.e("LocationUpdate", "❌ Permisos de ubicación no disponibles")
            Toast.makeText(this@DriverHomeActivity, "Error: Permisos de ubicación no disponibles", Toast.LENGTH_LONG).show()
        }
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

    private fun createDriverLocationIcon(): Bitmap {
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

        paint.color = android.graphics.Color.parseColor("#34A853")
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, radius - 1.5f, paint)

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
                    .snippet("Conductor")
                    .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(createDriverLocationIcon()))
            )
        } else {
            userLocationMarker?.position = userLatLng
        }
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

    
    private fun showTripRequest(tripRequest: TripRequest) {
        currentTripRequest = tripRequest
        showTripRequestDialog = true
    }

    private fun acceptTripRequest(tripRequest: TripRequest) {
        if (isAcceptingTrip) return 

        val driverId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val driverName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Conductor"

        Log.d("TripAccept", "✅ Conductor aceptando viaje: ${tripRequest.id}")
        
        
        isAcceptingTrip = true

        CoroutineScope(Dispatchers.IO).launch {
            val success = rideService.acceptRide(tripRequest.id, driverId, driverName)
            runOnUiThread {
                if (success) {
                    Log.d("TripAccept", "🎉 Viaje aceptado exitosamente")
                    currentTripRequest = tripRequest
                    showTripRequestDialog = false
                    driverPreferences.setDriverAvailability(false)

                    Toast.makeText(this@DriverHomeActivity, "✅ Viaje aceptado. Dirígete al pasajero.", Toast.LENGTH_LONG).show()

                    
                    val intent = Intent(this@DriverHomeActivity, NavigationActivity::class.java).apply {
                        putExtra("DESTINATION_NAME", "Recoger a ${tripRequest.passengerName}")
                        putExtra("DESTINATION_LAT", tripRequest.startLocation.latitude)
                        putExtra("DESTINATION_LON", tripRequest.startLocation.longitude)
                        putExtra("START_LAT", currentLocation?.latitude ?: 0.0)
                        putExtra("START_LON", currentLocation?.longitude ?: 0.0)
                        putExtra("TRIP_AMOUNT", tripRequest.estimatedPrice)
                        
                        
                        putExtra("NAV_MODE", "PICKUP")
                        putExtra("FINAL_DEST_LAT", tripRequest.endLocation.latitude)
                        putExtra("FINAL_DEST_LON", tripRequest.endLocation.longitude)
                        putExtra("FINAL_DEST_NAME", tripRequest.endAddress)
                    }
                    startActivity(intent)
                } else {
                    Log.e("TripAccept", "❌ Error al aceptar el viaje")
                    Toast.makeText(this@DriverHomeActivity, "Error al aceptar el viaje", Toast.LENGTH_SHORT).show()
                }
                
                isAcceptingTrip = false
            }
        }
    }

    private fun rejectTripRequest(tripRequest: TripRequest) {
        currentTripRequest = null
        showTripRequestDialog = false
        isAcceptingTrip = false 
        Toast.makeText(this, "Viaje rechazado", Toast.LENGTH_SHORT).show()
    }

    private fun startTrip() {
        currentTripRequest?.let { tripRequest ->
            CoroutineScope(Dispatchers.IO).launch {
                val success = rideService.markUserPickedUp(tripRequest.id)
                runOnUiThread {
                    if (success) {
                        isOnTrip = true
                        Toast.makeText(this@DriverHomeActivity, "Viaje iniciado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DriverHomeActivity, "Error al iniciar viaje", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun completeTrip() {
        currentTripRequest?.let { tripRequest ->
            CoroutineScope(Dispatchers.IO).launch {
                val success = rideService.completeRide(tripRequest.id)
                runOnUiThread {
                    if (success) {
                        
                        val trip = Trip(
                            passengerName = tripRequest.passengerName,
                            startAddress = tripRequest.startAddress,
                            endAddress = tripRequest.endAddress,
                            distance = tripRequest.distance,
                            earnings = tripRequest.estimatedPrice
                        )
                        driverPreferences.addTrip(trip)
                        driverPreferences.updateDriverStats(tripRequest.estimatedPrice)

                        
                        isOnTrip = false
                        currentTripRequest = null

                        
                        driverPreferences.setDriverAvailability(true)

                        Toast.makeText(this@DriverHomeActivity, "Viaje completado. Ganancias registradas.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@DriverHomeActivity, "Error al completar viaje", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    
    private fun activateDriverReal() {
        val driverId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val success = driverActivationService.activateDriver(driverId)
            runOnUiThread {
                if (success) {
                    driverPreferences.setDriverActivationStatus(true)
                    Toast.makeText(this@DriverHomeActivity, "✅ Cuenta activada exitosamente", Toast.LENGTH_LONG).show()

                    
                    val serviceIntent = Intent(this@DriverHomeActivity, DriverService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    
                    startPassengerProximityListener(driverId)
                } else {
                    Toast.makeText(this@DriverHomeActivity, "❌ Error activando cuenta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deactivateDriverReal() {
        val driverId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val success = driverActivationService.deactivateDriver(driverId)
            runOnUiThread {
                if (success) {
                    driverPreferences.setDriverActivationStatus(false)
                    Toast.makeText(this@DriverHomeActivity, "🔴 Cuenta desactivada", Toast.LENGTH_LONG).show()

                    
                    val serviceIntent = Intent(this@DriverHomeActivity, DriverService::class.java)
                    serviceIntent.action = "STOP_SERVICE"
                    startService(serviceIntent) 
                    
                    stopService(Intent(this@DriverHomeActivity, DriverService::class.java))
                } else {
                    Toast.makeText(this@DriverHomeActivity, "❌ Error desactivando cuenta", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        if (!areLocationUpdatesRunning) {
            startLocationUpdates()
        }
        mapView?.onResume()
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
        mapView?.onDestroy()
        rideRequestListener?.remove()
        driverStatusListener?.remove()
        tripMatchingManager.cleanup()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        areLocationUpdatesRunning = true
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
            fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            areLocationUpdatesRunning = false
            Toast.makeText(this, "Error: Permisos de ubicación no disponibles", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopLocationUpdates() {
        areLocationUpdatesRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun logout() {
        val appPreferences = AppPreferences(this)
        appPreferences.clearUserData()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @Composable
    fun DriverYaLlegoApp(
        onLocateClick: () -> Unit,
        onLogout: () -> Unit,
        driverData: DriverProfile,
        onUpdateDriverData: (String, String, String, String, String, String, String, String) -> Unit,
        onUpdateDriverStatus: (Boolean, Boolean) -> Unit,
        onActivateDriver: () -> Unit,
        onDeactivateDriver: () -> Unit,
        onMapViewCreated: (MapView) -> Unit,
        onMapReady: (MapLibreMap) -> Unit,
        onAcceptTrip: (TripRequest) -> Unit,
        onRejectTrip: (TripRequest) -> Unit,
        onStartTrip: () -> Unit,
        onCompleteTrip: () -> Unit,
        onAddExpense: (Expense) -> Unit,
        onDeleteExpense: (String) -> Unit,
        currentTripRequest: TripRequest?,
        showTripRequestDialog: Boolean,
        isOnTrip: Boolean,
        currentLocation: Location?,
        isFollowingLocation: Boolean,
        onFollowingLocationChange: (Boolean) -> Unit
        
    ) {
        val navController = rememberNavController()

        
        var showProximityAlert by rememberSaveable { mutableStateOf(false) }
        var proximityMessage by rememberSaveable { mutableStateOf("") }

        Scaffold(
            bottomBar = {
                DriverBottomNavigationBar(navController = navController)
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "driver_map",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable("driver_map") {
                    DriverMapScreen(
                        onLocateClick = onLocateClick,
                        currentLocation = currentLocation,
                        isFollowingLocation = isFollowingLocation,
                        onFollowingLocationChange = onFollowingLocationChange,
                        onMapViewCreated = onMapViewCreated,
                        onMapReady = onMapReady,
                        onUpdateDriverStatus = onUpdateDriverStatus,
                        onActivateDriver = onActivateDriver,
                        onDeactivateDriver = onDeactivateDriver,
                        driverData = driverData,
                        currentTripRequest = currentTripRequest,
                        showTripRequestDialog = showTripRequestDialog,
                        onAcceptTrip = onAcceptTrip,
                        onRejectTrip = onRejectTrip,
                        onStartTrip = onStartTrip,
                        onCompleteTrip = onCompleteTrip,
                        isOnTrip = isOnTrip,

                    )
                }

                composable("driver_earnings") {
                    DriverEarningsScreen(
                        driverData = driverData,
                        trips = driverPreferences.getTrips()
                    )
                }

                composable("driver_expenses") {
                    DriverExpensesScreen(
                        driverData = driverData,
                        expenses = driverPreferences.getExpenses(),
                        onAddExpense = onAddExpense,
                        onDeleteExpense = onDeleteExpense
                    )
                }

                composable("driver_profile") {
                    DriverProfileScreen(
                        onLogout = onLogout,
                        driverData = driverData,
                        onUpdateDriverData = onUpdateDriverData
                    )
                }
            }
        }
    }
    @Composable
    fun DriverBottomNavigationBar(navController: NavController) {
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route

        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Mapa") },
                label = { Text("Mapa") },
                selected = currentDestination == "driver_map",
                onClick = { navController.navigate("driver_map") }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Ganancias") },
                label = { Text("Ganancias") },
                selected = currentDestination == "driver_earnings",
                onClick = { navController.navigate("driver_earnings") }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.Receipt, contentDescription = "Gastos") },
                label = { Text("Gastos") },
                selected = currentDestination == "driver_expenses",
                onClick = { navController.navigate("driver_expenses") }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
                label = { Text("Perfil") },
                selected = currentDestination == "driver_profile",
                onClick = { navController.navigate("driver_profile") }
            )
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DriverMapScreen(
        onLocateClick: () -> Unit,
        currentLocation: Location?,
        isFollowingLocation: Boolean,
        onFollowingLocationChange: (Boolean) -> Unit,
        onMapViewCreated: (MapView) -> Unit,
        onMapReady: (MapLibreMap) -> Unit,
        onUpdateDriverStatus: (Boolean, Boolean) -> Unit,
        onActivateDriver: () -> Unit,
        onDeactivateDriver: () -> Unit,
        driverData: DriverProfile,
        currentTripRequest: TripRequest?,
        showTripRequestDialog: Boolean,
        onAcceptTrip: (TripRequest) -> Unit,
        onRejectTrip: (TripRequest) -> Unit,
        onStartTrip: () -> Unit,
        onCompleteTrip: () -> Unit,
        isOnTrip: Boolean
    ) {
        val context = LocalContext.current

        
        var isDriverActive by remember(driverData.estado) {
            mutableStateOf(driverData.estado == "activo")
        }
        var isDriverAvailable by remember(driverData.isAvailable) {
            mutableStateOf(driverData.isAvailable)
        }
        var showActivationDialog by remember { mutableStateOf(false) }
        var showDeactivationDialog by remember { mutableStateOf(false) }

        
        LaunchedEffect(driverData.estado, driverData.isAvailable) {
            isDriverActive = driverData.estado == "activo"
            isDriverAvailable = driverData.isAvailable
        }

        val availableStyles = listOf(
            "Estándar" to "https://tiles.openfreemap.org/styles/liberty",
            "Satélite" to "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
            "Topográfico" to "https://tiles.openfreemap.org/styles/humanitarian",
            "Oscuro" to "https://basemats.cartocdn.com/gl/dark-matter-gl-style/style.json"
        )
        var currentMapStyle by remember { mutableStateOf(availableStyles[0].second) }
        var showMapStyleMenu by remember { mutableStateOf(false) }

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

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapViewInstance },
                modifier = Modifier.fillMaxSize()
            )

            
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.9f),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        !isDriverActive -> ComposeColor(0xFFFFEBEE)
                        isDriverAvailable -> ComposeColor(0xFFE8F5E8)
                        else -> ComposeColor(0xFFFFF9C4)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when {
                                    !isDriverActive -> "🔴 Cuenta Inactiva"
                                    isDriverAvailable -> "🟢 Conductor Disponible"
                                    else -> "🟡 Activo - No Disponible"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = when {
                                    !isDriverActive -> ComposeColor(0xFFD32F2F)
                                    isDriverAvailable -> ComposeColor(0xFF388E3C)
                                    else -> ComposeColor(0xFFF57C00)
                                }
                            )
                            Text(
                                "${driverData.name} • ${driverData.tipoVehiculo}",
                                fontSize = 14.sp,
                                color = ComposeColor.Gray
                            )
                            if (isOnTrip) {
                                Text(
                                    "🚗 En viaje con ${currentTripRequest?.passengerName ?: "pasajero"}",
                                    fontSize = 12.sp,
                                    color = ComposeColor(0xFF1976D2),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        
                        if (!isDriverActive) {
                            Button(
                                onClick = { showActivationDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ComposeColor(0xFFD32F2F)
                                )
                            ) {
                                Text("Activar")
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Button(
                                    onClick = { showDeactivationDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ComposeColor(0xFF388E3C)
                                    )
                                ) {
                                    Text("Activo")
                                }

                                
                                if (!isOnTrip) {
                                    Switch(
                                        checked = isDriverAvailable,
                                        onCheckedChange = { available ->
                                            
                                            isDriverAvailable = available
                                            
                                            onUpdateDriverStatus(available, true)
                                            
                                            Toast.makeText(
                                                context,
                                                if (available) "🟢 Disponible" else "🟡 No disponible",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    
                    if (!isDriverActive) {
                        Text(
                            "Activa tu cuenta para comenzar a recibir viajes",
                            fontSize = 12.sp,
                            color = ComposeColor(0xFFD32F2F),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (!isDriverAvailable && !isOnTrip) {
                        Text(
                            "Activa la disponibilidad para recibir solicitudes de viaje",
                            fontSize = 12.sp,
                            color = ComposeColor(0xFFF57C00),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (isDriverAvailable && !isOnTrip) {
                        Text(
                            "Estás recibiendo solicitudes de viaje",
                            fontSize = 12.sp,
                            color = ComposeColor(0xFF388E3C),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 120.dp, start = 16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "🚗 ${driverData.placa}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (isOnTrip && currentTripRequest != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCompleteTrip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0xFF34A853)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "COMPLETAR VIAJE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFFFF9C4))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = ComposeColor(0xFF1976D2))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Pasajero", fontSize = 12.sp, color = ComposeColor.Gray)
                                    Text(
                                        currentTripRequest.passengerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = ComposeColor(0xFFD32F2F))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Destino", fontSize = 12.sp, color = ComposeColor.Gray)
                                    Text(currentTripRequest.endAddress, fontSize = 14.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Ganancias: $${"%.0f".format(currentTripRequest.estimatedPrice)}",
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor(0xFF388E3C)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val context = LocalContext.current
                            
                            Button(
                                onClick = {
                                    Log.d("ShareTrip", "=== COMPARTIR VIAJE ===")
                                    Log.d("ShareTrip", "currentTripRequest: $currentTripRequest")
                                    Log.d("ShareTrip", "passengerName: ${currentTripRequest.passengerName}")
                                    Log.d("ShareTrip", "endAddress: ${currentTripRequest.endAddress}")
                                    
                                    val shareText = "🚗 Estoy en un viaje con YaLlego\n" +
                                        "👤 Pasajero: ${currentTripRequest.passengerName}\n" +
                                        "📍 Destino: ${currentTripRequest.endAddress}\n" +
                                        "🛡️ Viaje seguro monitoreado."
                                    
                                    Log.d("ShareTrip", "Texto completo: $shareText")
                                    
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Compartir Viaje - YaLlego")
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartir viaje con..."))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compartir Viaje")
                            }
                        }
                    }
                }
            }

            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { showMapStyleMenu = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Cambiar estilo del mapa")
                }

                FloatingActionButton(
                    onClick = {
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
                        } ?: run {
                            onLocateClick()
                        }
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

            
            if (showActivationDialog) {
                DriverActivationDialog(
                    onActivate = {
                        onActivateDriver()
                        isDriverActive = true
                        showActivationDialog = false
                        Toast.makeText(context, "✅ Cuenta activada exitosamente", Toast.LENGTH_LONG).show()
                    },
                    onContactSupport = {
                        val emailIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("soporte@yallego.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "Activación de Cuenta - Conductor")
                            putExtra(Intent.EXTRA_TEXT,
                                "Hola, necesito activar mi cuenta de conductor.\n\n" +
                                        "Mi información:\n" +
                                        "- Nombre: ${driverData.name}\n" +
                                        "- Email: ${driverData.email}\n" +
                                        "- Cédula: ${driverData.cedula}\n" +
                                        "- Vehículo: ${driverData.tipoVehiculo} ${driverData.modelo}\n" +
                                        "- Placa: ${driverData.placa}\n\n" +
                                        "Por favor activar mi cuenta. Gracias."
                            )
                        }
                        try {
                            context.startActivity(Intent.createChooser(emailIntent, "Contactar soporte"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No hay aplicaciones de email", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { showActivationDialog = false }
                )
            }

            
            if (showDeactivationDialog) {
                DriverDeactivationDialog(
                    onDeactivate = {
                        onDeactivateDriver()
                        isDriverActive = false
                        showDeactivationDialog = false
                        Toast.makeText(context, "🔴 Cuenta desactivada", Toast.LENGTH_LONG).show()
                    },
                    onDismiss = { showDeactivationDialog = false }
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
                                        currentMapStyle = styleUri
                                        maplibreMap?.setStyle(Style.Builder().fromUri(styleUri))
                                        showMapStyleMenu = false
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
                                        Text(text = styleName, fontWeight = FontWeight.Medium)
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

            
            if (showTripRequestDialog && currentTripRequest != null) {
                TripRequestDialog(
                    tripRequest = currentTripRequest!!,
                    isLoading = isAcceptingTrip,
                    onAccept = { acceptTripRequest(currentTripRequest!!) },
                    onReject = { rejectTripRequest(currentTripRequest!!) }
                )
            }
        }
    }

    @Composable
    fun DriverActivationDialog(
        onActivate: () -> Unit,
        onContactSupport: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activar Cuenta de Conductor")
                }
            },
            text = {
                Column {
                    Text(
                        "Al activar tu cuenta aceptas:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text("• Tener licencia de conducción vigente")
                    Text("• Mantener el SOAT actualizado")
                    Text("• Tener revisión técnico-mecánica al día")
                    Text("• Cumplir con las normas de tránsito")
                    Text("• Proveer un servicio seguro y profesional")

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Tu cuenta será verificada por nuestro equipo. " +
                                "Podrías recibir una llamada de confirmación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComposeColor.Gray
                    )
                }
            },
            confirmButton = {
                Column {
                    Button(
                        onClick = onActivate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0xFF34A853)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Activar Mi Cuenta")
                    }

                    TextButton(
                        onClick = onContactSupport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Contactar Soporte")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }

    @Composable
    fun DriverDeactivationDialog(
        onDeactivate: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Desactivar Cuenta")
                }
            },
            text = {
                Column {
                    Text(
                        "¿Estás seguro de que quieres desactivar tu cuenta?",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text("• Dejarás de recibir solicitudes de viaje")
                    Text("• Tu perfil no será visible para pasajeros")
                    Text("• Puedes reactivar en cualquier momento")

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Esta acción no afecta tus viajes en curso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComposeColor.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDeactivate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFFD32F2F)
                    )
                ) {
                    Icon(Icons.Default.PauseCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Desactivar Cuenta")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }

    @Composable
fun TripRequestDialog(
    tripRequest: TripRequest,
    isLoading: Boolean = false,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onReject() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nueva Solicitud de Viaje")
            }
        },
        text = {
            Column {
                Text("Pasajero: ${tripRequest.passengerName}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Desde: ${tripRequest.startAddress}")
                Text("Hasta: ${tripRequest.endAddress}")
                Spacer(Modifier.height(8.dp))
                Text("Distancia: ${"%.1f".format(tripRequest.distance)} km")
                Text("Ganancias: $${"%.0f".format(tripRequest.estimatedPrice)}",
                    color = ComposeColor(0xFF388E3C),
                    fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF34A853))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = ComposeColor.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Aceptando...")
                } else {
                    Text("Aceptar Viaje")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onReject,
                enabled = !isLoading
            ) {
                Text("Rechazar")
            }
        }
    )
}

    @Composable
    fun DriverEarningsScreen(
        driverData: DriverProfile,
        trips: List<Trip>
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Mis Ganancias",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Resumen Financiero",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ganancias del Mes:")
                        Text(
                            "$${"%.0f".format(driverData.monthlyEarnings)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ganancias Totales:")
                        Text(
                            "$${"%.0f".format(driverData.totalEarnings)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Viajes del Mes:")
                        Text(
                            "${driverData.totalTrips} viajes",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Promedio por Viaje:")
                        Text(
                            if (driverData.totalTrips > 0) "$${"%.0f".format(driverData.monthlyEarnings / driverData.totalTrips)}" else "$0",
                            fontWeight = FontWeight.Bold,
                            color = ComposeColor.Gray
                        )
                    }
                }
            }

            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Historial de Viajes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (trips.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(trips) { trip ->
                                TripItem(trip = trip)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = "Sin viajes",
                                    modifier = Modifier.size(64.dp),
                                    tint = ComposeColor.Gray
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Aún no has realizado viajes",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ComposeColor.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Activa tu disponibilidad para recibir solicitudes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ComposeColor.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TripItem(trip: Trip) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.passengerName, fontWeight = FontWeight.Bold)
                    Text(trip.startAddress, fontSize = 12.sp, color = ComposeColor.Gray)
                    Text(trip.endAddress, fontSize = 12.sp, color = ComposeColor.Gray)
                    Text(
                        "${"%.1f".format(trip.distance)} km • ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(trip.timestamp))}",
                        fontSize = 11.sp,
                        color = ComposeColor.Gray
                    )
                }
                Text(
                    "$${"%.0f".format(trip.earnings)}",
                    fontWeight = FontWeight.Bold,
                    color = ComposeColor(0xFF388E3C)
                )
            }
        }
    }

    @Composable
    fun DriverExpensesScreen(
        driverData: DriverProfile,
        expenses: List<Expense>,
        onAddExpense: (Expense) -> Unit,
        onDeleteExpense: (String) -> Unit
    ) {
        var showAddExpenseDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Mis Gastos",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Resumen de Gastos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gastos del Mes:")
                        Text(
                            "$${"%.0f".format(driverData.monthlyExpenses)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gastos Totales:")
                        Text(
                            "$${"%.0f".format(driverData.totalExpenses)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ganancias Netas:")
                        Text(
                            "$${"%.0f".format(driverData.monthlyEarnings - driverData.monthlyExpenses)}",
                            fontWeight = FontWeight.Bold,
                            color = ComposeColor(0xFF388E3C)
                        )
                    }
                }
            }

            Button(
                onClick = { showAddExpenseDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar Gasto")
            }

            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Historial de Gastos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (expenses.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(expenses) { expense ->
                                ExpenseItem(
                                    expense = expense,
                                    onDelete = { onDeleteExpense(expense.id) }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = "Sin gastos",
                                    modifier = Modifier.size(64.dp),
                                    tint = ComposeColor.Gray
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No hay gastos registrados",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ComposeColor.Gray
                                )
                            }
                        }
                    }
                }
            }

            
            if (showAddExpenseDialog) {
                AddExpenseDialog(
                    onAddExpense = { expense ->
                        onAddExpense(expense)
                        showAddExpenseDialog = false
                    },
                    onDismiss = { showAddExpenseDialog = false }
                )
            }
        }
    }

    @Composable
    fun ExpenseItem(
        expense: Expense,
        onDelete: () -> Unit
    ) {
        var showDeleteConfirmation by remember { mutableStateOf(false) }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Eliminar Gasto") },
                text = { Text("¿Estás seguro de que quieres eliminar este gasto?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete()
                            showDeleteConfirmation = false
                        }
                    ) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.type, fontWeight = FontWeight.Bold)
                    Text(expense.description, fontSize = 12.sp, color = ComposeColor.Gray)
                    Text(
                        "${expense.category} • ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(expense.timestamp))}",
                        fontSize = 11.sp,
                        color = ComposeColor.Gray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "-$${"%.0f".format(expense.amount)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AddExpenseDialog(
        onAddExpense: (Expense) -> Unit,
        onDismiss: () -> Unit
    ) {
        var expenseType by remember { mutableStateOf("") }
        var expenseAmount by remember { mutableStateOf("") }
        var expenseDescription by remember { mutableStateOf("") }
        var expenseCategory by remember { mutableStateOf("") }

        val expenseCategories = listOf(
            "Combustible",
            "Mantenimiento",
            "Seguro",
            "Impuestos",
            "Lavado",
            "Reparaciones",
            "Peajes",
            "Estacionamiento",
            "Otros"
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Agregar Gasto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = expenseType,
                        onValueChange = { expenseType = it },
                        label = { Text("Tipo de gasto *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expenseAmount,
                        onValueChange = { expenseAmount = it },
                        label = { Text("Monto *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))

                    
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = expenseCategory,
                            onValueChange = { },
                            label = { Text("Categoría *") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            expenseCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        expenseCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expenseDescription,
                        onValueChange = { expenseDescription = it },
                        label = { Text("Descripción") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (expenseType.isNotBlank() && expenseAmount.isNotBlank() && expenseCategory.isNotBlank()) {
                            val amount = expenseAmount.toDoubleOrNull() ?: 0.0
                            val expense = Expense(
                                type = expenseType,
                                amount = amount,
                                description = expenseDescription,
                                category = expenseCategory
                            )
                            onAddExpense(expense)
                        }
                    },
                    enabled = expenseType.isNotBlank() && expenseAmount.isNotBlank() && expenseCategory.isNotBlank()
                ) {
                    Text("Agregar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }

    @Composable
    fun DriverProfileScreen(
        onLogout: () -> Unit,
        driverData: DriverProfile,
        onUpdateDriverData: (String, String, String, String, String, String, String, String) -> Unit
    ) {
        var isEditing by remember { mutableStateOf(false) }

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
                    Icons.Default.DirectionsCar,
                    contentDescription = "Conductor",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = driverData.name.ifEmpty { "Conductor" },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = driverData.email.ifEmpty { "email@ejemplo.com" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "🚗 Conductor",
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

            
            DriverProfileCard(driverData = driverData)

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
                EditDriverProfileDialog(
                    driverData = driverData,
                    onUpdate = onUpdateDriverData,
                    onDismiss = { isEditing = false }
                )
            }
        }
    }

    @Composable
    fun DriverProfileCard(driverData: DriverProfile) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Información del Conductor",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ProfileItem("Teléfono", driverData.phone.ifEmpty { "No registrado" })
                ProfileItem("Cédula", driverData.cedula.ifEmpty { "No registrada" })
                ProfileItem("Vehículo", "${driverData.tipoVehiculo} • ${driverData.modelo}")
                ProfileItem("Placa", driverData.placa.ifEmpty { "No registrada" })
                ProfileItem("Color", driverData.colorVehiculo.ifEmpty { "No especificado" })
                ProfileItem("Estado", driverData.estado)
                ProfileItem("Miembro desde", driverData.memberSince.ifEmpty { "No especificado" })
                ProfileItem("Viajes completados", "${driverData.totalTrips} viajes")
                ProfileItem("Calificación", "★ ${"%.1f".format(driverData.rating)}")
                ProfileItem("Ganancias totales", "$${"%.0f".format(driverData.totalEarnings)}")
            }
        }
    }

    @Composable
    fun EditDriverProfileDialog(
        driverData: DriverProfile,
        onUpdate: (String, String, String, String, String, String, String, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var editedName by remember { mutableStateOf(driverData.name) }
        var editedEmail by remember { mutableStateOf(driverData.email) }
        var editedPhone by remember { mutableStateOf(driverData.phone) }
        var editedCedula by remember { mutableStateOf(driverData.cedula) }
        var editedTipoVehiculo by remember { mutableStateOf(driverData.tipoVehiculo) }
        var editedPlaca by remember { mutableStateOf(driverData.placa) }
        var editedModelo by remember { mutableStateOf(driverData.modelo) }
        var editedColorVehiculo by remember { mutableStateOf(driverData.colorVehiculo) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Editar Perfil de Conductor") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Nombre Completo") },
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedCedula,
                        onValueChange = { editedCedula = it },
                        label = { Text("Cédula") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedTipoVehiculo,
                        onValueChange = { editedTipoVehiculo = it },
                        label = { Text("Tipo de Vehículo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedPlaca,
                        onValueChange = { editedPlaca = it },
                        label = { Text("Placa") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedModelo,
                        onValueChange = { editedModelo = it },
                        label = { Text("Modelo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedColorVehiculo,
                        onValueChange = { editedColorVehiculo = it },
                        label = { Text("Color del Vehículo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdate(editedName, editedEmail, editedPhone, editedCedula, editedTipoVehiculo, editedPlaca, editedModelo, editedColorVehiculo)
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
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
}