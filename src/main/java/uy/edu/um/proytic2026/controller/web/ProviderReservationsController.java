package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.exception.InstanceNotFoundException;
import uy.edu.um.proytic2026.service.ActivityService;
import uy.edu.um.proytic2026.service.ProviderReservationService;

/**
 * Páginas del CU-11 Consultar las reservas de sus actividades.
 * SecurityConfig ya restringe /provider/** al perfil PROVIDER; el servicio controla que la
 * fecha sea de una actividad de este prestador (si no, 404).
 */
@Controller
@RequestMapping("/provider/reservations")
@RequiredArgsConstructor
public class ProviderReservationsController {

    static final String LIST_VIEW = "provider-reservations";
    static final String DETAIL_VIEW = "provider-reservation-detail";

    private final ProviderReservationService reservationService;
    private final ActivityService activityService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Pasos 1 y 2: las fechas con sus cupos y cuántas reservas tienen. */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        ActivityService.Access access = activityService.access(user.userId());
        model.addAttribute("access", access);
        if (access == ActivityService.Access.ALLOWED) {
            model.addAttribute("overview", reservationService.overview(user.userId()));
        }
        return LIST_VIEW;
    }

    /** Pasos 3 y 4: los turistas anotados en una fecha. */
    @GetMapping("/{instanceId}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long instanceId, Model model) {
        try {
            model.addAttribute("detail", reservationService.instance(user.userId(), instanceId));
        } catch (InstanceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return DETAIL_VIEW;
    }
}
