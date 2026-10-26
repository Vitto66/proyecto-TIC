package uy.edu.um.proytic2026.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que manda la página de pago (CU-15). Con Mercado Pago, su formulario de tarjeta (Card
 * Payment Brick) genera el token y completa estos campos: el número de la tarjeta y el código
 * de seguridad no llegan a nuestro servidor. Con el pago simulado, solo se manda el titular.
 * El monto no viene del navegador: sale de la reserva.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayRequest {

    private String token;
    private String paymentMethodId;
    private String issuerId;
    private Integer installments;
    private String documentType;
    private String documentNumber;
    /** Solo para el pago simulado: decide el resultado (APRO, OTHE, CONT, ...). */
    private String cardholderName;
}
