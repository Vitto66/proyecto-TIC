package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Actividad publicada por un emprendimiento (CU-08). Las fechas con cupos son instancias
 * aparte (CU-09). Una actividad dada de baja queda con active = false (CU-10).
 * <p>
 * La base controla con CHECK que la duración sea positiva y que el precio, la edad mínima y
 * los minutos de cierre de reservas no sean negativos.
 */
@Entity
@Table(name = "activity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locality_id", nullable = false)
    private Locality locality;

    @Column(nullable = false, length = 150)
    private String name;

    /** En Supabase la columna es TEXT. */
    @Column(length = 2000)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** Precio por persona, en pesos uruguayos. En Supabase la columna es NUMERIC. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "min_age", nullable = false)
    private Integer minAge;

    /** Cuántos minutos antes del inicio se cierran las reservas; 0 = hasta la hora de comienzo. */
    @Column(name = "booking_cutoff_minutes", nullable = false)
    private Integer bookingCutoffMinutes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Uno o más tipos (paso 3). Tabla intermedia activity_activity_type. */
    @ManyToMany
    @JoinTable(name = "activity_activity_type",
            joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "activity_type_id"))
    @Builder.Default
    private Set<ActivityType> types = new LinkedHashSet<>();

    /** Equipamiento y quién lo pone (paso 4). Tabla activity_equipment. */
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ActivityEquipment> equipment = new ArrayList<>();

    public void addEquipment(EquipmentType type, boolean providedByProvider) {
        equipment.add(new ActivityEquipment(this, type, providedByProvider));
    }
}
