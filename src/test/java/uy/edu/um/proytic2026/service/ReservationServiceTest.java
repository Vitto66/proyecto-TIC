package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.ReservationOptions;
import uy.edu.um.proytic2026.dto.ReservationQuote;
import uy.edu.um.proytic2026.dto.ReservationView;
import uy.edu.um.proytic2026.dto.ReserveRequest;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.BookingClosedException;
import uy.edu.um.proytic2026.exception.DateNotAvailableException;
import uy.edu.um.proytic2026.exception.NotEnoughSeatsException;
import uy.edu.um.proytic2026.exception.ReservationNotFoundException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;
import uy.edu.um.proytic2026.repository.ReservationPaymentRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.dto.CompanionForm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-14 Reservar una actividad. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // varios tests usan solo parte de los datos de ejemplo
class ReservationServiceTest {

    /** "Ahora": lunes 26 de octubre de 2026, 10:00 en Uruguay. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);
    private static final Long ACTIVITY_ID = 100L;
    private static final Long TOURIST_ID = 7L;

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityInstanceRepository instanceRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private TouristRepository touristRepository;
    @Mock private ReservationPaymentRepository paymentRepository;

    private ReservationService service;
    private Activity activity;
    private ActivityInstance withCapacity;
    private ActivityInstance unlimited;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ClockConfig.URUGUAY).toInstant(), ClockConfig.URUGUAY);
        service = new ReservationService(activityRepository, instanceRepository, reservationRepository,
                touristRepository, paymentRepository, clock);

        Department rocha = Department.builder().id(1).name("Rocha").build();
        Locality laPaloma = Locality.builder().id(10).department(rocha).name("La Paloma").build();
        Business business = Business.builder().id(50L).name("Kayak Laguna").locality(laPaloma).build();
        // $ 1.500 por persona, 2 h 30 min, las reservas cierran 60 minutos antes
        activity = Activity.builder().id(ACTIVITY_ID).business(business).locality(laPaloma).name("Kayak")
                .price(new BigDecimal("1500")).durationMinutes(150).minAge(0).bookingCutoffMinutes(60)
                .active(true).build();
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

        withCapacity = instance(1L, NOW.plusDays(3), 10);
        unlimited = instance(2L, NOW.plusDays(4), null);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(withCapacity));
        when(instanceRepository.findById(2L)).thenReturn(Optional.of(unlimited));
        occupied(1L, 0L);
    }

    // ---------- Armado de datos ----------

    private ActivityInstance instance(Long id, LocalDateTime start, Integer capacity) {
        return ActivityInstance.builder().id(id).activity(activity).startAt(start).maxCapacity(capacity).build();
    }

    private void occupied(Long instanceId, Long seats) {
        ReservationRepository.OccupiedSeats row = new ReservationRepository.OccupiedSeats() {
            public Long getInstanceId() { return instanceId; }
            public Long getSeats() { return seats; }
        };
        when(reservationRepository.occupiedSeats(anyCollection(), anyCollection())).thenReturn(List.of(row));
    }

    /** Una solicitud con los datos de cada acompañante (las personas 2 en adelante). */
    private static ReserveRequest request(Long instanceId, int participants) {
        ReserveRequest request = ReserveRequest.builder().instanceId(instanceId).participants(participants).build();
        for (int i = 2; i <= participants; i++) {
            request.getCompanions().add(companion("Persona" + i, "4000000" + i));
        }
        return request;
    }

    private static CompanionForm companion(String name, String nationalId) {
        return CompanionForm.builder().firstName(name).lastName("Pérez").nationalId(nationalId)
                .phone("099123456").build();
    }

    // ---------- Pasos 4 y 5: verificar y resumir ----------

    @Test
    void quote_computesTheTotalPrice() {
        ReservationQuote quote = service.quote(ACTIVITY_ID, request(1L, 3));

        assertThat(quote.total()).isEqualByComparingTo("4500");
        assertThat(quote.totalLabel()).isEqualTo("$ 4.500");
        assertThat(quote.unitPriceLabel()).isEqualTo("$ 1.500");
        assertThat(quote.participantsLabel()).isEqualTo("3 personas");
        assertThat(quote.location()).isEqualTo("La Paloma, Rocha");
    }

    @Test
    void quote_notEnoughSeats_reportsHowManyAreLeft() {
        // 4a. Hay 10, están ocupados 8: quedan 2
        occupied(1L, 8L);

        assertThatThrownBy(() -> service.quote(ACTIVITY_ID, request(1L, 3)))
                .isInstanceOf(NotEnoughSeatsException.class)
                .extracting("available").isEqualTo(2L);
    }

    @Test
    void quote_exactlyTheSeatsLeft_isAllowed() {
        occupied(1L, 8L);

        assertThat(service.quote(ACTIVITY_ID, request(1L, 2)).participants()).isEqualTo(2);
    }

    @Test
    void quote_unlimitedDate_acceptsAnyAmount() {
        assertThat(service.quote(ACTIVITY_ID, request(2L, 40)).total()).isEqualByComparingTo("60000");
    }

    @Test
    void quote_withinTheBookingCutoff_isClosed() {
        // 4b. Empieza en 45 minutos y las reservas cierran 60 minutos antes
        when(instanceRepository.findById(3L)).thenReturn(Optional.of(instance(3L, NOW.plusMinutes(45), null)));

        assertThatThrownBy(() -> service.quote(ACTIVITY_ID, request(3L, 1)))
                .isInstanceOf(BookingClosedException.class);
    }

    @Test
    void quote_justBeforeTheCutoff_isStillOpen() {
        when(instanceRepository.findById(3L)).thenReturn(Optional.of(instance(3L, NOW.plusMinutes(61), null)));

        assertThat(service.quote(ACTIVITY_ID, request(3L, 1))).isNotNull();
    }

    @Test
    void quote_pastDate_isNotAvailable() {
        when(instanceRepository.findById(4L)).thenReturn(Optional.of(instance(4L, NOW.minusHours(1), null)));

        assertThatThrownBy(() -> service.quote(ACTIVITY_ID, request(4L, 1)))
                .isInstanceOf(DateNotAvailableException.class);
    }

    @Test
    void quote_dateOfAnotherActivity_isNotAvailable() {
        Activity other = Activity.builder().id(999L).build();
        when(instanceRepository.findById(5L)).thenReturn(Optional.of(
                ActivityInstance.builder().id(5L).activity(other).startAt(NOW.plusDays(2)).build()));

        assertThatThrownBy(() -> service.quote(ACTIVITY_ID, request(5L, 1)))
                .isInstanceOf(DateNotAvailableException.class);
    }

    @Test
    void quote_inactiveActivity_isNotFound() {
        activity.setActive(false);

        assertThatThrownBy(() -> service.quote(ACTIVITY_ID, request(1L, 1)))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    // ---------- Paso 6: confirmar ----------

    @Test
    void reserve_createsAPendingReservationWithFixedTotal() {
        Tourist tourist = Tourist.builder().id(TOURIST_ID).firstName("Ana").lastName("Gómez").nationalId("12345678").build();
        when(touristRepository.findById(TOURIST_ID)).thenReturn(Optional.of(tourist));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });

        ReservationView view = service.reserve(TOURIST_ID, ACTIVITY_ID, request(1L, 2));

        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(saved.capture());
        Reservation r = saved.getValue();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(r.getTourist()).isSameAs(tourist);
        assertThat(r.getInstance()).isSameAs(withCapacity);
        assertThat(r.getParticipants()).isEqualTo(2);
        assertThat(r.getTotalPrice()).isEqualByComparingTo("3000");
        assertThat(r.getCreatedAt()).isEqualTo(NOW);
        // La persona 1 es el turista; la 2 es el acompañante con sus datos
        assertThat(r.getCompanions()).singleElement().satisfies(c -> {
            assertThat(c.getPosition()).isEqualTo(2);
            assertThat(c.getFirstName()).isEqualTo("Persona2");
            assertThat(c.getNationalId()).isEqualTo("40000002");
            assertThat(c.getReservation()).isSameAs(r);
        });
        assertThat(view.touristName()).isEqualTo("Ana Gómez");
        assertThat(view.companions()).singleElement()
                .satisfies(c -> assertThat(c.fullName()).isEqualTo("Persona2 Pérez"));

        assertThat(view.id()).isEqualTo(500L);
        assertThat(view.pending()).isTrue();
        // Tiene 24 horas para pagar
        assertThat(view.payUntilLabel()).contains("27 de octubre de 2026").contains("10:00");
    }

    @Test
    void reserve_checksAgain_ifTheSeatsWereTakenMeanwhile() {
        occupied(1L, 10L);

        assertThatThrownBy(() -> service.reserve(TOURIST_ID, ACTIVITY_ID, request(1L, 1)))
                .isInstanceOf(NotEnoughSeatsException.class);
        verify(reservationRepository, never()).save(any());
    }

    // ---------- Paso 2: opciones ----------

    @Test
    void options_listUpcomingDatesWithAvailability() {
        ActivityInstance closingSoon = instance(3L, NOW.plusMinutes(30), null);
        when(instanceRepository.findUpcoming(anyCollection(), any()))
                .thenReturn(List.of(closingSoon, withCapacity, unlimited));
        occupied(1L, 10L); // la de capacidad 10 está llena

        ReservationOptions options = service.options(ACTIVITY_ID);

        assertThat(options.dates()).hasSize(3);
        assertThat(options.dates().get(0).bookingClosed()).isTrue();
        assertThat(options.dates().get(1).available()).isFalse();
        assertThat(options.dates().get(2).available()).isTrue();
        assertThat(options.anyAvailable()).isTrue();
    }

    // ---------- Ver la reserva ----------

    @Test
    void reservation_ofAnotherTourist_isNotFound() {
        Tourist other = Tourist.builder().id(99L).build();
        Reservation r = Reservation.builder().id(500L).tourist(other).instance(withCapacity).participants(1)
                .totalPrice(new BigDecimal("1500")).status(ReservationStatus.PENDING).createdAt(NOW).build();
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.reservation(TOURIST_ID, 500L))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    // ---------- Datos de los acompañantes ----------

    @Test
    void repeatedNationalIds_detectsTheTouristsOwnAndDuplicatedOnes() {
        Tourist tourist = Tourist.builder().id(TOURIST_ID).nationalId("12345678").build();
        when(touristRepository.findById(TOURIST_ID)).thenReturn(Optional.of(tourist));
        ReserveRequest request = ReserveRequest.builder().instanceId(1L).participants(4).build();
        request.getCompanions().add(companion("Luis", "12345678"));   // la del turista
        request.getCompanions().add(companion("Sofía", "5555555"));
        request.getCompanions().add(companion("Sofía bis", " 5555555 "));

        assertThat(service.repeatedNationalIds(TOURIST_ID, request)).containsExactly(0, 2);
    }

    @Test
    void reserve_withoutTheCompanionsData_isNotSaved() {
        Tourist tourist = Tourist.builder().id(TOURIST_ID).nationalId("12345678").build();
        when(touristRepository.findById(TOURIST_ID)).thenReturn(Optional.of(tourist));
        ReserveRequest request = ReserveRequest.builder().instanceId(1L).participants(3).build();

        assertThatThrownBy(() -> service.reserve(TOURIST_ID, ACTIVITY_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reservationRepository, never()).save(any());
    }
}
