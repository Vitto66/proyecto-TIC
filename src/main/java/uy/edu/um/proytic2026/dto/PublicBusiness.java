package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.Business;

/**
 * Datos públicos de un emprendimiento para la API (CU-19). No incluye datos del prestador
 * (documento, RUT, correo de la cuenta): solo lo que el emprendimiento muestra en el portal.
 */
public record PublicBusiness(Long id, String name, String description, String address,
                             String phone, String email, String locality, String department) {

    /** Se arma dentro de la transacción: la localidad y el departamento son LAZY. */
    public static PublicBusiness from(Business b) {
        return new PublicBusiness(b.getId(), b.getName(), b.getDescription(), b.getAddress(),
                b.getPhone(), b.getEmail(),
                b.getLocality().getName(), b.getLocality().getDepartment().getName());
    }
}
