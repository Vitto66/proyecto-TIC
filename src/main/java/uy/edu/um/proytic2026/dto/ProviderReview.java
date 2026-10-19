package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;

/**
 * Datos de un prestador que el administrador revisa en CU-03. Se arma dentro de la
 * transacción, así las pantallas no dependen de relaciones LAZY (open-in-view está apagado).
 */
public record ProviderReview(
        Long id,
        PersonType personType,
        ProviderStatus status,
        String firstName,
        String lastName,
        String document,
        String rut,
        String legalName,
        String businessName,
        String address,
        String email,
        String phone) {

    public static ProviderReview from(Provider p) {
        return new ProviderReview(p.getId(), p.getPersonType(), p.getStatus(),
                p.getFirstName(), p.getLastName(), p.getDocument(),
                p.getRut(), p.getLegalName(), p.getBusinessName(),
                p.getAddress(), p.getUser().getEmail(), p.getUser().getPhone());
    }

    public boolean legal() {
        return personType == PersonType.LEGAL;
    }

    /** Nombre para mostrar: la persona o, si es una empresa, su razón social. */
    public String displayName() {
        return legal() ? legalName : firstName + " " + lastName;
    }

    /** "Cédula" o "RUT", según el tipo de persona. */
    public String identifierLabel() {
        return legal() ? "RUT" : "Cédula";
    }

    public String identifier() {
        return legal() ? rut : document;
    }

    public String personTypeLabel() {
        return legal() ? "Persona jurídica" : "Persona física";
    }

    public boolean pending() {
        return status == ProviderStatus.PENDING;
    }

    public String statusLabel() {
        return switch (status) {
            case PENDING -> "Pendiente";
            case APPROVED -> "Aprobado";
            case REJECTED -> "Rechazado";
        };
    }
}
