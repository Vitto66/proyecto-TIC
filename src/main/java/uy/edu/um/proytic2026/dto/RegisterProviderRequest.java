package uy.edu.um.proytic2026.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uy.edu.um.proytic2026.entity.PersonType;

/**
 * Datos de entrada del CU-02 Registrarse como prestador.
 * <p>
 * Se usa tanto en la API REST (JSON) como en el formulario web (Thymeleaf), por eso es
 * una clase con getters y setters y no un record. Las anotaciones validan los campos
 * comunes; los que dependen del tipo de persona los valida
 * {@link uy.edu.um.proytic2026.validation.ProviderRegistrationValidator}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterProviderRequest {

    private PersonType personType;

    // Persona física
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    /** Cédula, sin puntos ni guion. */
    private String document;

    // Persona jurídica
    /** RUT, 12 dígitos. */
    private String rut;

    /** Razón social / nombre de la empresa. */
    @Size(max = 200)
    private String legalName;

    /** Nombre del emprendimiento. */
    @Size(max = 150)
    private String businessName;

    // Ambos tipos
    /** Domicilio (persona física) o dirección (persona jurídica). */
    @NotBlank(message = "es obligatorio")
    @Size(max = 200)
    private String address;

    @NotBlank(message = "es obligatorio")
    @Email(message = "no es un correo válido")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "es obligatoria")
    @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
    private String password;

    /** Solo lo manda el formulario web. Si viene, tiene que coincidir con password. */
    private String confirmPassword;
}
