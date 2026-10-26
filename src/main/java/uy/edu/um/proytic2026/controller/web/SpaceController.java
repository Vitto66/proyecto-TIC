package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.service.AuthService;
import uy.edu.um.proytic2026.service.BusinessService;
import uy.edu.um.proytic2026.service.ProviderReviewService;
import uy.edu.um.proytic2026.service.TypeProposalService;

/**
 * Espacio de cada perfil: a donde se llega después de iniciar sesión (CU-04).
 * Es un tablero con los accesos de cada perfil ("role" elige la foto y las tarjetas de space.html);
 * las funciones que faltan se suman con los demás casos de uso. Spring Security (SecurityConfig) garantiza que cada perfil solo entre al suyo.
 */
@Controller
@RequiredArgsConstructor
public class SpaceController {

    static final String SPACE_VIEW = "space";

    private final AuthService authService;
    private final ProviderReviewService providerReviewService;
    private final BusinessService businessService;
    private final TypeProposalService typeProposalService;

    @GetMapping("/tourist")
    public String tourist(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("title", "Tu espacio de turista");
        model.addAttribute("role", "tourist");
        model.addAttribute("intro", "Desde acá vas a explorar actividades, reservar y ver tus reservas. "
                + "Estas funciones se van sumando a medida que se terminan.");
        return space(user, model);
    }

    @GetMapping("/provider")
    public String provider(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("title", "Tu espacio de prestador");
        model.addAttribute("role", "provider");
        model.addAttribute("intro", "Cuando registres tu emprendimiento vas a poder publicar actividades "
                + "y ver las reservas que reciban.");
        // Un prestador PENDING entra, pero todavía no puede publicar (CU-02 / CU-03).
        // Uno APPROVED ve el aviso de que ya fue aprobado (CU-03 paso 4: notificación).
        ProviderStatus providerStatus = authService.providerStatus(user.userId()).orElse(null);
        boolean approved = providerStatus == ProviderStatus.APPROVED;
        // CU-07: el aprobado ve el acceso a su emprendimiento (registrarlo o verlo si ya lo hizo)
        boolean hasBusiness = approved && businessService.findForProvider(user.userId()).isPresent();
        model.addAttribute("pendingProvider", providerStatus == ProviderStatus.PENDING);
        model.addAttribute("approvedProvider", approved && !hasBusiness);
        model.addAttribute("businessAccess", approved);
        model.addAttribute("hasBusiness", hasBusiness);
        return space(user, model);
    }

    @GetMapping("/admin")
    public String admin(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("title", "Panel de administración");
        model.addAttribute("role", "admin");
        model.addAttribute("intro", "Desde acá vas a revisar los prestadores pendientes y mantener "
                + "los tipos de actividad y de equipamiento.");
        model.addAttribute("pendingProviders", providerReviewService.countPending());
        model.addAttribute("pendingProposals", typeProposalService.countPending());
        return space(user, model);
    }

    private String space(AuthenticatedUser user, Model model) {
        model.addAttribute("currentUser", user);
        return SPACE_VIEW;
    }
}
