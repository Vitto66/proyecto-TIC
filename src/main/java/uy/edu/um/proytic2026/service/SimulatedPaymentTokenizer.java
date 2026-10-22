package uy.edu.um.proytic2026.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tokenizador simulado: mientras no esté integrado el servicio de pagos externo, genera un
 * token al azar. Cuando se integre (CU-15), se reemplaza por una clase que llame a la API del
 * servicio y devuelva el token que éste genere. Los datos de la tarjeta no se guardan ni se
 * registran en el log.
 */
@Component
public class SimulatedPaymentTokenizer implements PaymentTokenizer {

    @Override
    public String tokenize(String cardDigits, int expiryMonth, int expiryYear, String cvv) {
        return "tok_sim_" + UUID.randomUUID();
    }
}
