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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.ReservationView;
import uy.edu.um.proytic2026.dto.ReserveRequest;
import uy.edu.um.proytic2026.dto.TouristSummary;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.BookingClosedException;
import uy.edu.um.proytic2026.exception.DateNotAvailableException;
import uy.edu.um.proytic2026.exception.NotEnoughSeatsException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.service.ReservationService;

/**
 * Páginas del CU-14 Reservar una actividad. SecurityConfig restringe /tourist/** al perfil
 * TOURIST: sin sesión se pide iniciarla (1a) y LoginPageController devuelve al turista acá;
 * un prestador o administrador no puede reservar (1b).
 */
@Controller
@RequiredArgsConstructor
public class ReservationPageController {

    static final String FORM_VIEW = "reserve";
    static final String SUMMARY_VIEW = "reserve-summary";
    static final String RESERVATION_VIEW = "reservation";

    private final ReservationService reservationService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    @ModelAttribute("spacePath")
    String spacePath() {
        return "/tourist";
    }

    /** El turista que reserva: es la persona 1 y no carga sus datos de nuevo. */
    @ModelAttribute("me")
    TouristSummary me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return null;
        try {
            return reservationService.touristSummary(user.userId());
        } catch (ReservationNotFoundException ex) {
            return null;
        }
    }

    /** Pasos 1 a 3: elegir fecha y horario, y la cantidad de participantes. */
    @GetMapping("/tourist/activities/{activityId}/reserve")
    public String form(@PathVariable Long activityId, @RequestParam(required = false) Long instanceId, Model model) {
        ReserveRequest form = new ReserveRequest();
        form.setInstanceId(instanceId);
        form.setParticipants(1);
        model.addAttribute("form", form);
        return formView(activityId, model);
    }

    /** Pasos 4 y 5: verificar y mostrar el resumen con el precio total. */
    @PostMapping("/tourist/activities/{activityId}/reserve")
    public String summary(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                          @Valid @ModelAttribute("form") ReserveRequest form, BindingResult result, Model model) {
        checkCompanions(user, form, result);
        if (result.hasErrors()) {
            return formView(activityId, model);
        }
        try {
            model.addAttribute("quote", reservationService.quote(activityId, form));
        } catch (RuntimeException ex) {
            return handle(ex, activityId, result, model);
        }
        return SUMMARY_VIEW;
    }

    /** Paso 6: confirmar. Queda PENDIENTE y se avanza al pago (CU-15). */
    @PostMapping("/tourist/activities/{activityId}/reserve/confirm")
    public String confirm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                          @Valid @ModelAttribute("form") ReserveRequest form, BindingResult result, Model model) {
        checkCompanions(user, form, result);
        if (result.hasErrors()) {
            return formView(activityId, model);
        }
        ReservationView reservation;
        try {
            // Se verifica de nuevo: en el medio pueden haberse llenado los cupos o cerrado las reservas
            reservation = reservationService.reserve(user.userId(), activityId, form);
        } catch (RuntimeException ex) {
            return handle(ex, activityId, result, model);
        }
        // Post/Redirect/Get: si recarga la página no se crea otra reserva
        return "redirect:/tourist/reservations/" + reservation.id() + "?created";
    }

    /** "Cambiar" desde el resumen: vuelve al formulario con todo lo que había cargado. */
    @PostMapping("/tourist/activities/{activityId}/reserve/edit")
    public String edit(@PathVariable Long activityId, @ModelAttribute("form") ReserveRequest form, Model model) {
        form.fitCompanions();
        return formView(activityId, model);
    }

    /** La reserva creada, pendiente de pago. */
    @GetMapping("/tourist/reservations/{reservationId}")
    public String reservation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId,
                              Model model) {
        try {
            model.addAttribute("reservation", reservationService.reservation(user.userId(), reservationId));
        } catch (ReservationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return RESERVATION_VIEW;
    }

    /**
     * Paso 3: si van varias personas, tiene que haber datos para cada una y sin cédulas repetidas.
     * Los formatos (cédula, teléfono, obligatorios) ya los marcó @Valid.
     */
    private void checkCompanions(AuthenticatedUser user, ReserveRequest form, BindingResult result) {
        if (form.getParticipants() == null || result.hasFieldErrors("participants")) {
            form.fitCompanions();
            return;
        }
        if (!form.companionsMatchParticipants()) {
            // Por ejemplo, cambió la cantidad con JavaScript desactivado: se arma un bloque por persona
            form.fitCompanions();
            result.reject("companions", "completá los datos de cada persona que va");
            return;
        }
        for (int i : reservationService.repeatedNationalIds(user.userId(), form)) {
            result.rejectValue("companions[" + i + "].nationalId", "repeated", "esa cédula ya está en la reserva");
        }
    }

    /** Traduce los problemas del paso 4 a mensajes en el formulario. */
    private String handle(RuntimeException ex, Long activityId, BindingResult result, Model model) {
        if (ex instanceof ActivityNotFoundException) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } else if (ex instanceof NotEnoughSeatsException seats) {
            // 4a. Se informa la cantidad disponible y se ofrecen otras fechas (las del mismo formulario)
            result.rejectValue("participants", "seats", seats.getAvailable() == 0
                    ? "esa fecha ya no tiene lugares. Elegí otra"
                    : "para esa fecha quedan " + seats.getAvailable()
                    + (seats.getAvailable() == 1 ? " lugar" : " lugares") + ". Elegí menos personas u otra fecha");
        } else if (ex instanceof BookingClosedException) {
            // 4b. Ya no se admiten reservas para esa instancia
            result.rejectValue("instanceId", "closed", "ya no se admiten reservas para esa fecha. Elegí otra");
        } else if (ex instanceof DateNotAvailableException) {
            result.rejectValue("instanceId", "unavailable", "esa fecha no está disponible. Elegí otra");
        } else {
            throw ex;
        }
        return formView(activityId, model);
    }

    private String formView(Long activityId, Model model) {
        try {
            model.addAttribute("options", reservationService.options(activityId));
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return FORM_VIEW;
    }
}
