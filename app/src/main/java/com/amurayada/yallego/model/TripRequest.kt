package com.amurayada.yallego.models

import org.maplibre.android.geometry.LatLng

data class TripRequest(
    val id: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val startLocation: LatLng = LatLng(0.0, 0.0),
    val endLocation: LatLng = LatLng(0.0, 0.0),
    val startAddress: String = "",
    val endAddress: String = "",
    val distance: Double = 0.0,
    val estimatedPrice: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: TripStatus = TripStatus.PENDING
)

enum class TripStatus {
    PENDING,      
    ACCEPTED,     
    IN_PROGRESS,  
    COMPLETED,    
    CANCELLED     
}