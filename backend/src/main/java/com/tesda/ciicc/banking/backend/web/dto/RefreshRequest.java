package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for refreshing an expired access token")
public record RefreshRequest(

        @Schema(description = "The refresh token received at login")
        String refreshToken) {
}
