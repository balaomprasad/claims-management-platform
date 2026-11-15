package com.claimsphere.user.web;

import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends IntegrationTestSupport {

    @Test
    void registerIgnoresSuppliedRoleAndLoginReturnsUsableToken() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.claimsphere.local";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD,
                                "fullName", "New User", "market", "INDIA", "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CLAIMANT"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email", "password", "short", "fullName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        TestUser user = createUser(Role.CLAIMANT);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminCanCreateUsersWithRoles() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser admin = createUser(Role.ADMIN);
        Map<String, String> request = Map.of("email", "adj-" + UUID.randomUUID() + "@test.claimsphere.local",
                "password", PASSWORD, "fullName", "Officer", "role", "CLAIMS_OFFICER");

        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMS_OFFICER"));
    }
}
