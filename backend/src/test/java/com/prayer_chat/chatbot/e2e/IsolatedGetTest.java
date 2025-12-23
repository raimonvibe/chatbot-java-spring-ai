package com.prayer_chat.chatbot.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Isolated GET Test - Debug REST Assured NPE")
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
}

