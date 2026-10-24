package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.ActivityDateView;
import uy.edu.um.proytic2026.dto.AddActivityDateRequest;
import uy.edu.um.proytic2026.dto.AddDateResult;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.ActivityNotPublishedException;
import uy.edu.um.proytic2026.exception.DuplicateDateException;
import uy.edu.um.proytic2026.exception.PastDateException;
import uy.edu.um.proytic2026.service.ActivityDateService;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Páginas del CU-09 Definir fechas, horarios y cupos de una actividad.
 * SecurityConfig ya restringe /provider/** al perfil PROVIDER; el servicio controla que la
 * actividad sea de este prestador (si no, 404).
 */
@Controller
@RequestMapping("/provider/activities/{activityId}/dates")
@RequiredArgsConstructor
public class ActivityDatesPageController {

    static final String VIEW = "activity-dates";

    private final ActivityDateService dateService;
    private final Clock clock;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Paso 1: las fechas de la actividad y el formulario para agregar una. */
    @GetMapping
    public String show(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId, Model model) {
        model.addAttribute("form", new AddActivityDateRequest());
        return view(user, activityId, model);
    }

    /** Pasos 2 a 4. */
    @PostMapping
    public String add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                      @Valid @ModelAttribute("form") AddActivityDateRequest form, BindingResult result,
                      Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return view(user, activityId, model);
        }
        AddDateResult outcome;
        try {
            outcome = dateService.addDate(user.userId(), activityId, form);
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (PastDateException ex) {
            // 3a. Fecha u hora ya pasadas → se rechaza y se informa
            result.rejectValue("date", "past", "la fecha y hora tienen que ser futuras");
            return view(user, activityId, model);
        } catch (DuplicateDateException ex) {
            result.rejectValue("time", "duplicate", "ya hay una fecha de esta actividad que empieza a esa hora");
            return view(user, activityId, model);
        } catch (ActivityNotPublishedException ex) {
            result.reject("inactive", ex.getMessage());
            return view(user, activityId, model);
        }

        if (outcome.needsConfirmation()) {
            // 3b. Superposición → se advierte y se pide confirmación
            model.addAttribute("overlaps", outcome.overlaps());
            return view(user, activityId, model);
        }
        ActivityDateView saved = outcome.saved();
        redirect.addFlashAttribute("added", saved.dayLabel() + ", " + saved.timeLabel());
        // Post/Redirect/Get: si recarga la página no se agrega de nuevo
        return "redirect:/provider/activities/" + activityId + "/dates";
    }

    private String view(AuthenticatedUser user, Long activityId, Model model) {
        try {
            model.addAttribute("schedule", dateService.schedule(user.userId(), activityId));
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Mínimo del selector de fecha: hoy en Uruguay (el servidor puede estar en otra zona)
        model.addAttribute("today", LocalDate.now(clock).toString());
        return VIEW;
    }
}
