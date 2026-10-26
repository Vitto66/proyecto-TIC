package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.PayRequest;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.payment.PaymentGateway;
import uy.edu.um.proytic2026.payment.PaymentResult;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CU-15: casos que dependen de lo que pasa del lado del servicio de pagos mientras se cobra.
 * El servicio de pagos es un simulacro de Mockito para controlar cada respuesta.
 */
@SpringBootTest
@Transactional
class PaymentServiceRaceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ClockConfig.URUGUAY).withSecond(0).withNano(0);

    @MockitoBean private PaymentGateway gateway;

    @Autowired private PaymentService paymentService;
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

    private Tourist tourist(String email, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return touristRepository.save(Tourist.builder().user(user).firstName("T").lastName("T").nationalId(nationalId).build());
    }

    private ActivityInstance date(int capacity) {
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
        return instanceRepository.save(ActivityInstance.builder().activity(activity)
                .startAt(NOW.plusDays(2)).maxCapacity(capacity).build());
    }

    private Reservation pending(Tourist tourist, ActivityInstance date, int participants) {
        return reservationRepository.save(Reservation.builder().tourist(tourist).instance(date)
                .participants(participants).totalPrice(new BigDecimal(1000 * participants))
                .status(ReservationStatus.PENDING).createdAt(NOW.minusMinutes(5)).build());
    }

    @Test
    void approvedButTheSeatsWereTakenWhileCharging_refundsAndDoesNotConfirm() {
        ActivityInstance date = date(2);
        Tourist ana = tourist("ana@mail.com", "12345678");
        Tourist luis = tourist("luis@mail.com", "23456789");
        Reservation anas = pending(ana, date, 2);
        Reservation luiss = pending(luis, date, 2);

        when(gateway.name()).thenReturn("TEST");
        when(gateway.findApproved(anyString())).thenReturn(Optional.empty());
        when(gateway.charge(any())).thenAnswer(inv -> {
            // Mientras Mercado Pago procesa el pago de Ana, Luis termina de pagar los 2 lugares
            luiss.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.flush();
            return new PaymentResult(PaymentStatus.APPROVED, "mp-1", "accredited");
        });
        when(gateway.refund(eq("mp-1"), anyString())).thenReturn(new PaymentResult(PaymentStatus.REFUNDED, "mp-1", "refunded"));

        PaymentService.PayResult result = paymentService.pay(ana.getId(), anas.getId(),
                PayRequest.builder().token("tok").paymentMethodId("visa").build());

        assertThat(result.outcome()).isEqualTo(PaymentService.Outcome.REFUNDED_NO_SEATS);
        assertThat(anas.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(gateway).refund(eq("mp-1"), anyString());
        assertThat(paymentRepository.findFirstByReservationIdOrderByIdDesc(anas.getId()))
                .get().extracting(ReservationPayment::getStatus).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void aPreviousAttemptThatWasChargedWithoutAnswer_isNotChargedAgain() {
        ActivityInstance date = date(10);
        Tourist ana = tourist("ana@mail.com", "12345678");
        Reservation anas = pending(ana, date, 1);

        when(gateway.name()).thenReturn("TEST");
        // El intento anterior se cortó, pero Mercado Pago sí lo cobró
        when(gateway.findApproved("reserva-" + anas.getId()))
                .thenReturn(Optional.of(new PaymentResult(PaymentStatus.APPROVED, "mp-9", "accredited")));

        PaymentService.PayResult result = paymentService.pay(ana.getId(), anas.getId(),
                PayRequest.builder().token("tok").paymentMethodId("visa").build());

        assertThat(result.outcome()).isEqualTo(PaymentService.Outcome.CONFIRMED);
        assertThat(anas.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(gateway, never()).charge(any());
    }

    @Test
    void chargesTheAmountOfTheReservation_withTheTouristsEmailAndCedula() {
        ActivityInstance date = date(10);
        Tourist ana = tourist("ana@mail.com", "12345678");
        Reservation anas = pending(ana, date, 3);
        when(gateway.name()).thenReturn("TEST");
        when(gateway.findApproved(anyString())).thenReturn(Optional.empty());
        when(gateway.charge(any())).thenReturn(new PaymentResult(PaymentStatus.APPROVED, "mp-2", "accredited"));

        paymentService.pay(ana.getId(), anas.getId(), PayRequest.builder().token("tok").paymentMethodId("master").build());

        verify(gateway).charge(argThat(c -> c.amount().compareTo(new BigDecimal("3000")) == 0
                && c.payerEmail().equals("ana@mail.com") && c.documentNumber().equals("12345678")
                && c.externalReference().equals("reserva-" + anas.getId()) && c.cardToken().equals("tok")));
    }
}
