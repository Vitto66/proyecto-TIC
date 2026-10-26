package uy.edu.um.proytic2026.payment;

import java.math.BigDecimal;

/**
 * Lo que se le pide cobrar al servicio de pagos.
 *
 * @param idempotencyKey    clave única del intento: si se reenvía, el servicio no cobra otra vez.
 * @param amount            el total de la reserva, en pesos uruguayos. Sale de la base, nunca del navegador.
 * @param externalReference referencia nuestra ("reserva-12"), para encontrar el pago después.
 * @param cardToken         token de la tarjeta que generó el formulario de Mercado Pago en el navegador:
 *                          los datos de la tarjeta nunca pasan por nuestro servidor.
 * @param paymentMethodId   marca que detectó el formulario (visa, master, ...).
 * @param issuerId          banco emisor, si el formulario lo informa.
 * @param cardholderName    titular: solo lo usa el pago simulado para decidir el resultado.
 */
public record ChargeRequest(String idempotencyKey, BigDecimal amount, String description, String externalReference,
                            String cardToken, String paymentMethodId, String issuerId, int installments,
                            String payerEmail, String documentType, String documentNumber, String cardholderName) {
}
