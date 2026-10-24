package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.BusinessSummary;
import uy.edu.um.proytic2026.dto.RegisterBusinessRequest;
import uy.edu.um.proytic2026.exception.BusinessAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.service.BusinessService;

import java.util.Optional;

/**
 * Páginas del CU-07 Registrar el emprendimiento. SecurityConfig ya restringe /provider/**
 * al perfil PROVIDER; acá se controla además que el prestador esté APROBADO.
 */
@Controller
@RequestMapping("/provider/business")
@RequiredArgsConstructor
public class BusinessPageController {

    static final String FORM_VIEW = "business-form";
    static final String BUSINESS_VIEW = "business";

    private final BusinessService businessService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    @GetMapping
    public String show(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        Long providerId = user.userId();

        // 1a. Ya tiene su emprendimiento registrado → se muestra con un aviso
        Optional<BusinessSummary> existing = businessService.findForProvider(providerId);
        if (existing.isPresent()) {
            model.addAttribute("business", existing.get());
            return BUSINESS_VIEW;
        }
        // Precondición: prestador aprobado
        if (!businessService.isApproved(providerId)) {
            model.addAttribute("notApproved", true);
            return BUSINESS_VIEW;
        }
        model.addAttribute("form", businessService.formDefaults(providerId));
        return formView(model);
    }

    @PostMapping
    public String submit(@AuthenticationPrincipal AuthenticatedUser user,
                         @Valid @ModelAttribute("form") RegisterBusinessRequest form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        // 3a. Faltan datos obligatorios → se marcan los campos
        if (result.hasErrors()) {
            return formView(model);
        }
        try {
            businessService.register(user.userId(), form);
        } catch (ProviderNotApprovedException ex) {
            model.addAttribute("notApproved", true);
            return BUSINESS_VIEW;
        } catch (BusinessAlreadyRegisteredException ex) {
            // 1a. Se vuelve a la página, que muestra el emprendimiento con el aviso
            return "redirect:/provider/business";
        } catch (InvalidLocationException ex) {
            result.rejectValue("localityId", "invalid", "elegí una localidad del departamento seleccionado");
            return formView(model);
        }
        // Post/Redirect/Get: si recarga la página no se vuelve a enviar el formulario
        redirect.addFlashAttribute("created", true);
        return "redirect:/provider/business";
    }

    private String formView(Model model) {
        model.addAttribute("locations", businessService.locationOptions());
        return FORM_VIEW;
    }
}
