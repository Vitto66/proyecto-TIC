package uy.edu.um.proytic2026.dto;

import java.util.List;

/**
 * Resultado de explorar las actividades (CU-05).
 *
 * @param anyPublished false si no hay ninguna actividad publicada (2a); true y la lista vacía
 *                     significa que hay, pero ninguna coincide con los filtros (4a).
 */
public record CatalogResult(List<CatalogCard> activities, boolean anyPublished) {

    public boolean noneMatch() {
        return anyPublished && activities.isEmpty();
    }
}
