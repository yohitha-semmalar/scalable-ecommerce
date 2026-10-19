package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.MessageResponse;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid bearer token is required");
        }
        String userId = authentication.getPrincipal().toString();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public MessageResponse logout() {
        // JWT is stateless; this endpoint does not revoke a token.
        // The frontend should discard its token. Server-side revocation needs a denylist/short expiry.
        return new MessageResponse("Logged out on client. Discard the access token.");
    }
}
