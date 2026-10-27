package uy.edu.um.proytic2026.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.proytic2026.dto.ActivityOfDay;
import uy.edu.um.proytic2026.dto.PublicBusiness;
import uy.edu.um.proytic2026.service.PublicApiService;

import java.time.LocalDate;
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

    /**
     * CU-20: GET /api/activities?date=2026-11-05&departmentId=3 (o localityId=10).
     * La fecha va en formato yyyy-MM-dd. Parámetros incorrectos → 400 indicando cuál.
     */
    @GetMapping("/activities")
    public List<ActivityOfDay> activitiesOfDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer localityId) {
        return publicApiService.activitiesOfDay(date, departmentId, localityId);
    }
}
