package uy.edu.um.proytic2026.dto;

import java.util.List;

/** CU-11 pasos 3 y 4: una fecha elegida y sus reservas. */
public record InstanceReservations(Long activityId, String activityName, ActivityDateView date,
                                   List<ProviderReservationRow> reservations) {

    /** Personas con lugar asegurado: las de reservas confirmadas o finalizadas. */
    public long confirmedPeople() {
        return reservations.stream().filter(ProviderReservationRow::takesSeats)
                .mapToLong(ProviderReservationRow::participants).sum();
    }

    /** Personas de reservas que todavía no se pagaron: no ocupan lugar. */
    public long pendingPeople() {
        return reservations.stream()
                .filter(r -> r.status() == uy.edu.um.proytic2026.entity.ReservationStatus.PENDING)
                .mapToLong(ProviderReservationRow::participants).sum();
    }
}
