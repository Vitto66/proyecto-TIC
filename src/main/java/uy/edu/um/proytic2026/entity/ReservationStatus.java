package uy.edu.um.proytic2026.entity;

/** Estados de una reserva. Coincide con el CHECK de reservation.status en Supabase. */
public enum ReservationStatus {
    /** Creada, a la espera del pago (CU-14). Todavía no ocupa cupos. */
    PENDING,
    /** Pago aprobado (CU-15). Ocupa cupos. */
    CONFIRMED,
    /** La instancia ya terminó (CU-16). Se puede valorar (CU-18). */
    FINISHED,
    /** Pasaron 24 horas sin pago o se cerraron las reservas de la instancia (CU-16). */
    EXPIRED,
    /** El prestador dio de baja la actividad (CU-10): si estaba paga, se devuelve el 100%. */
    CANCELLED
}
