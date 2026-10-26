package uy.edu.um.proytic2026.dto;

/**
 * Una fila de la lista de tipos del CU-13.
 *
 * @param usageCount cantidad de actividades que lo usan. Si es mayor que cero no se puede
 *                   eliminar (3a), solo desactivar.
 */
public record TypeRow(Integer id, String name, boolean active, long usageCount) {

    public boolean inUse() {
        return usageCount > 0;
    }

    public String usageLabel() {
        if (usageCount == 0) {
            return "Sin actividades";
        }
        return usageCount == 1 ? "1 actividad" : usageCount + " actividades";
    }
}
