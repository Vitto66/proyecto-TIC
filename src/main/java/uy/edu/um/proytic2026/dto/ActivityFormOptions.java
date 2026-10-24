package uy.edu.um.proytic2026.dto;

import java.util.List;

/** Lo que ofrece el formulario del CU-08: ubicaciones y los tipos definidos por el administrador. */
public record ActivityFormOptions(LocationOptions locations, List<Option> activityTypes, List<Option> equipmentTypes) {

    public record Option(Integer id, String name) {
    }
}
