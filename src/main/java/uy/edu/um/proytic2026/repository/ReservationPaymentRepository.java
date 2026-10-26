package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.PaymentStatus;
import uy.edu.um.proytic2026.entity.ReservationStatus;
import uy.edu.um.proytic2026.entity.ReservationPayment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationPaymentRepository extends JpaRepository<ReservationPayment, Long> {

    /** El último intento de pago de una reserva. */
    Optional<ReservationPayment> findFirstByReservationIdOrderByIdDesc(Long reservationId);

    List<ReservationPayment> findByReservationIdOrderByIdAsc(Long reservationId);

    /**
     * CU-10: reservas canceladas de una actividad que estaban pagas y todavía no se pudo devolver
     * el dinero (el servicio de pagos no respondió). Se reintentan desde "Mis actividades".
     */
    @Query("""
            select distinct p.reservation.id from ReservationPayment p
            where p.reservation.instance.activity.id = :activityId
              and p.reservation.status = :cancelled and p.status in :paid
            """)
    List<Long> reservationsWithPendingRefund(@Param("activityId") Long activityId,
                                             @Param("cancelled") ReservationStatus cancelled,
                                             @Param("paid") Collection<PaymentStatus> paid);

    /** Lo mismo, contado por actividad, para todas las actividades de un prestador. */
    @Query("""
            select p.reservation.instance.activity.id, count(distinct p.reservation.id) from ReservationPayment p
            where p.reservation.instance.activity.business.provider.id = :providerId
              and p.reservation.status = :cancelled and p.status in :paid
            group by p.reservation.instance.activity.id
            """)
    List<Object[]> countPendingRefundsByActivity(@Param("providerId") Long providerId,
                                                 @Param("cancelled") ReservationStatus cancelled,
                                                 @Param("paid") Collection<PaymentStatus> paid);
}
