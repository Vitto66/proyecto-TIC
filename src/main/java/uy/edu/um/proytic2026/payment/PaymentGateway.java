package uy.edu.um.proytic2026.payment;

import java.util.Optional;

/**
 * El servicio de pagos externo (RF-08, CU-15). Uruguay Mnatural no procesa pagos: le pide a este
 * servicio que cobre y solo confirma la reserva si el servicio lo aprueba.
 * <p>
 * Hay dos implementaciones, y {@link PaymentConfig} elige una al arrancar:
 * <ul>
 *   <li>{@link MercadoPagoGateway}: la API de Mercado Pago, en su modo de prueba (sandbox) con
 *       credenciales de prueba. Se usa si están las variables MP_PUBLIC_KEY y MP_ACCESS_TOKEN.</li>
 *   <li>{@link SimulatedPaymentGateway}: sin conexión, para los tests y para probar sin cuenta de
 *       Mercado Pago. Responde según el nombre del titular, igual que el sandbox (APRO, OTHE, ...).</li>
 * </ul>
 * Ninguna implementación lanza excepciones por fallas del servicio: devuelven
 * {@link PaymentResult#unavailable(String)} para que la reserva siga pendiente (CU-15 4b).
 */
public interface PaymentGateway {

    /** Nombre que se guarda con cada pago: MERCADOPAGO o SIMULATED. */
    String name();

    /** true si el formulario de tarjeta es el de Mercado Pago (necesita la clave pública). */
    boolean usesCardForm();

    /** Clave pública para el formulario de tarjeta del navegador. No es secreta. */
    String publicKey();

    /** Pide cobrar. Con la misma idempotencyKey, el servicio no cobra dos veces. */
    PaymentResult charge(ChargeRequest request);

    /** Consulta cómo quedó un pago que estaba en revisión. */
    PaymentResult status(String transactionId);

    /**
     * Busca un pago aprobado con esa referencia: si un intento anterior se cortó sin respuesta,
     * puede que igual se haya cobrado, y entonces no hay que volver a cobrar.
     * Devuelve {@link PaymentResult#unavailable(String)} si no se pudo consultar.
     */
    Optional<PaymentResult> findApproved(String externalReference);

    /** Devuelve el 100% de un pago aprobado. */
    PaymentResult refund(String transactionId, String idempotencyKey);
}
