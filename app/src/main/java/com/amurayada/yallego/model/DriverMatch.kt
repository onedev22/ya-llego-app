package com.amurayada.yallego.models

import org.maplibre.android.geometry.LatLng

data class DriverMatch(
    val driverId: String = "",
    val driverName: String = "",
    val vehicleInfo: VehicleInfo = VehicleInfo(),
    val currentLocation: LatLng = LatLng(0.0, 0.0),
    val distanceToPassenger: Double = 0.0,
    val estimatedArrivalTime: Int = 0,
    val rating: Double = 0.0,
    val totalTrips: Int = 0,
    val isAvailable: Boolean = false
)

data class VehicleInfo(
    val type: String = "",
    val plate: String = "",
    val model: String = "",
    val color: String = ""
)