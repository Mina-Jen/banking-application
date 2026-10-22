package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.service.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token pair returned by login and refresh")
public record TokenResponse(

        @Schema(description = "Send on every API call as 'Authorization: Bearer <accessToken>'")
        String accessToken,

        @Schema(description = "Long-lived; send it to /api/auth/refresh when the access token expires")
        String refreshToken,

        @Schema(description = "Token type for the Authorization header", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "900")
        long expiresInSeconds) {

    public static TokenResponse from(AuthService.SessionTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(),
                "Bearer", tokens.accessTtlSeconds());
    }
}
