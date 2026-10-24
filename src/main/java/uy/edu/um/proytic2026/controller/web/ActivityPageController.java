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
import uy.edu.um.proytic2026.dto.ActivitySummary;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.PublishActivityRequest;
import uy.edu.um.proytic2026.exception.BusinessRequiredException;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.exception.InvalidSelectionException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.service.ActivityService;
import uy.edu.um.proytic2026.service.ActivityService.Access;

/**
 * Páginas del CU-08 Publicar una actividad: "Mis actividades" y el formulario.
 * SecurityConfig ya restringe /provider/** al perfil PROVIDER; acá se controla además que
 * esté APROBADO y que tenga su emprendimiento registrado.
 */
@Controller
@RequestMapping("/provider/activities")
@RequiredArgsConstructor
public class ActivityPageController {

    static final String LIST_VIEW = "activities";
    static final String FORM_VIEW = "activity-form";

    private final ActivityService activityService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Paso 1: "Mis actividades". */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        Access access = activityService.access(user.userId());
        model.addAttribute("access", access);
        if (access == Access.ALLOWED) {
            model.addAttribute("activities", activityService.myActivities(user.userId()));
        }
        return LIST_VIEW;
    }

    /** Paso 1: elige publicar una nueva. */
    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        if (activityService.access(user.userId()) != Access.ALLOWED) {
            return "redirect:/provider/activities";
        }
        model.addAttribute("form", activityService.formDefaults(user.userId()));
        return formView(model);
    }

    /** Pasos 2 a 7. */
    @PostMapping
    public String publish(@AuthenticationPrincipal AuthenticatedUser user,
                          @Valid @ModelAttribute("form") PublishActivityRequest form, BindingResult result,
                          Model model, RedirectAttributes redirect) {
        // 6a. Faltan datos obligatorios → se marcan los campos
        if (result.hasErrors()) {
            return formView(model);
        }
        ActivitySummary published;
        try {
            published = activityService.publish(user.userId(), form);
        } catch (ProviderNotApprovedException | BusinessRequiredException ex) {
            return "redirect:/provider/activities";
        } catch (InvalidLocationException ex) {
            result.rejectValue("localityId", "invalid", "elegí una localidad del departamento seleccionado");
            return formView(model);
        } catch (InvalidSelectionException ex) {
            if ("equipment".equals(ex.getField())) {
                result.reject("equipment", "Elegí equipamiento de la lista.");
            } else {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
            return formView(model);
        }
        // Post/Redirect/Get: si recarga la lista no se vuelve a publicar
        redirect.addFlashAttribute("published", published.name());
        return "redirect:/provider/activities";
    }

    private String formView(Model model) {
        model.addAttribute("options", activityService.formOptions());
        return FORM_VIEW;
    }
}
