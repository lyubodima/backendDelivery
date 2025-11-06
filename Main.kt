import com.fasterxml.jackson.databind.ObjectMapper

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.serialization.jackson.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import io.ktor.client.request.get
import io.ktor.client.statement.*


fun main() {

    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson()
        }
    }

    embeddedServer(Netty, port = 8080) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }

        routing {
            get("/api/v1/delivery-order-price") {
                val venueSlug = call.request.queryParameters["venue_slug"]
                val cartValue = call.request.queryParameters["cart_value"]?.toIntOrNull()
                val userLat = call.request.queryParameters["user_lat"]?.toDoubleOrNull()
                val userLon = call.request.queryParameters["user_lon"]?.toDoubleOrNull()

                if (venueSlug == null || cartValue == null || userLat == null || userLon == null) {
                    call.respondText("Missing or invalid query parameters", status = HttpStatusCode.BadRequest)
                    return@get
                }

                // Fetch static and dynamic information
                val staticInfo = fetchStaticInfo(httpClient, venueSlug)
                val dynamicInfo = fetchDynamicInfo(httpClient, venueSlug)

                if (staticInfo == null || dynamicInfo == null) {
                    call.respondText("Failed to fetch venue information", status = HttpStatusCode.InternalServerError)
                    return@get
                }

                // Safely extract fields from the nested map
                val venueRaw = staticInfo["venue_raw"] as? Map<String, Any>
                val location = venueRaw?.get("location") as? Map<String, Any>
                val coordinates = location?.get("coordinates") as? List<Double>

                val deliverySpecs = (dynamicInfo["venue_raw"] as? Map<String, Any>)?.get("delivery_specs") as? Map<String, Any>
                val orderMinimumNoSurcharge = deliverySpecs?.get("order_minimum_no_surcharge") as? Int

                val deliveryPricing = deliverySpecs?.get("delivery_pricing") as? Map<String, Any>
                val basePrice = deliveryPricing?.get("base_price") as? Int
                val distanceRanges = deliveryPricing?.get("distance_ranges") as? List<Map<String, Any>>

                // Validate extracted fields
                if (coordinates == null || orderMinimumNoSurcharge == null || basePrice == null || distanceRanges == null) {
                    call.respondText("Invalid response data", status = HttpStatusCode.InternalServerError)
                    return@get
                }

                // Assign validated fields to variables
                val venueCoordinates = coordinates
                /*
                val orderMinNoSurcharge = orderMinimumNoSurcharge
                val baseDeliveryPrice = basePrice
                val distanceRangeList = distanceRanges

                val venueCoordinates = staticInfo["venue_raw"]?.get("location")?.get("coordinates") as? List<Double>
                val orderMinimumNoSurcharge = dynamicInfo["venue_raw"]?.get("delivery_specs")?.get("order_minimum_no_surcharge") as? Int
                val basePrice = dynamicInfo["venue_raw"]?.get("delivery_specs")?.get("delivery_pricing")?.get("base_price") as? Int
                val distanceRanges = dynamicInfo["venue_raw"]?.get("delivery_specs")?.get("delivery_pricing")?.get("distance_ranges") as? List<Map<String, Any>>

                if (venueCoordinates == null || orderMinimumNoSurcharge == null || basePrice == null || distanceRanges == null) {
                    call.respondText("Invalid response data", status = HttpStatusCode.InternalServerError)
                    return@get
                }
                */
                // Calculate distance between venue and user
                val deliveryDistance = calculateDistance(
                    venueCoordinates[1], venueCoordinates[0], userLat, userLon
                ).toInt()

                // Calculate delivery fee
                val deliveryFee = calculateDeliveryFee(deliveryDistance, basePrice, distanceRanges)

                if (deliveryFee == null) {
                    call.respondText("Delivery not available for this distance", status = HttpStatusCode.BadRequest)
                    return@get
                }

                val smallOrderSurcharge = if (cartValue < orderMinimumNoSurcharge) 100 else 0

                val response = OrderResponse(
                    total_price = cartValue + deliveryFee + smallOrderSurcharge,
                    small_order_surcharge = smallOrderSurcharge,
                    cart_value = cartValue,
                    delivery = DeliveryDetails(
                        fee = deliveryFee,
                        distance = deliveryDistance
                    )
                )

                call.respond(response)
            }
        }
    }.start(wait = true)
}

// Function to fetch static venue information
suspend fun fetchStaticInfo(client: HttpClient, venueSlug: String): Map<String, Any>? {
    val url = "https://consumer-api.development.dev.woltapi.com/home-assignment-api/v1/venues/$venueSlug/static"
    return try {
        client.get(url).bodyAsText().let { responseBody ->
            ObjectMapper().readValue(responseBody, Map::class.java) as Map<String, Any>
        }
        //client.get<Map<String, Any>>(url)
    } catch (e: Exception) {
        println("Error fetching static info: ${e.message}")
        null
    }
}

// Function to fetch dynamic venue information
suspend fun fetchDynamicInfo(client: HttpClient, venueSlug: String): Map<String, Any>? {
    val url = "https://consumer-api.development.dev.woltapi.com/home-assignment-api/v1/venues/$venueSlug/dynamic"
    return try {
        client.get(url).bodyAsText().let { responseBody ->
            ObjectMapper().readValue(responseBody, Map::class.java) as Map<String, Any>
        }
        //client.get<Map<String, Any>>(url)
    } catch (e: Exception) {
        println("Error fetching dynamic info: ${e.message}")
        null
    }
}

// Function to calculate distance (Haversine formula)
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return earthRadius * c
}

// Function to calculate delivery fee
fun calculateDeliveryFee(distance: Int, basePrice: Int, distanceRanges: List<Map<String, Any>>): Int? {
    for (range in distanceRanges) {
        val min = (range["min"] as? Int) ?: 0
        val max = (range["max"] as? Int) ?: 0
        val a = (range["a"] as? Int) ?: 0
        val b = (range["b"] as? Int) ?: 0

        if (distance in min until max || (max == 0 && distance >= min)) {
            val distanceFee = (b * distance / 10.0).roundToInt()
            return basePrice + a + distanceFee
        }
    }
    return null // Delivery not available for this distance
}

// Data classes for JSON response
data class OrderResponse(
    val total_price: Int,
    val small_order_surcharge: Int,
    val cart_value: Int,
    val delivery: DeliveryDetails
)

data class DeliveryDetails(
    val fee: Int,
    val distance: Int
)