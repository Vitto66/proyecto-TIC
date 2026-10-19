package uy.edu.um.proytic2026.entity;

/**
 * Perfil con el que se ingresa a la plataforma (CU-04). Un mismo correo puede tener
 * más de uno; el usuario elige con cuál entra y esa elección define sus permisos.
 */
public enum Role {
    TOURIST,
    PROVIDER,
    ADMIN
}
