package uy.edu.um.proytic2026.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filtros de la vidriera pública (CU-05 paso 3). Todos son opcionales: llegan como parámetros de
 * la URL (/activities?departmentId=3&keyword=kayak), así una búsqueda se puede compartir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySearch {

    /** Busca en el nombre y la descripción de la actividad y en el nombre del emprendimiento. */
    private String keyword;

    private Integer departmentId;

    private Integer localityId;

    private Integer typeId;

    /** Actividades con al menos una fecha ese día que todavía tenga lugar. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** Calificación promedio mínima, de 1 a 5. */
    private Integer minRating;

    /** true si no se aplicó ningún filtro (3a: se muestran todas, de mejor a peor calificadas). */
    public boolean isEmpty() {
        return (keyword == null || keyword.isBlank()) && departmentId == null && localityId == null
                && typeId == null && date == null && minPrice == null && maxPrice == null && minRating == null;
    }
}
