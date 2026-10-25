package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.service.HomeService;

/**
 * Menú inicial de la plataforma. Es público: se ve sin tener cuenta. Muestra el mapa de
 * Uruguay con las actividades publicadas por departamento y los tipos de actividad.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    static final String HOME_VIEW = "home";

    private final HomeService homeService;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthenticatedUser current, Model model) {
        // Si hay sesión iniciada, el menú muestra el acceso a su espacio y el botón para salir
        model.addAttribute("currentUser", current);
        if (current != null) {
            model.addAttribute("spacePath", RoleHome.pathFor(current.role()));
        }
        model.addAttribute("overview", homeService.overview());
        return HOME_VIEW;
    }
}
