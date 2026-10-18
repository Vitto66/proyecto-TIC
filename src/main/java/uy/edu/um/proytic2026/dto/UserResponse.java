package uy.edu.um.proytic2026.dto;

import java.util.List;

/**
 * Lo que se devuelve de un usuario. Nunca incluye la contraseña.
 *
 * @param providerStatus estado de validación del perfil de prestador (PENDING, APPROVED,
 *                       REJECTED), o null si la cuenta no es prestador.
 */
public record UserResponse(Long id, String email, String firstName, String lastName,
                           List<String> roles, String providerStatus) {
}
