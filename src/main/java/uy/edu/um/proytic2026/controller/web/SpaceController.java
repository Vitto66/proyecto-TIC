package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.service.AuthService;

/**
 * Espacio de cada perfil: a donde se llega después de iniciar sesión (CU-04).
 * Por ahora son páginas de bienvenida; las funciones de cada perfil se suman con los demás
 * casos de uso. Spring Security (SecurityConfig) garantiza que cada perfil solo entre al suyo.
 */
@Controller
@RequiredArgsConstructor
public class SpaceController {

    static final String SPACE_VIEW = "space";

    private final AuthService authService;

    @GetMapping("/tourist")
    public String tourist(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("title", "Tu espacio de turista");
        model.addAttribute("intro", "Desde acá vas a explorar actividades, reservar y ver tus reservas. "
                + "Estas funciones se van sumando a medida que se terminan.");
        return space(user, model);
    }

    @GetMapping("/provider")
    public String provider(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("title", "Tu espacio de prestador");
        model.addAttribute("intro", "Cuando registres tu emprendimiento vas a poder publicar actividades "
                + "y ver las reservas que reciban.");
        // Un prestador PENDING entra, pero todavía no puede publicar (CU-02 / CU-03)
        boolean pending = authService.providerStatus(user.userId())
                .map(status -> status == ProviderStatus.PENDING)
                .orElse(false);
        model.addAttribute("pendingProvider", pending);
        return space(user, model);
    }

    @GetMapping("/admin")
    public String admin(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("title", "Panel de administración");
        model.addAttribute("intro", "Desde acá vas a revisar los prestadores pendientes y mantener "
                + "los tipos de actividad y de equipamiento.");
        return space(user, model);
    }

    private String space(AuthenticatedUser user, Model model) {
        model.addAttribute("currentUser", user);
        return SPACE_VIEW;
    }
}
