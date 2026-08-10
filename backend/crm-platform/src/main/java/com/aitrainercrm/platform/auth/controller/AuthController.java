package com.aitrainercrm.platform.auth.controller;

import com.aitrainercrm.platform.auth.dto.AuthResponse;
import com.aitrainercrm.platform.auth.dto.ChangePasswordRequest;
import com.aitrainercrm.platform.auth.dto.ForgotPasswordRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RefreshTokenRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.auth.dto.ResetPasswordRequest;
import com.aitrainercrm.platform.auth.dto.VerifyEmailRequest;
import com.aitrainercrm.platform.auth.service.AuthService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * All eight auth flows designed in AuthService, exposed as REST endpoints.
 * Registration/login/refresh/forgot-password/reset-password/verify-email
 * are listed as public in SecurityConfig.PUBLIC_ENDPOINTS; logout and
 * change-password require a valid access token like any other endpoint,
 * so they're not listed there.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, clientIp(httpRequest));
        return ApiResponse.ok(response, "Account created");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return ApiResponse.ok(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(
                request.refreshToken(), httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return ApiResponse.ok(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null, "Logged out");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        authService.forgotPassword(request.email(), clientIp(httpRequest));
        // Always the same response, whether or not the email is registered - see
        // AuthService#forgotPassword's javadoc on why this endpoint can't reveal that.
        return ApiResponse.ok(null, "If that email is registered, a reset link has been sent");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok(null, "Password has been reset");
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.ok(null, "Email verified");
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        authService.changePassword(principal.getId(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(null, "Password changed - please sign in again on other devices");
    }

    /**
     * X-Forwarded-For is trusted here because this service is expected to sit behind a
     * load balancer/reverse proxy (see infrastructure spec) that sets it; the leftmost
     * entry is the original client. Falls back to the direct socket address when absent
     * (e.g. local dev without a proxy in front).
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
