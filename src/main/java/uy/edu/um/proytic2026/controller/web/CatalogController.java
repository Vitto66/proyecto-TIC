package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.proytic2026.dto.ActivitySearch;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.service.CatalogService;

/**
 * CU-05 Explorar actividades publicadas: la vidriera pública. No hace falta cuenta (RF-03);
 * SecurityConfig deja /activities abierto.
 */
@Controller
@RequiredArgsConstructor
public class CatalogController {

    static final String VIEW = "catalog";

    private final CatalogService catalogService;

    @GetMapping("/activities")
    public String explore(@ModelAttribute("search") ActivitySearch search, BindingResult binding,
                          @AuthenticationPrincipal AuthenticatedUser current, Model model) {
        // Un filtro con un valor que no se entiende (texto en el precio, por ejemplo) queda vacío
        // y se avisa, pero el resto de los filtros se aplica igual.
        model.addAttribute("invalidFilters", binding.hasErrors());
        model.addAttribute("result", catalogService.search(search));
        model.addAttribute("filters", catalogService.filters());
        model.addAttribute("currentUser", current);
        if (current != null) {
            model.addAttribute("spacePath", RoleHome.pathFor(current.role()));
        }
        return VIEW;
    }
}
