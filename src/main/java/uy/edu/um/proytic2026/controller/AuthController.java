package uy.edu.um.proytic2026.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;
import uy.edu.um.proytic2026.dto.UserResponse;
import uy.edu.um.proytic2026.service.UserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** CU-01: POST /api/auth/register/tourist */
    @PostMapping("/register/tourist")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerTourist(@Valid @RequestBody RegisterTouristRequest request) {
        return userService.registerTourist(request);
    }
}
