package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Medio de pago registrado por un turista (CU-01 paso 3, se usa en CU-15).
 * <p>
 * Nunca se guarda el número completo ni el código de seguridad: el servicio de pagos
 * devuelve un token que representa la tarjeta, y acá solo quedan la marca, los últimos
 * 4 dígitos y el vencimiento, para que el turista la reconozca ("Visa •••• 4242").
 */
@Entity
@Table(name = "payment_method")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tourist_id", nullable = false)
    private Tourist tourist;

    /** Lo que devuelve el servicio de pagos para cobrar con esta tarjeta sin conocer su número. */
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardBrand brand;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;
}
