package com.benchos.api.iam.internal.web;

import com.benchos.api.iam.internal.service.UserManagementService;
import com.benchos.api.iam.internal.web.dto.CreateUserRequest;
import com.benchos.api.iam.internal.web.dto.ResetUserPasswordRequest;
import com.benchos.api.iam.internal.web.dto.TechnicianOptionResponse;
import com.benchos.api.iam.internal.web.dto.UpdateUserCommissionRequest;
import com.benchos.api.iam.internal.web.dto.UpdateUserStatusRequest;
import com.benchos.api.iam.internal.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userManagementService.getCurrentUser();
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public List<TechnicianOptionResponse> listTechnicians() {
        return userManagementService.listTechnicianOptions();
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<UserResponse> listUsers() {
        return userManagementService.listUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse getUser(@PathVariable UUID id) {
        return userManagementService.getUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return userManagementService.updateUserStatus(id, request.active());
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse resetUserPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetUserPasswordRequest request
    ) {
        return userManagementService.resetUserPassword(id, request.newPassword());
    }

    @PatchMapping("/{id}/commission")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse updateUserCommission(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserCommissionRequest request
    ) {
        return userManagementService.updateUserCommission(id, request.commissionPercent());
    }
}
