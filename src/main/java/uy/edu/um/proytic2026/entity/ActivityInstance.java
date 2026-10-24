package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Una fecha y hora en que se realiza una actividad, con su cupo (CU-09). Es lo que reserva el
 * turista (CU-14). La base no permite dos instancias de la misma actividad que empiecen a la
 * misma hora: UNIQUE (activity_id, start_at).
 * <p>
 * start_at es TIMESTAMP sin zona horaria: se guarda y se lee como hora de Uruguay.
 */
@Entity
@Table(name = "activity_instance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** Cantidad máxima de participantes. null = sin límite de cupos (CU-09 2a). */
    @Column(name = "max_capacity")
    private Integer maxCapacity;
}
