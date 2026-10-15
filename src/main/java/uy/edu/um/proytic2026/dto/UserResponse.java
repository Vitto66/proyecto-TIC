package uy.edu.um.proytic2026.dto;

import java.util.List;

/** Lo que se devuelve de un usuario. Nunca incluye la contraseña. */
public record UserResponse(Long id, String email, String firstName, String lastName, List<String> roles) {
}
