package uy.edu.um.proytic2026.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uy.edu.um.proytic2026.entity.PaymentStatus;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pagos con la API de Mercado Pago (https://www.mercadopago.com.uy/developers), usando el
 * Checkout API con Bricks:
 * <ol>
 *   <li>En el navegador, el formulario de tarjeta de Mercado Pago (Card Payment Brick, con la
 *       clave pública) convierte la tarjeta en un token. Los datos de la tarjeta no llegan a
 *       nuestro servidor.</li>
 *   <li>El servidor cobra con ese token: POST /v1/payments, con el access token (secreto).</li>
 * </ol>
 * Con credenciales de prueba (sandbox) no se mueve plata real y el resultado se elige con el
 * nombre del titular de la tarjeta de prueba: APRO aprueba, OTHE rechaza, CONT queda pendiente.
 * <p>
 * Si la API no responde o devuelve un error del servidor, se informa
 * {@link PaymentResult#unavailable(String)}: la reserva sigue pendiente y no se confirma.
 */
@Slf4j
public class MercadoPagoGateway implements PaymentGateway {

    static final String BASE_URL = "https://api.mercadopago.com";

    private final RestClient restClient;
    private final String publicKey;

    public MercadoPagoGateway(String accessToken, String publicKey) {
        this(defaultBuilder(), accessToken, publicKey);
    }

    /** Para los tests, que conectan un servidor simulado al builder. */
    MercadoPagoGateway(RestClient.Builder builder, String accessToken, String publicKey) {
        this.restClient = builder.baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
        this.publicKey = publicKey;
    }

    /** Si Mercado Pago tarda más que esto, se toma como que no respondió (CU-15 4b). */
    private static RestClient.Builder defaultBuilder() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public String name() {
        return "MERCADOPAGO";
    }

    @Override
    public boolean usesCardForm() {
        return true;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.amount());
        body.put("token", request.cardToken());
        body.put("description", request.description());
        body.put("installments", Math.max(1, request.installments()));
        body.put("payment_method_id", request.paymentMethodId());
        if (request.issuerId() != null && !request.issuerId().isBlank()) {
            body.put("issuer_id", request.issuerId());
        }
        body.put("external_reference", request.externalReference());
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("email", request.payerEmail());
        if (request.documentNumber() != null && !request.documentNumber().isBlank()) {
            payer.put("identification", Map.of("type", request.documentType() == null ? "CI" : request.documentType(),
                    "number", request.documentNumber()));
        }
        body.put("payer", payer);

        try {
            Map<String, Object> response = restClient.post().uri("/v1/payments")
                    .header("X-Idempotency-Key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return toResult(response);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                // Datos inválidos (por ejemplo, un token vencido): no se cobró nada
                log.warn("Mercado Pago rechazó la solicitud de pago {}: {} {}", request.externalReference(),
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                return new PaymentResult(PaymentStatus.REJECTED, null, "invalid_request");
            }
            log.warn("Mercado Pago respondió con error al cobrar {}: {}", request.externalReference(), ex.getStatusCode());
            return PaymentResult.unavailable("http_" + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("Mercado Pago no respondió al cobrar {}: {}", request.externalReference(), ex.getMessage());
            return PaymentResult.unavailable(ex instanceof ResourceAccessException ? "timeout" : "error");
        }
    }

    @Override
    public PaymentResult status(String transactionId) {
        try {
            Map<String, Object> response = restClient.get().uri("/v1/payments/{id}", transactionId)
                    .retrieve().body(Map.class);
            return toResult(response);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar el pago {} en Mercado Pago: {}", transactionId, ex.getMessage());
            return PaymentResult.unavailable("error");
        }
    }

    @Override
    public Optional<PaymentResult> findApproved(String externalReference) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/payments/search?external_reference={ref}&sort=date_created&criteria=desc", externalReference)
                    .retrieve().body(Map.class);
            if (response == null || !(response.get("results") instanceof List<?> results)) {
                return Optional.empty();
            }
            return results.stream()
                    .filter(Map.class::isInstance)
                    .map(r -> toResult((Map<String, Object>) r))
                    .filter(PaymentResult::approved)
                    .findFirst();
        } catch (RestClientException ex) {
            log.warn("No se pudieron buscar los pagos de {} en Mercado Pago: {}", externalReference, ex.getMessage());
            return Optional.of(PaymentResult.unavailable("error"));
        }
    }

    @Override
    public PaymentResult refund(String transactionId, String idempotencyKey) {
        try {
            // Sin monto = devolución total
            restClient.post().uri("/v1/payments/{id}/refunds", transactionId)
                    .header("X-Idempotency-Key", idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
            return new PaymentResult(PaymentStatus.REFUNDED, transactionId, "refunded");
        } catch (RestClientException ex) {
            log.error("No se pudo devolver el pago {} en Mercado Pago: {}", transactionId, ex.getMessage());
            return PaymentResult.unavailable("refund_failed");
        }
    }

    /** Traduce el estado de Mercado Pago (approved, rejected, in_process, ...) al nuestro. */
    static PaymentResult toResult(Map<String, Object> payment) {
        if (payment == null) return PaymentResult.unavailable("empty_response");
        String id = payment.get("id") == null ? null : String.valueOf(payment.get("id"));
        String status = String.valueOf(payment.get("status"));
        String detail = payment.get("status_detail") == null ? null : String.valueOf(payment.get("status_detail"));
        PaymentStatus ours = switch (status) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected", "cancelled" -> PaymentStatus.REJECTED;
            case "refunded", "charged_back" -> PaymentStatus.REFUNDED;
            // pending, in_process, authorized, in_mediation: todavía no está aprobado
            default -> PaymentStatus.IN_PROCESS;
        };
        return new PaymentResult(ours, id, detail);
    }
}
