import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import io.ktor.client.statement.bodyAsText
import kotlin.test.assertEquals

class DeliveryOrderPriceApiTest {

    @Test
    fun `test delivery order price API with valid parameters`() = testApplication {
        application { // Sets up the server for testing
            main() // Calls the main function to set up routes
        }

        val venueSlug = "example-venue"
        val cartValue = 1500
        val userLat = 60.1695
        val userLon = 24.9354

        val response = client.get("/api/v1/delivery-order-price") {
            url {
                parameters.append("venue_slug", venueSlug)
                parameters.append("cart_value", cartValue.toString())
                parameters.append("user_lat", userLat.toString())
                parameters.append("user_lon", userLon.toString())
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val responseBody = response.bodyAsText()
        println(responseBody as String) // Debugging
    }

    @Test
    fun `test delivery order price API with missing parameters`() = testApplication {
        application {
            main()
        }

        val response = client.get("/api/v1/delivery-order-price")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val responseBody = response.bodyAsText()
        assertEquals("Missing or invalid query parameters", responseBody)
    }
}