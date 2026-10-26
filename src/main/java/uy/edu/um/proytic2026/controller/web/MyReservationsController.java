package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.service.MyReservationsService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * CU-17 Consultar mis reservas: "/tourist/reservations" con el calendario del mes.
 * Parámetros opcionales: month (2026-11), day (2026-11-01) y type (id del tipo de actividad).
 * Un valor que no se entiende se ignora y se muestra lo de siempre.
 */
@Controller
@RequiredArgsConstructor
public class MyReservationsController {

    private final MyReservationsService myReservationsService;

    @GetMapping("/tourist/reservations")
    public String myReservations(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) String month,
                                 @RequestParam(required = false) String day,
                                 @RequestParam(required = false) String type,
                                 Model model) {
        model.addAttribute("currentUser", user);
        model.addAttribute("spacePath", "/tourist");
        model.addAttribute("page", myReservationsService.overview(user.userId(), parseMonth(month), parseDay(day),
                parseType(type)));
        return "my-reservations";
    }

    private static YearMonth parseMonth(String value) {
        try {
            return value == null || value.isBlank() ? null : YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDate parseDay(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Integer parseType(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
