package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.ProviderReview;
import uy.edu.um.proytic2026.exception.ProviderAlreadyReviewedException;
import uy.edu.um.proytic2026.exception.ProviderNotFoundException;
import uy.edu.um.proytic2026.service.ProviderReviewService;

/**
 * Páginas del administrador para el CU-03 Aprobar o rechazar un prestador.
 * SecurityConfig ya restringe /admin/** al perfil ADMIN.
 */
@Controller
@RequestMapping("/admin/providers")
@RequiredArgsConstructor
public class AdminProviderController {

    static final String LIST_VIEW = "admin-providers";
    static final String DETAIL_VIEW = "admin-provider-detail";

    private final ProviderReviewService reviewService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Paso 1: lista de prestadores pendientes. */
    @GetMapping
    public String pending(Model model) {
        model.addAttribute("providers", reviewService.pendingProviders());
        return LIST_VIEW;
    }

    /** Paso 2: detalle para revisar los datos. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("provider", findOr404(id));
        return DETAIL_VIEW;
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        return resolve(id, true, redirect);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes redirect) {
        return resolve(id, false, redirect);
    }

    /** Pasos 3 y 4: se actualiza el estado, se avisa al prestador y se vuelve a la lista. */
    private String resolve(Long id, boolean approve, RedirectAttributes redirect) {
        try {
            ProviderReview result = approve ? reviewService.approve(id) : reviewService.reject(id);
            redirect.addFlashAttribute("notice", result.displayName()
                    + (approve ? " quedó aprobado. Ya puede registrar su emprendimiento."
                               : " quedó rechazado. No va a poder publicar actividades."));
        } catch (ProviderNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ProviderAlreadyReviewedException ex) {
            redirect.addFlashAttribute("error", ex.getMessage() + ". No se hizo ningún cambio.");
        }
        // Post/Redirect/Get: si se recarga la lista no se vuelve a enviar la decisión
        return "redirect:/admin/providers";
    }

    private ProviderReview findOr404(Long id) {
        try {
            return reviewService.detail(id);
        } catch (ProviderNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
