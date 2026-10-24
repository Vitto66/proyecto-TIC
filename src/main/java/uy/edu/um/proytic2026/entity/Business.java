package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Emprendimiento de un prestador (CU-07). Cada prestador tiene uno solo: la base lo garantiza
 * con UNIQUE (provider_id). Las actividades (CU-08) se publican a nombre del emprendimiento.
 * <p>
 * La ubicación se guarda como localidad; el departamento sale de la localidad.
 */
@Entity
@Table(name = "business")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** provider_id referencia a provider(user_id), la clave del perfil de prestador. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locality_id", nullable = false)
    private Locality locality;

    @Column(nullable = false, length = 150)
    private String name;

    /** Descripción general de los servicios. En Supabase la columna es TEXT. */
    @Column(length = 2000)
    private String description;

    @Column(length = 200)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;
}
