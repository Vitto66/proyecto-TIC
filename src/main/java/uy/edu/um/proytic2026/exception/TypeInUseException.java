package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/** CU-13 3a: no se puede eliminar un tipo que usan actividades; se ofrece desactivarlo. */
@Getter
public class TypeInUseException extends RuntimeException {

    private final Integer typeId;
    private final String typeName;
    private final long usageCount;

    public TypeInUseException(Integer typeId, String typeName, long usageCount) {
        super("\"" + typeName + "\" está en uso por "
                + (usageCount == 1 ? "1 actividad" : usageCount + " actividades"));
        this.typeId = typeId;
        this.typeName = typeName;
        this.usageCount = usageCount;
    }

    /** "1 actividad" / "3 actividades". */
    public String getUsageLabel() {
        return usageCount == 1 ? "1 actividad" : usageCount + " actividades";
    }
}
