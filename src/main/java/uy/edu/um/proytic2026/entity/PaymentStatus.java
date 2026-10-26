package uy.edu.um.proytic2026.entity;

/** Resultado de un intento de pago con el servicio externo (CU-15). */
public enum PaymentStatus {
    /** El servicio aprobó el cobro: la reserva se confirma. */
    APPROVED,
    /** El servicio rechazó la tarjeta (4a): no se cobró y se puede reintentar. */
    REJECTED,
    /** El servicio todavía lo está revisando: no se confirma hasta que lo apruebe. */
    IN_PROCESS,
    /** El servicio no respondió (4b): no se confirma y se puede reintentar más tarde. */
    UNAVAILABLE,
    /** Se cobró pero se devolvió el dinero (por ejemplo, porque mientras tanto se llenaron los cupos). */
    REFUNDED
}
