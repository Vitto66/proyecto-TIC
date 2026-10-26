package uy.edu.um.proytic2026.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uy.edu.um.proytic2026.entity.PaymentStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * La conexión con la API de Mercado Pago, contra un servidor simulado: no sale a internet.
 * Verifica qué se manda (token, monto, clave de idempotencia, access token) y cómo se traduce
 * cada respuesta, incluidos los errores y la falta de respuesta (CU-15 4a y 4b).
 */
class MercadoPagoGatewayTest {

    private static final String PAYMENTS = MercadoPagoGateway.BASE_URL + "/v1/payments";

    private MockRestServiceServer server;
    private MercadoPagoGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MercadoPagoGateway(builder, "TEST-access-token", "TEST-public-key");
    }

    private static ChargeRequest charge() {
        return new ChargeRequest("clave-1", new BigDecimal("3000.00"), "Reserva #12 · Kayak", "reserva-12",
                "tok_123", "visa", "310", 1, "ana@mail.com", "CI", "12345678", null);
    }

    @Test
    void charge_sendsTheTokenAndAmountWithTheAccessTokenAndIdempotencyKey() {
        server.expect(requestTo(PAYMENTS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-access-token"))
                .andExpect(header("X-Idempotency-Key", "clave-1"))
                .andExpect(content().string(containsString("\"transaction_amount\":3000.00")))
                .andExpect(content().string(containsString("\"token\":\"tok_123\"")))
                .andExpect(content().string(containsString("\"payment_method_id\":\"visa\"")))
                .andExpect(content().string(containsString("\"installments\":1")))
                .andExpect(content().string(containsString("\"external_reference\":\"reserva-12\"")))
                .andExpect(content().string(containsString("\"email\":\"ana@mail.com\"")))
                .andExpect(content().string(containsString("\"number\":\"12345678\"")))
                .andRespond(withSuccess("""
                        {"id": 1234567890, "status": "approved", "status_detail": "accredited"}
                        """, MediaType.APPLICATION_JSON));

        PaymentResult result = gateway.charge(charge());

        server.verify();
        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.transactionId()).isEqualTo("1234567890");
    }

    @Test
    void charge_rejected_saysWhy() {
        server.expect(requestTo(PAYMENTS)).andRespond(withSuccess("""
                {"id": 55, "status": "rejected", "status_detail": "cc_rejected_insufficient_amount"}
                """, MediaType.APPLICATION_JSON));

        PaymentResult result = gateway.charge(charge());

        assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(result.rejectionMessage()).isEqualTo("La tarjeta no tiene fondos suficientes.");
    }

    @Test
    void charge_inProcess_isNotApprovedYet() {
        server.expect(requestTo(PAYMENTS)).andRespond(withSuccess("""
                {"id": 56, "status": "in_process", "status_detail": "pending_contingency"}
                """, MediaType.APPLICATION_JSON));

        assertThat(gateway.charge(charge()).status()).isEqualTo(PaymentStatus.IN_PROCESS);
    }

    @Test
    void charge_invalidRequest_isRejectedWithoutCharging() {
        server.expect(requestTo(PAYMENTS)).andRespond(withBadRequest()
                .body("{\"message\":\"invalid card token\"}").contentType(MediaType.APPLICATION_JSON));

        PaymentResult result = gateway.charge(charge());

        assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(result.transactionId()).isNull();
    }

    @Test
    void charge_serverError_isUnavailable() {
        server.expect(requestTo(PAYMENTS)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        PaymentResult result = gateway.charge(charge());

        assertThat(result.status()).isEqualTo(PaymentStatus.UNAVAILABLE);
    }

    @Test
    void charge_noAnswer_isUnavailable() {
        server.expect(requestTo(PAYMENTS)).andRespond(request -> {
            throw new IOException("connect timed out");
        });

        assertThat(gateway.charge(charge()).status()).isEqualTo(PaymentStatus.UNAVAILABLE);
    }

    @Test
    void status_readsThePayment() {
        server.expect(requestTo(PAYMENTS + "/56")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\": 56, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.status("56").approved()).isTrue();
    }

    @Test
    void findApproved_findsAPreviousApprovedPaymentOfTheReservation() {
        server.expect(requestTo(containsString("/v1/payments/search?external_reference=reserva-12")))
                .andRespond(withSuccess("""
                        {"results": [
                          {"id": 1, "status": "rejected", "status_detail": "cc_rejected_other_reason"},
                          {"id": 2, "status": "approved", "status_detail": "accredited"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        Optional<PaymentResult> found = gateway.findApproved("reserva-12");

        assertThat(found).get().extracting(PaymentResult::transactionId).isEqualTo("2");
    }

    @Test
    void findApproved_withNone_isEmpty_andWithoutAnswer_isUnavailable() {
        server.expect(requestTo(containsString("/v1/payments/search")))
                .andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v1/payments/search")))
                .andRespond(withServerError());

        assertThat(gateway.findApproved("reserva-12")).isEmpty();
        assertThat(gateway.findApproved("reserva-12")).get()
                .extracting(PaymentResult::status).isEqualTo(PaymentStatus.UNAVAILABLE);
    }

    @Test
    void refund_returnsTheWholePayment() {
        server.expect(requestTo(PAYMENTS + "/2/refunds")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "refund-9"))
                .andRespond(withSuccess("{\"id\": 77, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.refund("2", "refund-9").status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void toResult_mapsMercadoPagoStatuses() {
        assertThat(MercadoPagoGateway.toResult(Map.of("status", "cancelled")).status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(MercadoPagoGateway.toResult(Map.of("status", "pending")).status()).isEqualTo(PaymentStatus.IN_PROCESS);
        assertThat(MercadoPagoGateway.toResult(Map.of("status", "refunded")).status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(MercadoPagoGateway.toResult(null).status()).isEqualTo(PaymentStatus.UNAVAILABLE);
    }
}
