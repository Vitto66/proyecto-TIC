package uy.edu.um.proytic2026.dto;

/**
 * CU-10 2a y 2b: lo que pasa si se da de baja la actividad, para pedir confirmación.
 *
 * @param confirmedReservations reservas pagas en fechas futuras: se cancelan y se devuelve el 100%.
 * @param confirmedPeople       cuántas personas suman esas reservas.
 * @param pendingReservations   reservas pendientes de pago en fechas futuras: se cancelan.
 */
public record CancelPreview(Long activityId, String activityName, long confirmedReservations, long confirmedPeople,
                            long pendingReservations) {

    public boolean hasReservations() {
        return confirmedReservations + pendingReservations > 0;
    }

    public static String reservations(long count) {
        return count + (count == 1 ? " reserva" : " reservas");
    }

    public String confirmedLabel() {
        return reservations(confirmedReservations) + " paga" + (confirmedReservations == 1 ? "" : "s")
                + " (" + confirmedPeople + (confirmedPeople == 1 ? " persona" : " personas") + ")";
    }

    public String pendingLabel() {
        return reservations(pendingReservations) + " pendiente" + (pendingReservations == 1 ? "" : "s") + " de pago";
    }
}
