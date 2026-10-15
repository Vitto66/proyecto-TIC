package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Perfil de prestador. Los campos de persona física (firstName, lastName,
 * documentNumber, address) y de persona jurídica (rut, companyName) se
 * completan según el {@link ProviderType}.
 */
@Entity
@Table(name = "providers")
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
    @Column(nullable = false, length = 20)
    private ProviderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProviderStatus status = ProviderStatus.PENDING;

    // Persona física
    @Column(length = 80)
    private String firstName;

    @Column(length = 80)
    private String lastName;

    @Column(unique = true, length = 20)
    private String documentNumber;

    // Persona jurídica
    @Column(unique = true, length = 20)
    private String rut;

    @Column(length = 150)
    private String companyName;

    // Ambos
    @Column(length = 200)
    private String address;
}
