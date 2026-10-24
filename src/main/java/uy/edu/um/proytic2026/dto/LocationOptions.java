package uy.edu.um.proytic2026.dto;

import java.util.List;

/** Opciones de ubicación para los formularios: los departamentos y todas las localidades. */
public record LocationOptions(List<DepartmentOption> departments, List<LocalityOption> localities) {

    public record DepartmentOption(Integer id, String name) {
    }

    /** departmentId permite mostrar solo las localidades del departamento elegido. */
    public record LocalityOption(Integer id, Integer departmentId, String name) {
    }
}
