package uy.edu.um.proytic2026.service;

import uy.edu.um.proytic2026.entity.Provider;

/**
 * CU-03 paso 4: avisarle al prestador que su solicitud fue resuelta.
 * <p>
 * Hoy el aviso es dentro de la plataforma: al iniciar sesión, el prestador aprobado ve que ya
 * puede registrar su emprendimiento y el rechazado ve que su solicitud fue rechazada (CU-04 2b).
 * Esta interfaz es el punto donde se enchufa un aviso por correo sin tocar la lógica de CU-03.
 */
public interface ProviderNotifier {

    void providerReviewed(Provider provider);
}
