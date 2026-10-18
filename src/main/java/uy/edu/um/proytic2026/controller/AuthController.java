package uy.edu.um.proytic2026.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.dto.RegisterProviderRequest;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;
import uy.edu.um.proytic2026.dto.UserResponse;
import uy.edu.um.proytic2026.service.UserService;
import uy.edu.um.proytic2026.validation.ProviderRegistrationValidator;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final ProviderRegistrationValidator providerRegistrationValidator;

    /** Suma las validaciones que dependen del tipo de persona al @Valid del CU-02. */
    @InitBinder("registerProviderRequest")
    void initProviderBinder(WebDataBinder binder) {
        binder.addValidators(providerRegistrationValidator);
    }

    /** CU-01: POST /api/auth/register/tourist */
    @PostMapping("/register/tourist")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerTourist(@Valid @RequestBody RegisterTouristRequest request) {
        return userService.registerTourist(request);
    }

    /** CU-02: POST /api/auth/register/provider. El prestador queda PENDING. */
    @PostMapping("/register/provider")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerProvider(@Valid @RequestBody RegisterProviderRequest request) {
        return userService.registerProvider(request);
    }
}
