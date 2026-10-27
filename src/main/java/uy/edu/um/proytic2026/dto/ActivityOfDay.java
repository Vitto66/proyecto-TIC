package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una actividad con sus horarios en un día, para la API (CU-20).
 *
 * @param times los horarios de ese día, del más temprano al más tarde
 */
public record ActivityOfDay(Long id, String name, String business, String locality, String department,
                            BigDecimal price, int durationMinutes, int minAge, List<String> types,
                            List<Time> times) {

    /**
     * Un horario de la actividad.
     *
     * @param availableSeats lugares libres; null si la fecha no tiene límite de cupos (CU-09 2a)
     */
    public record Time(Long instanceId, LocalDateTime start, LocalDateTime end, Integer availableSeats) {
    }
}
