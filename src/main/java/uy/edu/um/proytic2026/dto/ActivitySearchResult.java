package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una actividad encontrada por palabra clave, para la API (CU-23).
 *
 * @param averageRating promedio de todas sus valoraciones, redondeado a 2 decimales; null si no tiene
 */
public record ActivitySearchResult(Long id, String name, String description, String business,
                                   String locality, String department, List<String> types,
                                   BigDecimal price, int durationMinutes, int minAge,
                                   Double averageRating, long reviewCount) {
}
