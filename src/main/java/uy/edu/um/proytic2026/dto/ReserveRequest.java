package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos del formulario del CU-14 Reservar una actividad: la fecha (paso 2) y los participantes (paso 3). */
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
}
