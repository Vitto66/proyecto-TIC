package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.Business;

/**
 * Datos de un emprendimiento para mostrar en pantalla. Se arma dentro de la transacción,
 * así las vistas no dependen de relaciones LAZY (open-in-view está apagado).
 */
public record BusinessSummary(Long id, String name, String description, String address,
                              String phone, String email, String localityName, String departmentName) {

    public static BusinessSummary from(Business b) {
        return new BusinessSummary(b.getId(), b.getName(), b.getDescription(), b.getAddress(),
                b.getPhone(), b.getEmail(),
                b.getLocality().getName(), b.getLocality().getDepartment().getName());
    }

    /** "Localidad, Departamento", o solo el departamento si se llaman igual (Montevideo, Montevideo). */
    public String location() {
        return localityName.equalsIgnoreCase(departmentName)
                ? departmentName
                : localityName + ", " + departmentName;
    }
}
