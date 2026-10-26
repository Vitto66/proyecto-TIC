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
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.ReviewRequest;
import uy.edu.um.proytic2026.exception.AlreadyReviewedException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.exception.ReservationNotReviewableException;
import uy.edu.um.proytic2026.service.ReservationService;
import uy.edu.um.proytic2026.service.ReviewService;

/**
 * CU-18 Valorar y comentar una actividad: "/tourist/reservations/{id}/review".
 * Pasos 2 y 3: el formulario con la puntuación y el comentario. Paso 4: se guarda y vuelve a la
 * reserva. 1a: si ya la valoró se le informa (una sola por reserva); 3a: sin puntuación se le pide.
 */
@Controller
@RequiredArgsConstructor
public class ReviewPageController {

    static final String REVIEW_VIEW = "review";

    private final ReservationService reservationService;
    private final ReviewService reviewService;

    @GetMapping("/tourist/reservations/{reservationId}/review")
    public String form(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId,
                       Model model) {
        model.addAttribute("form", new ReviewRequest());
        return page(user, reservationId, model);
    }

    @PostMapping("/tourist/reservations/{reservationId}/review")
    public String review(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId,
                         @Valid @ModelAttribute("form") ReviewRequest form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return page(user, reservationId, model); // 3a
        }
        try {
            reviewService.review(user.userId(), reservationId, form);
        } catch (AlreadyReviewedException | ReservationNotReviewableException ex) {
            return page(user, reservationId, model); // la página explica por qué no se puede
        } catch (ReservationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Post/Redirect/Get: si recarga, no se reenvía
        return "redirect:/tourist/reservations/" + reservationId + "?reviewed";
    }

    /** La reserva, la valoración que ya tenga y si todavía se puede valorar. */
    private String page(AuthenticatedUser user, Long reservationId, Model model) {
        try {
            model.addAttribute("reservation", reservationService.reservation(user.userId(), reservationId));
            model.addAttribute("myReview", reviewService.ofReservation(user.userId(), reservationId).orElse(null));
            model.addAttribute("canReview", reviewService.canReview(user.userId(), reservationId));
        } catch (ReservationNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("currentUser", user);
        model.addAttribute("spacePath", "/tourist");
        model.addAttribute("maxComment", ReviewRequest.MAX_COMMENT);
        return REVIEW_VIEW;
    }
}
