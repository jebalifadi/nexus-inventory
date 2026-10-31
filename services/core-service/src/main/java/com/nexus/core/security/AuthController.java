package com.nexus.core.security;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login, and user management for the owner. */
@RestController
class AuthController {

    record LoginRequest(String username, String password) {
    }

    record LoginResponse(String token, String tokenType, Instant expiresAt) {
    }

    record NewUser(String username, String password, Role role) {
    }

    private final UserService users;

    AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/api/v1/auth/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        TokenService.Token token = users.login(request.username(), request.password());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt());
    }

    @PostMapping("/api/v1/users")
    @ResponseStatus(HttpStatus.CREATED)
    User createUser(@RequestBody NewUser request) {
        return users.createUser(request.username(), request.password(), request.role());
    }

    @GetMapping("/api/v1/users")
    List<User> listUsers() {
        return users.listUsers();
    }
}
