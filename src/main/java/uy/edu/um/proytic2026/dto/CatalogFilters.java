package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.dto.ActivityFormOptions.Option;

import java.util.List;

/** Opciones de los filtros de la vidriera (CU-05): ubicaciones y tipos de actividad activos. */
public record CatalogFilters(LocationOptions locations, List<Option> activityTypes) {
}
