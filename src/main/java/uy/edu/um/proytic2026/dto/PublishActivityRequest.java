package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos del formulario del CU-08 Publicar una actividad. Si falta un dato obligatorio
 * se marca el campo (6a).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishActivityRequest {

    @NotBlank(message = "es obligatorio")
    @Size(max = 150, message = "no puede superar los 150 caracteres")
    private String name;

    @NotBlank(message = "es obligatoria")
    @Size(max = 2000, message = "no puede superar los 2000 caracteres")
    private String description;

    @NotNull(message = "elegí el departamento")
    private Integer departmentId;

    @NotNull(message = "elegí la localidad")
    private Integer localityId;

    /** Duración aproximada, en minutos. Hasta 7 días, para cubrir travesías de varios días. */
    @NotNull(message = "es obligatoria")
    @Min(value = 1, message = "tiene que ser de al menos 1 minuto")
    @Max(value = 10080, message = "no puede superar los 7 días (10080 minutos)")
    private Integer durationMinutes;

    /** Precio por persona, en pesos uruguayos. */
    @NotNull(message = "es obligatorio")
    @DecimalMin(value = "0", message = "no puede ser negativo")
    @DecimalMax(value = "99999999.99", message = "es demasiado alto")
    @Digits(integer = 8, fraction = 2, message = "puede tener hasta 2 decimales")
    private BigDecimal price;

    @NotNull(message = "es obligatoria")
    @Min(value = 0, message = "no puede ser negativa")
    @Max(value = 99, message = "no puede superar los 99 años")
    private Integer minAge;

    /** Paso 5: minutos antes del inicio en que se cierran las reservas; 0 = hasta la hora de comienzo. */
    @NotNull(message = "es obligatorio")
    @Min(value = 0, message = "no puede ser negativo")
    @Max(value = 10080, message = "no puede superar los 7 días (10080 minutos)")
    private Integer bookingCutoffMinutes;

    /** Paso 3: uno o más tipos de actividad. */
    @NotEmpty(message = "elegí al menos un tipo de actividad")
    @Builder.Default
    private List<Integer> activityTypeIds = new ArrayList<>();

    /**
     * Paso 4: equipamiento elegido, por id de tipo de equipamiento, con quién lo pone.
     * Los que no se usan no aparecen (o quedan en null). Es opcional: hay actividades sin equipamiento.
     */
    @Builder.Default
    private Map<Integer, EquipmentSource> equipment = new HashMap<>();

    /** Para el formulario: "PROVIDER", "TOURIST" o "" si ese equipamiento no se usa. */
    public String equipmentChoice(Integer equipmentTypeId) {
        EquipmentSource source = equipment == null ? null : equipment.get(equipmentTypeId);
        return source == null ? "" : source.name();
    }
}
