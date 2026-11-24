package com.benchos.api.iam.internal.web;

import com.benchos.api.iam.internal.service.AuthService;
import com.benchos.api.iam.internal.service.PasswordResetService;
import com.benchos.api.iam.internal.web.dto.AuthResponse;
import com.benchos.api.iam.internal.web.dto.ForgotPasswordRequest;
import com.benchos.api.iam.internal.web.dto.LoginRequest;
import com.benchos.api.iam.internal.web.dto.MessageResponse;
import com.benchos.api.iam.internal.web.dto.RegisterTenantRequest;
import com.benchos.api.iam.internal.web.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterTenantRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        return new MessageResponse("If an account exists for this email, a verification code has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.email(), request.code(), request.newPassword());
        return new MessageResponse("Password has been reset successfully.");
    }
}
