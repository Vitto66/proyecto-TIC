package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Un equipamiento de una actividad y quién lo pone (CU-08 paso 4): el prestador o el turista.
 * Tabla activity_equipment, con clave (activity_id, equipment_type_id).
 */
@Entity
@Table(name = "activity_equipment")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEquipment {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("activityId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @MapsId("equipmentTypeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_type_id")
    private EquipmentType equipmentType;

    /** true: lo provee el prestador. false: lo tiene que traer el turista. */
    @Column(name = "provided_by_provider", nullable = false)
    private boolean providedByProvider;

    public ActivityEquipment(Activity activity, EquipmentType equipmentType, boolean providedByProvider) {
        this.activity = activity;
        this.equipmentType = equipmentType;
        this.providedByProvider = providedByProvider;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "activity_id")
        private Long activityId;

        @Column(name = "equipment_type_id")
        private Integer equipmentTypeId;
    }
}
