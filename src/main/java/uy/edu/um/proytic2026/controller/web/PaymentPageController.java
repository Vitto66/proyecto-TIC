package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.PayRequest;
import uy.edu.um.proytic2026.exception.NotEnoughSeatsException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.exception.ReservationNotPayableException;
import uy.edu.um.proytic2026.service.PaymentService;
import uy.edu.um.proytic2026.service.PaymentService.PayPage;
import uy.edu.um.proytic2026.service.PaymentService.PayResult;

/**
 * Páginas del CU-15 Pagar una reserva. Solo para turistas (SecurityConfig: /tourist/**).
 * El resultado se muestra después de redirigir (Post/Redirect/Get): si recarga la página no se
 * vuelve a cobrar.
 */
@Controller
@RequiredArgsConstructor
public class PaymentPageController {

    static final String PAY_VIEW = "pay";

    private final PaymentService paymentService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    @ModelAttribute("spacePath")
    String spacePath() {
        return "/tourist";
    }

    /** Paso 1: elegir con qué tarjeta pagar. */
    @GetMapping("/tourist/reservations/{reservationId}/pay")
    public String form(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId,
                       Model model, RedirectAttributes redirect) {
        PayPage page = page(user, reservationId);
        if (!page.reservation().payable()) {
            if (!page.reservation().confirmed()) {
                flash(redirect, "warning", "Esta reserva ya no se puede pagar.");
            }
            return redirectToReservation(reservationId);
        }
        model.addAttribute("page", page);
        return PAY_VIEW;
    }

    /** Pasos 2 a 6: verificar, cobrar con el servicio externo y confirmar. */
    @PostMapping("/tourist/reservations/{reservationId}/pay")
    public String pay(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId,
                      @ModelAttribute PayRequest request, RedirectAttributes redirect) {
        PayPage page = page(user, reservationId);
        boolean complete = page.cardForm()
                ? notBlank(request.getToken()) && notBlank(request.getPaymentMethodId())
                : notBlank(request.getCardholderName());
        if (!complete) {
            flash(redirect, "danger", "Completá los datos de la tarjeta.");
            return "redirect:/tourist/reservations/" + reservationId + "/pay";
        }

        PayResult result;
        try {
            result = paymentService.pay(user.userId(), reservationId, request);
        } catch (ReservationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ReservationNotPayableException ex) {
            flash(redirect, "warning", switch (ex.getReason()) {
                case EXPIRED -> "Pasaron las 24 horas para pagar esta reserva, así que venció. No se cobró nada.";
                case BOOKING_CLOSED -> "Ya no se admiten reservas para esa fecha. No se cobró nada.";
                case NOT_PENDING -> "Esta reserva ya no se puede pagar.";
            });
            return redirectToReservation(reservationId);
        } catch (NotEnoughSeatsException ex) {
            // 2a. Otro turista ocupó los lugares mientras la reserva estaba pendiente
            flash(redirect, "warning", "Ya no hay lugares suficientes para esa fecha"
                    + (ex.getAvailable() > 0 ? " (quedan " + ex.getAvailable() + ")" : "")
                    + ". No se cobró nada.");
            return redirectToReservation(reservationId);
        }

        return switch (result.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> {
                flash(redirect, "success", result.message());
                yield redirectToReservation(reservationId);
            }
            case IN_PROCESS, REFUNDED_NO_SEATS -> {
                flash(redirect, "warning", result.message());
                yield redirectToReservation(reservationId);
            }
            // 4a y 4b: sigue pendiente y puede reintentar
            case REJECTED, UNAVAILABLE -> {
                flash(redirect, "danger", result.message());
                yield "redirect:/tourist/reservations/" + reservationId + "/pay";
            }
        };
    }

    /** Un pago que quedó en revisión: se consulta si el servicio ya lo aprobó. */
    @PostMapping("/tourist/reservations/{reservationId}/pay/refresh")
    public String refresh(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId,
                          RedirectAttributes redirect) {
        PayResult result;
        try {
            result = paymentService.refresh(user.userId(), reservationId);
        } catch (ReservationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        flash(redirect, switch (result.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> "success";
            case REJECTED -> "danger";
            default -> "warning";
        }, result.message());
        return redirectToReservation(reservationId);
    }

    private PayPage page(AuthenticatedUser user, Long reservationId) {
        try {
            return paymentService.page(user.userId(), reservationId);
        } catch (ReservationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static String redirectToReservation(Long reservationId) {
        return "redirect:/tourist/reservations/" + reservationId;
    }

    private static void flash(RedirectAttributes redirect, String level, String message) {
        redirect.addFlashAttribute("paymentLevel", level);
        redirect.addFlashAttribute("paymentMessage", message);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
