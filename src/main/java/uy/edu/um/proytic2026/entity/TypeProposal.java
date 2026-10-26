package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Propuesta de un tipo de actividad o de equipamiento nuevo, hecha por un prestador (CU-12)
 * y resuelta por un administrador (CU-13 2a). Tabla type_proposal, que ya existía en Supabase.
 */
@Entity
@Table(name = "type_proposal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TypeProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** provider_id referencia a provider(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    /** El administrador que la resolvió; null mientras está PENDING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private Admin resolvedBy;

    /** ACTIVITY o EQUIPMENT: los mismos valores que el CHECK de la tabla. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.PENDING;
}
