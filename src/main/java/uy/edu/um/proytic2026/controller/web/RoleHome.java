package uy.edu.um.proytic2026.controller.web;

import uy.edu.um.proytic2026.entity.Role;

/** A qué espacio va cada perfil después de iniciar sesión. */
final class RoleHome {

    private RoleHome() {
    }

    static String pathFor(Role role) {
        return switch (role) {
            case TOURIST -> "/tourist";
            case PROVIDER -> "/provider";
            case ADMIN -> "/admin";
        };
    }
}
