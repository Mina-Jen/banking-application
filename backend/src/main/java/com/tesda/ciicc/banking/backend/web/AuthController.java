package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.service.AuthService;
import com.tesda.ciicc.banking.backend.web.dto.LoginRequest;
import com.tesda.ciicc.banking.backend.web.dto.RefreshRequest;
import com.tesda.ciicc.banking.backend.web.dto.RegisterRequest;
import com.tesda.ciicc.banking.backend.web.dto.RegisterResponse;
import com.tesda.ciicc.banking.backend.web.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration, login, and token refresh")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Register a customer login",
            description = "Always creates a CUSTOMER. ADMIN logins are created by the operator at startup "
                    + "from the ADMIN_PASSWORD environment variable, never through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Login created"),
            @ApiResponse(responseCode = "409", description = "Username already taken"),
            @ApiResponse(responseCode = "422", description = "Invalid username or password format")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request)
            throws BankingException {
        CurrentUser user = auth.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.username(), user.role().name()));
    }

    @Operation(summary = "Log in and receive the token pair",
            description = "Returns an access token (send as 'Authorization: Bearer ...') and a long-lived "
                    + "refresh token for later. Repeated failed logins are rate limited with 429.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password"),
            @ApiResponse(responseCode = "403", description = "Account is disabled"),
            @ApiResponse(responseCode = "429", description = "Too many failed login attempts")
    })
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        return TokenResponse.from(auth.login(request.username(), request.password()));
    }

    @Operation(summary = "Exchange the refresh token for a new pair",
            description = "Call this when the access token expires. An access token sent here is rejected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair"),
            @ApiResponse(responseCode = "401", description = "Refresh token is missing, invalid, or expired")
    })
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest request) {
        return TokenResponse.from(auth.refresh(request.refreshToken()));
    }
}
