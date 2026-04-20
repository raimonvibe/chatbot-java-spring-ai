package com.prayer_chat.chatbot.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated GET request test to debug REST Assured NPE issue
 * This test runs without E2ETestBase to isolate the problem
 * 
 * NOTE: These tests are disabled because:
 * 1. We have confirmed the REST Assured GET NPE is a library bug
 * 2. We have migrated all E2E tests to WebTestClient
 * 3. These tests were only for debugging purposes
 * 
 * If you need to debug REST Assured issues in the future, re-enable these tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Isolated GET Test - Debug REST Assured NPE (DISABLED)")
@org.junit.jupiter.api.Disabled("REST Assured GET NPE confirmed as library bug. All tests migrated to WebTestClient.")
class IsolatedGetTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        // Complete reset before each test
        RestAssured.reset();
        RestAssured.requestSpecification = null;
        RestAssured.responseSpecification = null;
        
        // Verify port is available
        assertNotEquals(0, port, "Port should be set by Spring Boot!");
        System.out.println("=== Isolated GET Test Debug ===");
        System.out.println("Port: " + port);
        System.out.println("BaseURI before: " + RestAssured.baseURI);
        System.out.println("RequestSpec before: " + RestAssured.requestSpecification);
        
        // Set configuration
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.basePath = "/api";
        
        System.out.println("BaseURI after: " + RestAssured.baseURI);
        System.out.println("Port after: " + RestAssured.port);
        System.out.println("=================================");
    }

    @AfterEach
    void tearDown() {
        // Complete cleanup
        RestAssured.reset();
        RestAssured.requestSpecification = null;
        RestAssured.responseSpecification = null;
    }

    @Test
    @DisplayName("Minimal GET Test - No Authentication")
    void minimalGetTest() {
        String url = "http://localhost:" + port + "/api/chatbots";
        System.out.println("Testing URL: " + url);
        
        try {
            Response response = given()
                .log().all()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
            .when()
                .get(url);
            
            System.out.println("Response status: " + response.getStatusCode());
            System.out.println("Response received successfully!");
            
            // Should get 401 (unauthorized) since we're not authenticated
            // But at least we should get a response, not NPE
            assertNotNull(response, "Response should not be null");
            int statusCode = response.getStatusCode();
            assertTrue(statusCode >= 200 && statusCode < 600, 
                "Should get HTTP response, not NPE. Status: " + statusCode);
            
        } catch (NullPointerException e) {
            System.err.println("NPE occurred at: " + e.getStackTrace()[0]);
            e.printStackTrace();
            fail("NullPointerException occurred: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("GET Test with Relative Path")
    void getWithRelativePath() {
        try {
            System.out.println("Creating request with relative path...");
            Response response = given()
                .log().all()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/api")
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
            .when()
                .get("/chatbots");
            
            System.out.println("Response status: " + response.getStatusCode());
            assertNotNull(response, "Response should not be null");
            
        } catch (NullPointerException e) {
            System.err.println("NPE with relative path at: " + e.getStackTrace()[0]);
            e.printStackTrace();
            fail("NullPointerException occurred: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Capture Real Response Headers (Apache HttpClient)")
    void captureRealResponseHeaders() {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            
            ClassicHttpRequest request = org.apache.hc.core5.http.io.support.ClassicRequestBuilder.get()
                .setUri("http://localhost:" + port + "/api/chatbots")
                .addHeader("Accept", "application/json")
                .build();
            
            CloseableHttpResponse response = httpClient.execute(request);
            
            System.out.println("=== REAL RESPONSE (zonder REST Assured) ===");
            System.out.println("Status: " + response.getCode());
            
            Header contentTypeHeader = response.getHeader("Content-Type");
            System.out.println("Content-Type: " + (contentTypeHeader != null ? contentTypeHeader.getValue() : "MISSING"));
            
            System.out.println("All Headers:");
            for (Header header : response.getHeaders()) {
                System.out.println("  " + header.getName() + ": " + header.getValue());
            }
            
            // Lees body
            String body = EntityUtils.toString(response.getEntity());
            System.out.println("Body Length: " + body.length());
            System.out.println("Body Preview: " + (body.length() > 200 ? body.substring(0, 200) + "..." : body));
            System.out.println("==========================================");
            
            httpClient.close();
            
            // Test slaagt als we een response krijgen
            assertTrue(response.getCode() >= 200 && response.getCode() < 600, 
                "Should get HTTP response. Status: " + response.getCode());
            
        } catch (Exception e) {
            System.err.println("Error in Apache HttpClient test: " + e.getMessage());
            e.printStackTrace();
            fail("Apache HttpClient test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Compare POST vs GET Response Headers")
    void comparePostVsGetResponse() {
        System.out.println("=== Testing POST ===");
        try {
            // POST (werkt meestal)
            Response postResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"test\",\"description\":\"test\"}")
                .post("http://localhost:" + port + "/api/chatbots");
            
            System.out.println("POST Status: " + postResponse.getStatusCode());
            System.out.println("POST Content-Type: " + postResponse.getContentType());
            System.out.println("POST Headers: " + postResponse.getHeaders());
            System.out.println("POST Body Preview: " + 
                (postResponse.getBody().asString().length() > 200 ? 
                    postResponse.getBody().asString().substring(0, 200) + "..." : 
                    postResponse.getBody().asString()));
        } catch (Exception e) {
            System.out.println("POST Error: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== Testing GET ===");
        try {
            // GET (faalt met NPE)
            Response getResponse = given()
                .accept(ContentType.JSON)
                .get("http://localhost:" + port + "/api/chatbots");
            
            System.out.println("GET Status: " + getResponse.getStatusCode());
            System.out.println("GET Content-Type: " + getResponse.getContentType());
            System.out.println("GET Headers: " + getResponse.getHeaders());
            System.out.println("GET Body Preview: " + 
                (getResponse.getBody().asString().length() > 200 ? 
                    getResponse.getBody().asString().substring(0, 200) + "..." : 
                    getResponse.getBody().asString()));
        } catch (NullPointerException e) {
            System.out.println("GET NPE Error: " + e.getMessage());
            System.out.println("NPE Stack Trace:");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("GET Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Check if GET Returns Empty Body")
    void checkIfGetReturnsBody() {
        try {
            // Gebruik extract().asString() in plaats van response object
            String body = given()
                .accept(ContentType.JSON)
                .get("http://localhost:" + port + "/api/chatbots")
                .then()
                .extract().asString();  // Direct als String
            
            System.out.println("=== GET Body Analysis ===");
            System.out.println("GET Body Length: " + body.length());
            System.out.println("GET Body: " + (body.length() > 500 ? body.substring(0, 500) + "..." : body));
            System.out.println("Is Empty: " + body.isEmpty());
            System.out.println("Is Null: " + (body == null));
            System.out.println("=========================");
            
            assertNotNull(body, "Body should not be null");
            
        } catch (Exception e) {
            System.out.println("Failed to extract body: " + e.getMessage());
            e.printStackTrace();
            // Don't fail - this is just for debugging
        }
    }
}

