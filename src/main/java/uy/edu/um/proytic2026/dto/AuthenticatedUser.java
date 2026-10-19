package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.Role;

/**
 * Quién tiene la sesión iniciada y con qué perfil entró. Es el principal de Spring Security:
 * nunca incluye la contraseña.
 *
 * @param displayName nombre para mostrar en pantalla (nombre, emprendimiento o nombre de admin).
 */
public record AuthenticatedUser(Long userId, String email, Role role, String displayName) {
}
