package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.Role;

import java.util.List;

/**
 * Resultado de validar las credenciales (CU-04): de qué cuenta se trata y con qué perfiles
 * puede ingresar. Si hay más de uno, el sistema pregunta con cuál (alternativa 2c).
 */
public record LoginOptions(Long userId, List<Role> roles) {
}
