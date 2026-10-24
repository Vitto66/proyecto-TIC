package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Datos del formulario del CU-09 Definir fechas, horarios y cupos de una actividad. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddActivityDateRequest {

    /** El campo de fecha del navegador manda "2026-10-25". */
    @NotNull(message = "elegí la fecha")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    /** El campo de hora del navegador manda "10:30". */
    @NotNull(message = "elegí la hora de inicio")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime time;

    /** Cantidad máxima de participantes. Vacío = sin límite de cupos (2a). */
    @Min(value = 1, message = "tiene que ser de al menos 1 persona")
    @Max(value = 10000, message = "no puede superar las 10000 personas")
    private Integer maxCapacity;

    /** 3b: el prestador ya vio el aviso de superposición y confirmó que quiere agregarla igual. */
    private boolean confirmOverlap;

    public LocalDateTime startAt() {
        return LocalDateTime.of(date, time);
    }
}
