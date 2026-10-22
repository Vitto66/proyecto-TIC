package uy.edu.um.proytic2026.service;

/**
 * Convierte los datos de una tarjeta en un token del servicio de pagos externo (RF-08).
 * Uruguay Mnatural nunca guarda el número ni el código de seguridad: guarda el token,
 * y con él el servicio de pagos sabe qué tarjeta cobrar (CU-15).
 */
public interface PaymentTokenizer {

    String tokenize(String cardDigits, int expiryMonth, int expiryYear, String cvv);
}
