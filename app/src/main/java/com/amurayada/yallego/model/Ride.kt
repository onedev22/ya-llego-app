package com.amurayada.yallego.model

import java.util.Date

data class RideRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val pickupAddress: String = "",
    val destination: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val status: String = "searching",
    val price: Double = 0.0,
    val distance: Double = 0.0,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val createdAt: Date? = null,
    val acceptedAt: Date? = null,
    val completedAt: Date? = null
)

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)

enum class RideStatus {
    SEARCHING, DRIVER_ASSIGNED, PICKUP, ON_TRIP, COMPLETED, CANCELLED
}