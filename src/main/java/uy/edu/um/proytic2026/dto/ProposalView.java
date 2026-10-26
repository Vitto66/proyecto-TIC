package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.ProposalStatus;
import uy.edu.um.proytic2026.entity.TypeCategory;
import uy.edu.um.proytic2026.entity.TypeProposal;

/** Una propuesta de tipo para mostrar: al prestador que la hizo (CU-12) o al administrador (CU-13 2a). */
public record ProposalView(Long id, TypeCategory category, String name, String description,
                           ProposalStatus status, String providerName) {

    public static ProposalView from(TypeProposal p) {
        return new ProposalView(p.getId(), p.getCategory(), p.getName(), p.getDescription(), p.getStatus(),
                ProviderReview.from(p.getProvider()).displayName());
    }

    public String categoryLabel() {
        return category == TypeCategory.ACTIVITY ? "Tipo de actividad" : "Tipo de equipamiento";
    }

    public String statusLabel() {
        return switch (status) {
            case PENDING -> "Pendiente";
            case ACCEPTED -> "Aceptada";
            case REJECTED -> "Rechazada";
        };
    }

    public String statusClass() {
        return switch (status) {
            case PENDING -> "status-pending";
            case ACCEPTED -> "status-approved";
            case REJECTED -> "status-rejected";
        };
    }

    /** Lo que significa para el prestador (3a: se le notifica el resultado). */
    public String statusHint() {
        return switch (status) {
            case PENDING -> "Un administrador la va a revisar.";
            case ACCEPTED -> "Ya lo podés elegir al publicar una actividad.";
            case REJECTED -> "El administrador la rechazó: el tipo no se creó.";
        };
    }
}
