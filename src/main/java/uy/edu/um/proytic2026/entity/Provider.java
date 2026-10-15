package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Perfil de prestador. Los campos de persona física (firstName, lastName, document)
 * y de persona jurídica (rut, legalName) se completan según el {@link PersonType};
 * la base lo controla con el CHECK ck_provider_person_data.
 * La dirección no va acá: es del emprendimiento (tabla business).
 */
@Entity
@Table(name = "provider")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 10)
    private PersonType personType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ProviderStatus status = ProviderStatus.PENDING;

    // Persona física
    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(unique = true, length = 20)
    private String document;

    // Persona jurídica
    @Column(unique = true, length = 20)
    private String rut;

    @Column(length = 200)
    private String legalName;
}
