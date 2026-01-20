package com.amurayada.yallego

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.geometry.LatLng
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit
import android.util.Log

data class OSRMResponse(
    @SerializedName("routes") val routes: List<OSRMRoute>,
    @SerializedName("code") val code: String
)

data class OSRMRoute(
    @SerializedName("geometry") val geometry: String,
    @SerializedName("legs") val legs: List<OSRMLeg>,
    @SerializedName("distance") val distance: Double,
    @SerializedName("duration") val duration: Double
)

data class OSRMLeg(
    @SerializedName("steps") val steps: List<OSRMStep>,
    @SerializedName("distance") val distance: Double,
    @SerializedName("duration") val duration: Double,
    @SerializedName("summary") val summary: String
)

data class OSRMStep(
    @SerializedName("geometry") val geometry: String,
    @SerializedName("distance") val distance: Double,
    @SerializedName("duration") val duration: Double,
    @SerializedName("name") val name: String?,
    @SerializedName("maneuver") val maneuver: OSRMManeuver
)

data class OSRMManeuver(
    @SerializedName("type") val type: String,
    @SerializedName("modifier") val modifier: String?,
    @SerializedName("location") val location: List<Double>
)

class OSRMClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    
    suspend fun getRoute(
        start: LatLng,
        end: LatLng,
        profile: String = "driving"
    ): OSRMResponse? = withContext(Dispatchers.IO) {
        try {
            
            val url = "https://router.project-osrm.org/route/v1/$profile/" +
                    "${start.longitude},${start.latitude};" +
                    "${end.longitude},${end.latitude}?" +
                    "overview=full&geometries=polyline&steps=true"  

            Log.d("OSRMClient", "📍 Solicitando ruta a OSRM")
            Log.d("OSRMClient", "   Desde: (${start.latitude}, ${start.longitude})")
            Log.d("OSRMClient", "   Hasta: (${end.latitude}, ${end.longitude})")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "YaLlegoApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonString = response.body?.string()
                    Log.d("OSRMClient", "✅ Respuesta OSRM recibida")

                    val osrmResponse = gson.fromJson(jsonString, OSRMResponse::class.java)

                    if (osrmResponse.routes.isNotEmpty()) {
                        val route = osrmResponse.routes[0]
                        Log.d("OSRMClient", "📍 Ruta encontrada: ${route.distance}m, ${route.duration}s")
                        Log.d("OSRMClient", "📍 Geometry length: ${route.geometry.length}")

                        
                        val points = decodePolylineToLatLng(route.geometry)
                        Log.d("OSRMClient", "📍 Puntos decodificados: ${points.size}")
                        if (points.isNotEmpty()) {
                            Log.d("OSRMClient", "📍 Primera coordenada: (${points.first().latitude}, ${points.first().longitude})")
                            Log.d("OSRMClient", "📍 Última coordenada: (${points.last().latitude}, ${points.last().longitude})")
                        }
                    } else {
                        Log.w("OSRMClient", "⚠️ OSRM no encontró rutas")
                    }

                    osrmResponse
                } else {
                    Log.e("OSRMClient", "❌ Error OSRM: ${response.code} - ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("OSRMClient", "❌ Excepción en getRoute: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    
    fun decodePolylineToLatLng(encoded: String): List<LatLng> {
        val points = mutableListOf<LatLng>()

        if (encoded.isEmpty()) {
            Log.w("OSRMClient", "⚠️ Polyline vacío")
            return createFallbackRoute()
        }

        try {
            var index = 0
            val len = encoded.length
            var lat = 0
            var lng = 0

            Log.d("OSRMClient", "🔍 Decodificando polyline de $len caracteres")

            while (index < len) {
                
                var shift = 0
                var result = 0
                var b: Int
                do {
                    if (index >= len) {
                        Log.w("OSRMClient", "⚠️ Fin inesperado del polyline en latitud")
                        break
                    }
                    b = encoded[index++].code - 63
                    result = result or (b and 0x1f shl shift)
                    shift += 5
                } while (b >= 0x20 && index < len)

                val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                lat += dlat

                
                shift = 0
                result = 0
                do {
                    if (index >= len) {
                        Log.w("OSRMClient", "⚠️ Fin inesperado del polyline en longitud")
                        break
                    }
                    b = encoded[index++].code - 63
                    result = result or (b and 0x1f shl shift)
                    shift += 5
                } while (b >= 0x20 && index < len)

                val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                lng += dlng

                
                val latitude = lat * 1e-5
                val longitude = lng * 1e-5

                
                if (isValidCoordinate(latitude, longitude)) {
                    points.add(LatLng(latitude, longitude))
                } else {
                    Log.w("OSRMClient", "⚠️ Coordenada inválida filtrada: lat=$latitude, lng=$longitude")
                    
                    if (points.size == 0) {
                        
                        val altLatitude = lat * 1e-6
                        val altLongitude = lng * 1e-6
                        if (isValidCoordinate(altLatitude, altLongitude)) {
                            Log.d("OSRMClient", "🔁 Usando factor 1e-6 para coordenadas")
                            points.add(LatLng(altLatitude, altLongitude))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OSRMClient", "❌ Error crítico en decodePolyline: ${e.message}")
            e.printStackTrace()
            return createFallbackRoute()
        }

        Log.d("OSRMClient", "✅ Polyline decodificado: ${points.size} puntos")

        
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }

        if (validPoints.size < 2) {
            Log.w("OSRMClient", "⚠️ Pocos puntos válidos (${validPoints.size}), usando fallback")
            return createFallbackRoute()
        }

        
        Log.d("OSRMClient", "📍 Primera coordenada válida: (${validPoints.first().latitude}, ${validPoints.first().longitude})")
        Log.d("OSRMClient", "📍 Última coordenada válida: (${validPoints.last().latitude}, ${validPoints.last().longitude})")
        Log.d("OSRMClient", "📍 Rango lat: ${validPoints.minByOrNull { it.latitude }?.latitude} - ${validPoints.maxByOrNull { it.latitude }?.latitude}")
        Log.d("OSRMClient", "📍 Rango lng: ${validPoints.minByOrNull { it.longitude }?.longitude} - ${validPoints.maxByOrNull { it.longitude }?.longitude}")

        return validPoints
    }

    
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0 &&
                latitude != 0.0 && longitude != 0.0
    }

    
    private fun createFallbackRoute(): List<LatLng> {
        Log.w("OSRMClient", "🔄 Usando ruta fallback")
        return listOf(
            LatLng(4.5709, -74.2973), 
            LatLng(4.5710, -74.2974),
            LatLng(4.5715, -74.2980),
            LatLng(4.5720, -74.2990),
            LatLng(4.5981, -74.0758)  
        )
    }

    
    fun createRouteGeoJSON(encodedPolyline: String): String {
        val points = decodePolylineToLatLng(encodedPolyline)

        if (points.isEmpty()) {
            Log.e("OSRMClient", "❌ No hay puntos para crear GeoJSON")
            return createFallbackGeoJSON()
        }

        Log.d("OSRMClient", "📍 Creando GeoJSON con ${points.size} puntos")

        val coordinates = points.joinToString(",\n            ") { "[${it.longitude}, ${it.latitude}]" }

        val geoJSON = """
        {
            "type": "Feature",
            "properties": {
                "name": "Ruta navegación",
                "stroke": "#2196F3",
                "stroke-width": 6,
                "stroke-opacity": 0.8
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [
                    $coordinates
                ]
            }
        }
        """.trimIndent()

        Log.d("OSRMClient", "✅ GeoJSON creado exitosamente")
        return geoJSON
    }

    
    private fun createFallbackGeoJSON(): String {
        Log.w("OSRMClient", "🔄 Creando GeoJSON fallback")
        return """
        {
            "type": "Feature",
            "properties": {
                "name": "Ruta fallback",
                "stroke": "#FF0000",
                "stroke-width": 4,
                "stroke-opacity": 0.6
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [
                    [-74.2973, 4.5709],
                    [-74.2974, 4.5710],
                    [-74.2980, 4.5715],
                    [-74.2990, 4.5720],
                    [-74.0758, 4.5981]
                ]
            }
        }
        """.trimIndent()
    }

    
    fun debugPolyline(encoded: String) {
        Log.d("OSRMClient", "🔍 DEBUG POLYLINE:")
        Log.d("OSRMClient", "   Longitud: ${encoded.length} caracteres")
        Log.d("OSRMClient", "   Primeros 50 chars: ${encoded.take(50)}")

        val points = decodePolylineToLatLng(encoded)
        Log.d("OSRMClient", "   Puntos decodificados: ${points.size}")

        points.take(5).forEachIndexed { index, point ->
            Log.d("OSRMClient", "   Punto $index: (${point.latitude}, ${point.longitude})")
        }

        if (points.size > 5) {
            Log.d("OSRMClient", "   ...")
            points.takeLast(5).forEachIndexed { index, point ->
                val realIndex = points.size - 5 + index
                Log.d("OSRMClient", "   Punto $realIndex: (${point.latitude}, ${point.longitude})")
            }
        }
    }

    
    fun decodePolyline(encoded: String): List<GeoPoint> {
        val latLngPoints = decodePolylineToLatLng(encoded)
        return latLngPoints.map { GeoPoint(it.latitude, it.longitude) }
    }

    
    suspend fun getRoute(
        start: GeoPoint,
        end: GeoPoint,
        profile: String = "driving"
    ): OSRMResponse? = getRoute(
        LatLng(start.latitude, start.longitude),
        LatLng(end.latitude, end.longitude),
        profile
    )
}