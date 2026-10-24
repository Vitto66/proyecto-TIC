package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tipo de equipamiento (casco, chaleco salvavidas...). Lo define el administrador (CU-13);
 * en Supabase la tabla ya tiene datos. Uno inactivo no se ofrece al publicar.
 */
@Entity
@Table(name = "equipment_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
