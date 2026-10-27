package uy.edu.um.proytic2026.dto;

/**
 * Una actividad del top del mes de un departamento, para la API (CU-21).
 *
 * @param position       1, 2 o 3
 * @param averageRating  promedio de las valoraciones recibidas en el mes, redondeado a 2 decimales
 * @param reviewsInMonth cantidad de valoraciones recibidas en el mes
 */
public record TopActivity(int position, Long id, String name, String business, String locality,
                          String department, double averageRating, long reviewsInMonth) {
}
