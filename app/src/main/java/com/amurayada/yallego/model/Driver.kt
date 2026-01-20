package com.amurayada.yallego.model

import java.util.Date

data class Driver(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val rating: Double = 0.0,
    val totalRides: Int = 0,
    val isAvailable: Boolean = false,
    val isOnline: Boolean = false,
    val currentLocation: DriverLocation? = null,
    val vehicleInfo: VehicleInfo = VehicleInfo(),
    val lastUpdate: Date? = null,

    
    val fcmToken: String = "",
    val estado: String = "pendiente",
    val disponible: Boolean = false,
    val tipoVehiculo: String = "",
    val placa: String = "",
    val modelo: String = "",
    val colorVehiculo: String = "",
    val userType: String = "conductor",
    val telefono: String = "",
    val createdAt: Date? = null
)

data class DriverLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Date? = null
)

data class VehicleInfo(
    val plate: String = "",
    val model: String = "",
    val color: String = "",
    val year: Int = 0,
    val type: String = "" 
)