package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.CancelResult;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.payment.PaymentGateway;
import uy.edu.um.proytic2026.payment.PaymentResult;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CU-10 2a cuando el servicio de pagos falla: la reserva igual se cancela, la devolución queda
 * pendiente y se puede reintentar. El servicio de pagos es un simulacro de Mockito.
 */
@SpringBootTest
@Transactional
class ActivityCancellationRefundTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    @MockitoBean private PaymentGateway gateway;
    @MockitoBean private TouristNotifier notifier;

    @Autowired private ActivityCancellationService cancellationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private TouristRepository touristRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityInstanceRepository instanceRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationPaymentRepository paymentRepository;

    private record Data(Provider provider, Activity activity, Reservation reservation) {
    }

    private Data paidReservation() {
        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak").description("x").address("x").phone("099").email("a@b.uy").build());
        Activity activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Kayak").description("x").durationMinutes(60).price(new BigDecimal("1000"))
                .minAge(0).bookingCutoffMinutes(0).active(true).build());
        ActivityInstance date = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusDays(3)).maxCapacity(10).build());
        User touristUser = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        Tourist tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("P").nationalId("12345678").build());
        Reservation reservation = reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(2).totalPrice(new BigDecimal("2000")).status(ReservationStatus.CONFIRMED)
                .createdAt(NOW.minusDays(1)).build());
        paymentRepository.save(ReservationPayment.builder().reservation(reservation).provider("MERCADOPAGO")
                .transactionId("mp-1").status(PaymentStatus.APPROVED).amount(new BigDecimal("2000"))
                .createdAt(NOW.minusDays(1)).build());
        return new Data(provider, activity, reservation);
    }

    @Test
    void refundServiceDown_cancelsAnyway_andTheRefundCanBeRetried() {
        Data data = paidReservation();
        when(gateway.refund(eq("mp-1"), anyString())).thenReturn(PaymentResult.unavailable("timeout"));

        CancelResult result = cancellationService.cancel(data.provider().getId(), data.activity().getId());

        assertThat(result.refundsPending()).isEqualTo(1);
        assertThat(result.message()).contains("reintentá la devolución");
        assertThat(data.reservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancellationService.pendingRefunds(data.provider().getId()))
                .containsEntry(data.activity().getId(), 1L);
        verify(notifier).reservationCancelled(eq("ana@mail.com"), eq("Ana P"), eq("Kayak"),
                eq(data.reservation().getId()), eq(PaymentService.RefundOutcome.PENDING));

        // El servicio vuelve: se reintenta y queda devuelto
        when(gateway.refund(eq("mp-1"), anyString()))
                .thenReturn(new PaymentResult(PaymentStatus.REFUNDED, "mp-1", "refunded"));

        CancelResult retry = cancellationService.retryRefunds(data.provider().getId(), data.activity().getId());

        assertThat(retry.refunded()).isEqualTo(1);
        assertThat(retry.refundsPending()).isZero();
        assertThat(cancellationService.pendingRefunds(data.provider().getId())).isEmpty();
        assertThat(paymentRepository.findFirstByReservationIdOrderByIdDesc(data.reservation().getId()))
                .get().extracting(ReservationPayment::getStatus).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void pendingReservationWhosePaymentWasChargedWithoutAnswer_isRefundedToo() {
        Data data = paidReservation();
        // Esta otra reserva estaba pendiente: su intento de pago se cortó, pero Mercado Pago lo cobró
        Reservation pending = reservationRepository.save(Reservation.builder().tourist(data.reservation().getTourist())
                .instance(data.reservation().getInstance()).participants(1).totalPrice(new BigDecimal("1000"))
                .status(ReservationStatus.PENDING).createdAt(NOW.minusHours(1)).build());
        paymentRepository.save(ReservationPayment.builder().reservation(pending).provider("MERCADOPAGO")
                .status(PaymentStatus.UNAVAILABLE).amount(new BigDecimal("1000")).createdAt(NOW.minusHours(1)).build());
        when(gateway.name()).thenReturn("MERCADOPAGO");
        when(gateway.findApproved("reserva-" + pending.getId()))
                .thenReturn(Optional.of(new PaymentResult(PaymentStatus.APPROVED, "mp-2", "accredited")));
        when(gateway.refund(anyString(), anyString()))
                .thenAnswer(inv -> new PaymentResult(PaymentStatus.REFUNDED, inv.getArgument(0), "refunded"));

        CancelResult result = cancellationService.cancel(data.provider().getId(), data.activity().getId());

        assertThat(result.refunded()).isEqualTo(2);
        verify(gateway).refund(eq("mp-2"), anyString());
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }
}
