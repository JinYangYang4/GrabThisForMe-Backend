package com.study.grabthisforme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.grabthisforme.service.AuthService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthHttpFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;

    @Test
    void registrationThenLoginCanImmediatelyReadTheAuthenticatedProfile() throws Exception {
        String account="http_"+UUID.randomUUID();
        var registration=mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("accountName",account,"password","Testpass123","displayName","HTTP Tester"))))
            .andExpect(status().isOk()).andReturn();
        JsonNode registered=json.readTree(registration.getResponse().getContentAsString()).path("data");
        long userId=registered.path("user").path("id").asLong();
        var login=mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier",account,"password","Testpass123"))))
            .andExpect(status().isOk()).andReturn();
        String token=json.readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(userId));
        mvc.perform(get("/api/auth/me/").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(userId));
        // The previous request must not leave an identity on the reused servlet thread.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsMissingInvalidAndRevokedTokens() throws Exception {
        var account=auth.register("revoked_"+UUID.randomUUID(),"Testpass123","Tester",null,null);
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+account.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(account.user().id()));
        auth.logout(account.user().id());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+account.token()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40109));
    }

    @Test
    void passwordRecoveryRemainsAccessibleWithoutASession() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier","someone","phone","13900000000"))))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(50311));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("challengeId",UUID.randomUUID().toString(),"code","123456","newPassword","Changed123"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40081));
    }

    @Test
    void refreshIsAnonymousRotatesBothTokensAndDetectsReplay() throws Exception {
        var account = auth.register(
            "refresh_http_" + UUID.randomUUID(), "Testpass123", "Refresh Tester", null, null, "Phone A"
        );
        var refreshed = mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", account.refreshToken()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(account.sessionId()))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn();
        String nextAccessToken = json.readTree(refreshed.getResponse().getContentAsString())
            .path("data").path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nextAccessToken))
            .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", account.refreshToken()))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40112));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nextAccessToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40112));
    }
}
