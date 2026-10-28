package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.proytic2026.dto.ActivityDetail;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.service.ActivityDetailService;
import uy.edu.um.proytic2026.service.ActivityPhotoService;

/**
 * CU-06 Consultar el detalle de una actividad. Es público, como la vidriera (RF-03).
 */
@Controller
@RequiredArgsConstructor
public class ActivityDetailController {

    static final String VIEW = "activity-detail";

    /** Qué ve el visitante en lugar del botón de reservar, según quién es. */
    public enum ReserveMode {
        /** 1a: sin sesión → tiene que iniciar sesión o registrarse como turista. */
        LOGIN,
        /** Ingresó como turista: el botón de reservar (CU-14). */
        TOURIST,
        /** CU-14 1b: ingresó como prestador o administrador → tiene que usar su perfil de turista. */
        OTHER_PROFILE
    }

    private final ActivityDetailService detailService;
    private final ActivityPhotoService photoService;

    @GetMapping("/activities/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser current, Model model) {
        ActivityDetail detail = detailService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("activity", detailService.withForecast(detail));
        model.addAttribute("photoUrl", photoService.url(id));

        model.addAttribute("currentUser", current);
        if (current != null) {
            model.addAttribute("spacePath", RoleHome.pathFor(current.role()));
        }
        model.addAttribute("reserveMode", current == null ? ReserveMode.LOGIN
                : current.role() == Role.TOURIST ? ReserveMode.TOURIST : ReserveMode.OTHER_PROFILE);
        return VIEW;
    }
}
