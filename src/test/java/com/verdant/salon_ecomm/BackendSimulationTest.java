package com.verdant.salon_ecomm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.EmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class BackendSimulationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Use a nested class to override the EmailService to mock sending email
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public EmailService emailService() throws Exception {
            EmailService mockService = mock(EmailService.class);
            doNothing().when(mockService).sendVerificationEmail(anyString(), anyString());
            return mockService;
        }
    }

    @Test
    public void testCompleteBackendFlow() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String testEmail = "testuser_" + uniqueId + "@example.com";
        String testPassword = "SecurePassword123!";
        String testName = "Test User " + uniqueId;
        String testPhone = "+1234567890";

        System.out.println("========== STEP 1: SIGNING UP ACCOUNT ==========");
        RegisterUserDto signUpDto = new RegisterUserDto();
        signUpDto.setFullName(testName);
        signUpDto.setEmail(testEmail);
        signUpDto.setPhoneNumber(testPhone);
        signUpDto.setPassword(testPassword);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpDto)))
                .andExpect(status().isOk());

        System.out.println("Sign up request completed successfully for: " + testEmail);

        System.out.println("========== STEP 2: RETRIEVING VERIFICATION CODE FROM DATABASE ==========");
        User user = userRepository.findByEmail(testEmail)
                .orElseThrow(() -> new AssertionError("User was not saved in database"));
        String verificationCode = user.getVerificationCode();
        assertNotNull(verificationCode, "Verification code should not be null");
        System.out.println("Found verification code in database: " + verificationCode);

        System.out.println("========== STEP 3: VERIFYING ACCOUNT ==========");
        VerifyUserDto verifyDto = new VerifyUserDto();
        verifyDto.setEmail(testEmail);
        verifyDto.setVerificationCode(verificationCode);

        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyDto)))
                .andExpect(status().isOk());

        System.out.println("Account verified successfully.");

        System.out.println("========== STEP 4: LOGGING IN ==========");
        LogInUserDto loginDto = new LogInUserDto();
        loginDto.setEmail(testEmail);
        loginDto.setPassword(testPassword);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponseStr = loginResult.getResponse().getContentAsString();
        Map<?, ?> loginResponse = objectMapper.readValue(loginResponseStr, Map.class);
        String accessToken = (String) loginResponse.get("token");
        assertNotNull(accessToken, "Access token must be present in response");
        System.out.println("Logged in successfully. Access Token received: " + accessToken.substring(0, 15) + "...");

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie, "Refresh token cookie should be set");
        System.out.println("Refresh token cookie set: " + refreshCookie.getName() + "=" + refreshCookie.getValue().substring(0, 10) + "...");

        System.out.println("========== STEP 5: FETCHING PRODUCTS VIA GRAPHQL ==========");
        String productsQuery = "{\"query\": \"query { products(page: 1, pageSize: 5) { items { id name price inStock } } }\"}";
        MvcResult productsResult = mockMvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productsQuery))
                .andExpect(status().isOk())
                .andReturn();

        String productsResponse = productsResult.getResponse().getContentAsString();
        System.out.println("Products GraphQL Response: " + productsResponse);
        assertFalse(productsResponse.contains("\"errors\""), "Products query returned errors: " + productsResponse);

        System.out.println("========== STEP 6: FETCHING SERVICES VIA GRAPHQL ==========");
        String servicesQuery = "{\"query\": \"query { services(page: 1, pageSize: 5) { items { id name price durationInMinutes } } }\"}";
        MvcResult servicesResult = mockMvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicesQuery))
                .andExpect(status().isOk())
                .andReturn();

        String servicesResponse = servicesResult.getResponse().getContentAsString();
        System.out.println("Services GraphQL Response: " + servicesResponse);
        assertFalse(servicesResponse.contains("\"errors\""), "Services query returned errors: " + servicesResponse);

        System.out.println("========== STEP 7: LOGGING OUT ==========");
        mockMvc.perform(post("/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isOk());

        System.out.println("Logged out successfully.");
        System.out.println("========== ALL SIMULATION STEPS COMPLETED SUCCESSFULLY ==========");
    }
}
