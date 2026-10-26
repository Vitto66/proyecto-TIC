package uy.edu.um.proytic2026.service;

/**
 * CU-10: avisarle al turista que su reserva se canceló porque el prestador dio de baja la
 * actividad. Hoy el aviso es dentro de la plataforma (la reserva aparece "Cancelada" con el
 * estado de la devolución); esta interfaz es el punto donde se enchufa un correo.
 */
public interface TouristNotifier {

    /**
     * @param refundStatus qué pasó con el dinero: nada que devolver, devuelto o devolución pendiente.
     */
    void reservationCancelled(String email, String touristName, String activityName, Long reservationId,
                              PaymentService.RefundOutcome refundStatus);
}
