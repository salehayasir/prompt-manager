package com.saleha.promptservice.controller;

import com.saleha.promptservice.dto.LoginRequest;
import com.saleha.promptservice.dto.LoginResponse;
import com.saleha.promptservice.exception.InvalidCredentialsException;
import com.saleha.promptservice.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;

    @Value("${auth.username}")
    private String authUsername;

    @Value("${auth.password}")
    private String authPassword;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        if (!authUsername.equals(request.getUsername())
                || !authPassword.equals(request.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(request.getUsername());

        return new LoginResponse(token, jwtService.getExpirationMs());
    }
}