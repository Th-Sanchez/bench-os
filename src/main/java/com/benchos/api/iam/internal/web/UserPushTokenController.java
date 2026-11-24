package com.benchos.api.iam.internal.web;

import com.benchos.api.iam.internal.service.PushTokenService;
import com.benchos.api.iam.internal.web.dto.PushTokenResponse;
import com.benchos.api.iam.internal.web.dto.RegisterPushTokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/push-tokens")
public class UserPushTokenController {

    private final PushTokenService pushTokenService;

    public UserPushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @PostMapping
    public PushTokenResponse register(@Valid @RequestBody RegisterPushTokenRequest request) {
        return pushTokenService.registerForCurrentUser(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@RequestParam("token") String expoPushToken) {
        pushTokenService.unregisterForCurrentUser(expoPushToken);
    }
}
