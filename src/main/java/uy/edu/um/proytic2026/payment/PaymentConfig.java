package uy.edu.um.proytic2026.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige el servicio de pagos al arrancar: Mercado Pago si están sus credenciales
 * (variables de entorno MP_PUBLIC_KEY y MP_ACCESS_TOKEN), si no el simulado.
 * Las credenciales NUNCA van en el repo: el access token permite cobrar.
 */
@Slf4j
@Configuration
public class PaymentConfig {

    @Bean
    PaymentGateway paymentGateway(@Value("${app.payments.mercadopago.access-token:}") String accessToken,
                                  @Value("${app.payments.mercadopago.public-key:}") String publicKey) {
        if (!accessToken.isBlank() && !publicKey.isBlank()) {
            log.info("Pagos con Mercado Pago ({})", accessToken.startsWith("TEST-") ? "credenciales de prueba" : "credenciales de la cuenta");
            return new MercadoPagoGateway(accessToken.trim(), publicKey.trim());
        }
        log.info("Pagos simulados: no están MP_PUBLIC_KEY y MP_ACCESS_TOKEN");
        return new SimulatedPaymentGateway();
    }
}
