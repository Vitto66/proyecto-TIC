package uy.edu.um.proytic2026.dto;

/** Un aviso sin leer en el espacio del turista. reservationId puede ser null. */
public record NotificationView(Long id, String message, Long reservationId, String createdLabel) {
}
