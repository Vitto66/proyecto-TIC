package uy.edu.um.proytic2026.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.service.PublicApiService;

import java.util.List;

/**
 * API REST para otras plataformas (RF-10). Es pública y de solo lectura:
 * SecurityConfig ya deja pasar /api/** sin sesión.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicApiController {

    private final PublicApiService publicApiService;

    /** CU-19: GET /api/businesses → 200 con la lista (vacía si no hay emprendimientos). */
    @GetMapping("/businesses")
    public List<PublicBusiness> listBusinesses() {
        return publicApiService.listBusinesses();
    }
}
