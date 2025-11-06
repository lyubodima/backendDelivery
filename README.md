# backendDelivery
Backend for food delivery


Documentation

This Ktor-based application provides an API endpoint to calculate the delivery price for a given order. It integrates static and dynamic data for delivery specifications and calculates fees based on the user's location, order value, and distance from the venue.



Table of Contents

1 Setup and Installation
2 API Documentation
3 Testing
4 Project Structure
5 Implementation Details


Setup and Installation

Prerequisites
JDK 11 or higher
Gradle 7.x or higher
Kotlin 1.9.x or higher


1. Setup and Installation

Unzip the file. Create a kotlin project in IntelliJ IDEA with gradle. Paste the content from gradle file into build.gradle.kts. Paste
the content from main to main.kt. Copy the DeliveryOrderPeiceApiTest.kt into the test/kotlin folder


2. API Documentation

Endpoint: /api/v1/delivery-order-price

Method: GET

Description: Calculates the total delivery price for an order, including surcharges based on the distance and order value.


Parameter	Type	Required	Description

venue_slug	String	Yes			The identifier for the venue.
cart_value	Int	Yes	The 		total value of the order in cents.
user_lat	Double	Yes			The latitude of the user.
user_lon	Double	Yes			The longitude of the user.


Response

Status Code: 200 OK

//json
{
    "total_price": 1800,
    "small_order_surcharge": 100,
    "cart_value": 1500,
    "delivery": {
        "fee": 200,
        "distance": 3000
    }
}

Status Code: 400 Bad Request

"Missing or invalid query parameters"

Status Code: 500 Internal Server Error

"Invalid response data"


3. Testing 

Running Tests


1 Navigate to the project directory.
2 Run all tests using Gradle:

//bash
./gradlew test

Test Cases

The project includes tests for the following scenarios:

1 Valid Parameters:

Ensures that the API calculates the correct delivery price when all query parameters are provided.

2 Missing Parameters:

Validates that the API returns a 400 Bad Request status for missing or invalid parameters.


Test Dependencies

Ensure the following dependencies are added to build.gradle.kts:

testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
testImplementation("io.ktor:ktor-server-tests-jvm:2.3.4")


Project Structure

For simplicity there are two files: Main.kt and DeliveryOrderPriceApiTest.kt that goes into test. And the gradle file.


Implementation Details

1 Static and Dynamic Data Fetching
	
	Static data is fetched from: https://consumer-api.development.dev.woltapi.com/home-assignment-api/v1/venues/{venue_slug}/static

	Dynamic data is fetched from: https://consumer-api.development.dev.woltapi.com/home-assignment-api/v1/venues/{venue_slug}/dynamic

2 Distance Calculation

	Uses the Haversine formula to calculate the distance (in meters) between the user and the venue.

3 Delivery Fee Calculation

	The fee is computed based on basePrice and distanceRanges returned in the dynamic data.

4 Small Order Surcharge

	A surcharge of 100 cents is applied if the cart value is below a certain threshold (orderMinimumNoSurcharge).


