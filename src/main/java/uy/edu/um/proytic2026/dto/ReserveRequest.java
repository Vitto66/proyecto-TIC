package uy.edu.um.proytic2026.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Datos del formulario del CU-14 Reservar una actividad: la fecha (paso 2), los participantes
 * (paso 3) y, si va más de una persona, los datos de cada acompañante.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveRequest {

    /** La instancia (fecha y horario) elegida. */
    @NotNull(message = "elegí la fecha y el horario")
    private Long instanceId;

    @NotNull(message = "indicá cuántas personas van")
    @Min(value = 1, message = "tiene que ir al menos 1 persona")
    @Max(value = 50, message = "para más de 50 personas, contactá al prestador")
    private Integer participants;

    /**
     * El turista reserva para otros (por ejemplo, sus hijos) y no participa: queda como adulto
     * responsable y todos los participantes son acompañantes.
     */
    private boolean responsibleOnly;

    /** Las personas que van además del turista (o todas, si él es solo el adulto responsable). */
    @Valid
    @Builder.Default
    private List<CompanionForm> companions = new ArrayList<>();

    /** Cuántos acompañantes corresponden a la cantidad de participantes. */
    public int expectedCompanions() {
        if (participants == null || participants < 1) return 0;
        int people = Math.min(participants, 50);
        return responsibleOnly ? people : people - 1;
    }

    /** El número de persona del primer acompañante: 2 si el turista va, 1 si es solo el responsable. */
    public int firstCompanionNumber() {
        return responsibleOnly ? 1 : 2;
    }

    public boolean companionsMatchParticipants() {
        return companions != null && companions.size() == expectedCompanions()
                && companions.stream().allMatch(Objects::nonNull);
    }

    /** Deja un lugar por acompañante: agrega vacíos o saca los que sobran (por ejemplo, si bajó la cantidad). */
    public void fitCompanions() {
        if (companions == null) companions = new ArrayList<>();
        int expected = expectedCompanions();
        while (companions.size() > expected) companions.remove(companions.size() - 1);
        while (companions.size() < expected) companions.add(new CompanionForm());
        companions.replaceAll(c -> c == null ? new CompanionForm() : c);
    }
}
