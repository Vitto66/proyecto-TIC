package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un intento de pago de una reserva con el servicio externo (CU-15). Se guarda cada intento,
 * también los rechazados o sin respuesta, para saber qué pasó y no cobrar dos veces.
 */
@Entity
@Table(name = "reservation_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    /** Qué servicio lo procesó: MERCADOPAGO o SIMULATED. */
    @Column(nullable = false, length = 20)
    private String provider;

    /** Identificador del pago en el servicio externo. Vacío si el servicio no respondió. */
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Motivo que informa el servicio (por ejemplo cc_rejected_insufficient_amount). */
    @Column(name = "status_detail", length = 100)
    private String statusDetail;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
