package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos del formulario del CU-07 Registrar el emprendimiento: identificación (nombre),
 * contacto, ubicación y descripción general de los servicios. Todos son obligatorios
 * (3a: si falta alguno, se marca el campo).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterBusinessRequest {

    @NotBlank(message = "es obligatorio")
    @Size(max = 150, message = "no puede superar los 150 caracteres")
    private String name;

    @NotNull(message = "elegí el departamento")
    private Integer departmentId;

    @NotNull(message = "elegí la localidad")
    private Integer localityId;

    @NotBlank(message = "es obligatoria")
    @Size(max = 200, message = "no puede superar los 200 caracteres")
    private String address;

    @NotBlank(message = "es obligatorio")
    @Size(max = 30, message = "no puede superar los 30 caracteres")
    private String phone;

    @NotBlank(message = "es obligatorio")
    @Email(message = "no es un correo válido")
    @Size(max = 150, message = "no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "es obligatoria")
    @Size(max = 2000, message = "no puede superar los 2000 caracteres")
    private String description;
}
