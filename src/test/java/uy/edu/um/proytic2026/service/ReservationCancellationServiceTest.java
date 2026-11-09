package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.exception.ReservationNotCancellableException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.payment.PaymentGateway;
import uy.edu.um.proytic2026.payment.PaymentResult;
import uy.edu.um.proytic2026.repository.*;
import uy.edu.um.proytic2026.service.PaymentService.RefundOutcome;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El turista cancela su reserva según la política que eligió el prestador, y se le devuelve el
 * 100% de lo pagado. El servicio de pagos es un simulacro de Mockito.
 */
@SpringBootTest
@Transactional
class ReservationCancellationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    @MockitoBean private PaymentGateway gateway;

    @Autowired private ReservationCancellationService cancellationService;
    @Autowired private ReservationService reservationService;
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

    /**
     * Una reserva confirmada y paga de una actividad que empieza en {@code hoursToStart} horas.
     *
     * @param allowed si la actividad permite cancelar; se puede hasta 24 horas antes.
     */
    private Reservation paidReservation(boolean allowed, int hoursToStart) {
        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak").description("x").address("x").phone("099").email("a@b.uy").build());
        Activity activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Kayak").description("x").durationMinutes(60).price(new BigDecimal("1000"))
                .minAge(0).bookingCutoffMinutes(0).cancellationAllowed(allowed).cancellationHours(24)
                .active(true).build());
        ActivityInstance date = instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusHours(hoursToStart)).maxCapacity(10).build());
        User touristUser = userRepository.save(User.builder().email("ana@mail.com").passwordHash("x").build());
        Tourist tourist = touristRepository.save(Tourist.builder().user(touristUser)
                .firstName("Ana").lastName("P").nationalId("12345678").build());
        Reservation reservation = reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(2).totalPrice(new BigDecimal("2000")).status(ReservationStatus.CONFIRMED)
                .createdAt(NOW.minusDays(1)).build());
        paymentRepository.save(ReservationPayment.builder().reservation(reservation).provider("MERCADOPAGO")
                .transactionId("mp-1").status(PaymentStatus.APPROVED).amount(new BigDecimal("2000"))
                .createdAt(NOW.minusDays(1)).build());
        return reservation;
    }

    private Long touristId(Reservation r) {
        return r.getTourist().getId();
    }

    @Test
    void allowedAndInTime_cancelsAndRefundsEverything() {
        Reservation r = paidReservation(true, 72);
        when(gateway.refund(eq("mp-1"), anyString()))
                .thenReturn(new PaymentResult(PaymentStatus.REFUNDED, "mp-1", "refunded"));

        assertThat(reservationService.reservation(touristId(r), r.getId()).cancellable()).isTrue();

        RefundOutcome outcome = cancellationService.cancel(touristId(r), r.getId());

        assertThat(outcome).isEqualTo(RefundOutcome.REFUNDED);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(r.isCancelledByTourist()).isTrue();
        verify(gateway).refund(eq("mp-1"), anyString());
        var view = reservationService.reservation(touristId(r), r.getId());
        assertThat(view.refunded()).isTrue();
        assertThat(view.refundLabel()).isEqualTo("− $ 2.000");
        assertThat(view.cancellable()).isFalse();
    }

    @Test
    void activityDoesNotAllowIt_isRejected() {
        Reservation r = paidReservation(false, 72);

        assertThatThrownBy(() -> cancellationService.cancel(touristId(r), r.getId()))
                .isInstanceOf(ReservationNotCancellableException.class);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(gateway, never()).refund(anyString(), anyString());
    }

    @Test
    void afterTheDeadline_isRejected() {
        // Empieza en 10 horas y se podía cancelar hasta 24 horas antes
        Reservation r = paidReservation(true, 10);

        assertThat(reservationService.reservation(touristId(r), r.getId()).cancellable()).isFalse();
        assertThatThrownBy(() -> cancellationService.cancel(touristId(r), r.getId()))
                .isInstanceOf(ReservationNotCancellableException.class);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void anotherTouristsReservation_isNotFound() {
        Reservation r = paidReservation(true, 72);

        assertThatThrownBy(() -> cancellationService.cancel(touristId(r) + 999, r.getId()))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void refundServiceDown_staysCancelled_andCanBeRetried() {
        Reservation r = paidReservation(true, 72);
        when(gateway.refund(eq("mp-1"), anyString())).thenReturn(PaymentResult.unavailable("timeout"));

        assertThat(cancellationService.cancel(touristId(r), r.getId())).isEqualTo(RefundOutcome.PENDING);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservationService.reservation(touristId(r), r.getId()).refundPending()).isTrue();

        when(gateway.refund(eq("mp-1"), anyString()))
                .thenReturn(new PaymentResult(PaymentStatus.REFUNDED, "mp-1", "refunded"));

        assertThat(cancellationService.cancel(touristId(r), r.getId())).isEqualTo(RefundOutcome.REFUNDED);
        assertThat(reservationService.reservation(touristId(r), r.getId()).refunded()).isTrue();
    }
}
