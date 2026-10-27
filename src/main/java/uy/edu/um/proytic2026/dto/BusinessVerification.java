package uy.edu.um.proytic2026.dto;

import uy.edu.um.proytic2026.entity.ProviderStatus;

/**
 * Resultado de verificar un emprendimiento por RUT o documento, para la API (CU-22).
 *
 * @param registered   si hay un prestador registrado con ese RUT o documento (2a: false si no)
 * @param approved     si el administrador lo aprobó
 * @param status       PENDING, APPROVED o REJECTED (3a); null si no está registrado
 * @param businessName nombre del emprendimiento, o null si todavía no lo registró (CU-07)
 */
public record BusinessVerification(boolean registered, boolean approved, ProviderStatus status,
                                   String businessName) {

    public static BusinessVerification notRegistered() {
        return new BusinessVerification(false, false, null, null);
    }
}
