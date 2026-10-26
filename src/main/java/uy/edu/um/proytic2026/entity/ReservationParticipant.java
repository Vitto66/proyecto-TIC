package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un acompañante de una reserva (CU-14 paso 3): cuando van varias personas, el turista carga
 * nombre, apellido, cédula y teléfono de cada una. El turista que reserva es la persona 1 y ya
 * está identificado por su cuenta, así que acá se guardan las personas 2 en adelante.
 */
@Entity
@Table(name = "reservation_participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reservation_id", "person_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    /** Número de persona dentro de la reserva: 2, 3, ... (la 1 es el turista). */
    @Column(name = "person_number", nullable = false)
    private Integer position;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Cédula de identidad, sin puntos ni guion. */
    @Column(name = "national_id", nullable = false, length = 8)
    private String nationalId;

    @Column(nullable = false, length = 30)
    private String phone;
}
