package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.ReservationPayment;

import java.util.List;
import java.util.Optional;

public interface ReservationPaymentRepository extends JpaRepository<ReservationPayment, Long> {

    /** El último intento de pago de una reserva. */
    Optional<ReservationPayment> findFirstByReservationIdOrderByIdDesc(Long reservationId);

    List<ReservationPayment> findByReservationIdOrderByIdAsc(Long reservationId);
}
