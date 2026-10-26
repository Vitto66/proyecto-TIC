package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.MyReviews;
import uy.edu.um.proytic2026.dto.ReviewRequest;
import uy.edu.um.proytic2026.dto.ReviewView;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.Reservation;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.entity.Review;
import uy.edu.um.proytic2026.exception.AlreadyReviewedException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.exception.ReservationNotReviewableException;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReviewRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CU-18 Valorar y comentar una actividad. El turista valora una reserva suya FINALIZADA, una sola
 * vez. El promedio de la actividad no se guarda: el detalle (CU-06), la vidriera (CU-05) y la API
 * lo calculan con las valoraciones, así que queda actualizado apenas se guarda una (paso 4).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    /** La valoración que ya hizo el turista de esa reserva, para mostrarla. */
    @Transactional(readOnly = true)
    public Optional<ReviewView> ofReservation(Long touristId, Long reservationId) {
        owned(touristId, reservationId);
        return reviewRepository.findByReservationId(reservationId)
                .map(r -> new ReviewView("Vos", r.getRating(), r.getComment(), r.getCreatedAt()));
    }

    /** Si todavía puede valorarla: es suya, está FINALIZADA y no tiene valoración. */
    @Transactional(readOnly = true)
    public boolean canReview(Long touristId, Long reservationId) {
        Reservation r = owned(touristId, reservationId);
        return r.getStatus() == ReservationStatus.FINISHED && !reviewRepository.existsByReservationId(reservationId);
    }

    /** Paso 4: registra la valoración. */
    @Transactional
    public void review(Long touristId, Long reservationId, ReviewRequest request) {
        Reservation r = owned(touristId, reservationId);
        if (r.getStatus() != ReservationStatus.FINISHED) {
            throw new ReservationNotReviewableException();
        }
        if (reviewRepository.existsByReservationId(reservationId)) {
            throw new AlreadyReviewedException(); // 1a
        }
        try {
            // saveAndFlush: si dos pestañas envían a la vez, la segunda choca con la restricción
            // única de review.reservation_id acá y no al terminar la transacción
            reviewRepository.saveAndFlush(Review.builder()
                    .reservation(r)
                    .rating(request.getRating())
                    .comment(request.cleanComment())
                    .createdAt(LocalDateTime.now(clock))
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyReviewedException();
        }
    }

    /**
     * CU-24 Consultar mis valoraciones: las reservas finalizadas que le falta valorar (paso 2) y las
     * valoraciones que ya hizo (paso 3). Es solo una consulta.
     */
    @Transactional(readOnly = true)
    public MyReviews mine(Long touristId) {
        List<MyReviews.ToReview> pending = reservationRepository.findFinishedWithoutReview(touristId).stream()
                .map(r -> {
                    Activity a = r.getInstance().getActivity();
                    return new MyReviews.ToReview(r.getId(), a.getName(), a.getBusiness().getName(),
                            r.getInstance().getStartAt());
                })
                .toList();
        List<MyReviews.Done> done = reviewRepository.findAllOfTourist(touristId).stream()
                .map(rv -> {
                    Reservation r = rv.getReservation();
                    Activity a = r.getInstance().getActivity();
                    return new MyReviews.Done(r.getId(), a.getId(), a.getName(), a.getBusiness().getName(),
                            a.isActive(), r.getInstance().getStartAt(),
                            new ReviewView("Vos", rv.getRating(), rv.getComment(), rv.getCreatedAt()));
                })
                .toList();
        return new MyReviews(pending, done);
    }

    /** La reserva, si es de este turista. Si no existe o es de otro, es lo mismo: 404. */
    private Reservation owned(Long touristId, Long reservationId) {
        return reservationRepository.findById(reservationId)
                .filter(r -> r.getTourist().getId().equals(touristId))
                .orElseThrow(ReservationNotFoundException::new);
    }
}
