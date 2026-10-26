package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CU-16 contra H2: qué reservas vencen, cuáles finalizan y el aviso al turista. El servicio se arma
 * con un reloj fijo para controlar "ahora".
 */
@SpringBootTest
@Transactional
class ReservationStatusServiceIntegrationTest {

    /** "Ahora": lunes 26 de octubre de 2026, 10:00 en Uruguay. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);

    /** El detalle de actividades pide el clima: acá no se sale a internet. */
    @MockitoBean private WeatherService weatherService;

    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private TouristRepository touristRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private LocalityRepository localityRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityInstanceRepository instanceRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private TouristNotificationRepository notificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private ReservationStatusService service;
    private Tourist tourist;
    private Activity activity;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ClockConfig.URUGUAY).toInstant(), ClockConfig.URUGUAY);
        service = new ReservationStatusService(reservationRepository, instanceRepository, notificationRepository,
                transactionManager, clock);

        Department rocha = departmentRepository.save(Department.builder().name("Rocha").build());
        Locality laPaloma = localityRepository.save(Locality.builder().department(rocha).name("La Paloma").build());
        User providerUser = userRepository.save(User.builder().email("juan@mail.com").passwordHash("x").build());
        Provider provider = providerRepository.save(Provider.builder().user(providerUser).personType(PersonType.PHYSICAL)
                .status(ProviderStatus.APPROVED).firstName("Juan").lastName("R").document("45678901").address("x").build());
        Business business = businessRepository.save(Business.builder().provider(provider).locality(laPaloma)
                .name("Kayak Laguna").description("x").address("x").phone("099").email("a@b.uy").build());
        tourist = tourist("ana@mail.com", "12345678");

        // 2 h 30 min, las reservas cierran 60 minutos antes
        activity = activityRepository.save(Activity.builder().business(business).locality(laPaloma)
                .name("Atardecer en kayak").description("Por la laguna").durationMinutes(150)
                .price(new BigDecimal("1500")).minAge(0).bookingCutoffMinutes(60).active(true).build());
    }

    // ---------- Datos de prueba ----------

    private Tourist tourist(String email, String nationalId) {
        User user = userRepository.save(User.builder().email(email).passwordHash("x").build());
        return touristRepository.save(Tourist.builder().user(user)
                .firstName("Ana").lastName("Pérez").nationalId(nationalId).build());
    }

    private ActivityInstance date(LocalDateTime start) {
        return instanceRepository.save(ActivityInstance.builder().activity(activity).startAt(start).maxCapacity(10).build());
    }

    private Reservation reservation(ActivityInstance date, ReservationStatus status, LocalDateTime createdAt) {
        return reservationRepository.save(Reservation.builder().tourist(tourist).instance(date).participants(2)
                .totalPrice(new BigDecimal("3000")).status(status).createdAt(createdAt).build());
    }

    private ReservationStatus statusOf(Reservation r) {
        return reservationRepository.findStatusById(r.getId()).orElseThrow();
    }

    private List<TouristNotification> notificationsOf(Tourist t) {
        return notificationRepository.findByTouristIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(t.getId());
    }

    // ---------- Pasos 2 a 4: vencer las pendientes ----------

    @Test
    void pendingFor24Hours_expiresAndTheTouristIsNotified() {
        // Fecha el domingo 1 de noviembre a las 10:00; la reserva se creó hace exactamente 24 horas
        Reservation r = reservation(date(LocalDateTime.of(2026, 11, 1, 10, 0)), ReservationStatus.PENDING,
                NOW.minusHours(24));

        ReservationStatusService.UpdateResult result = service.update();

        assertThat(result.expired()).isEqualTo(1);
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.EXPIRED);
        List<TouristNotification> notifications = notificationsOf(tourist);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getReservation().getId()).isEqualTo(r.getId());
        assertThat(notifications.get(0).getCreatedAt()).isEqualTo(NOW);
        assertThat(notifications.get(0).getMessage())
                .isEqualTo("Tu reserva de «Atardecer en kayak» para el domingo 1 de noviembre de 2026 a las 10:00 "
                        + "venció porque pasaron 24 horas sin que se aprobara el pago. No se te cobró nada.");
    }

    @Test
    void pendingWithinTheDay_stillPayable_isLeftAlone() {
        Reservation r = reservation(date(NOW.plusDays(5)), ReservationStatus.PENDING, NOW.minusHours(23).minusMinutes(59));

        assertThat(service.update().expired()).isZero();
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.PENDING);
        assertThat(notificationsOf(tourist)).isEmpty();
    }

    @Test
    void pendingWhoseDateClosedTheBookings_expires() {
        // Creada hace 1 hora, pero la fecha empieza en 45 minutos y las reservas cierran 60 antes
        Reservation r = reservation(date(NOW.plusMinutes(45)), ReservationStatus.PENDING, NOW.minusHours(1));

        assertThat(service.update().expired()).isEqualTo(1);
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(notificationsOf(tourist).get(0).getMessage())
                .contains("se cerraron las reservas para esa fecha");
    }

    @Test
    void confirmedBeforeTheDeadline_doesNotExpire() {
        // 2a. El turista pagó: aunque pasaron más de 24 horas, ya está confirmada
        Reservation r = reservation(date(NOW.plusDays(3)), ReservationStatus.CONFIRMED, NOW.minusDays(2));

        assertThat(service.update().expired()).isZero();
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(notificationsOf(tourist)).isEmpty();
    }

    @Test
    void runningAgain_doesNotNotifyTwice() {
        Reservation r = reservation(date(NOW.plusDays(5)), ReservationStatus.PENDING, NOW.minusDays(2));

        service.update();
        ReservationStatusService.UpdateResult second = service.update();

        assertThat(second.expired()).isZero();
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(notificationsOf(tourist)).hasSize(1);
    }

    @Test
    void eachTouristGetsTheirOwnNotice() {
        Tourist other = tourist("otro@mail.com", "87654321");
        Reservation mine = reservation(date(NOW.plusDays(5)), ReservationStatus.PENDING, NOW.minusDays(2));
        Reservation theirs = reservationRepository.save(Reservation.builder().tourist(other)
                .instance(mine.getInstance()).participants(1).totalPrice(new BigDecimal("1500"))
                .status(ReservationStatus.PENDING).createdAt(NOW.minusHours(2)).build());

        service.update();

        assertThat(notificationsOf(tourist)).hasSize(1);
        assertThat(notificationsOf(other)).isEmpty();
        assertThat(statusOf(theirs)).isEqualTo(ReservationStatus.PENDING);
    }

    // ---------- Paso 5: finalizar las confirmadas ----------

    @Test
    void confirmedWhoseDateEnded_finishes() {
        // Empezó hace 2 h 30 min y dura 2 h 30 min: terminó justo ahora
        Reservation ended = reservation(date(NOW.minusMinutes(150)), ReservationStatus.CONFIRMED, NOW.minusDays(3));
        // Empezó hace 1 hora: todavía está en curso
        Reservation ongoing = reservation(date(NOW.minusHours(1)), ReservationStatus.CONFIRMED, NOW.minusDays(3));
        // Todavía no empezó
        Reservation upcoming = reservation(date(NOW.plusDays(1)), ReservationStatus.CONFIRMED, NOW.minusDays(3));

        ReservationStatusService.UpdateResult result = service.update();

        assertThat(result.finished()).isEqualTo(1);
        assertThat(statusOf(ended)).isEqualTo(ReservationStatus.FINISHED);
        assertThat(statusOf(ongoing)).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(statusOf(upcoming)).isEqualTo(ReservationStatus.CONFIRMED);
        // Finalizar no genera avisos
        assertThat(notificationsOf(tourist)).isEmpty();
    }

    @Test
    void expiredAndFinishedReservations_areNotTouched() {
        Reservation expired = reservation(date(NOW.minusDays(1)), ReservationStatus.EXPIRED, NOW.minusDays(5));
        Reservation finished = reservation(date(NOW.minusDays(2)), ReservationStatus.FINISHED, NOW.minusDays(5));

        ReservationStatusService.UpdateResult result = service.update();

        assertThat(result).isEqualTo(new ReservationStatusService.UpdateResult(0, 0));
        assertThat(statusOf(expired)).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(statusOf(finished)).isEqualTo(ReservationStatus.FINISHED);
    }

    @Test
    void finishedReservation_stillCountsItsSeats() {
        // Al finalizar, la reserva sigue ocupando sus lugares (cuentan CONFIRMED y FINISHED)
        ActivityInstance d = date(NOW.minusMinutes(150));
        reservation(d, ReservationStatus.CONFIRMED, NOW.minusDays(3));

        service.update();

        Long seats = reservationRepository.occupiedSeats(List.of(d.getId()), ActivityDateService.SEAT_TAKING)
                .get(0).getSeats();
        assertThat(seats).isEqualTo(2L);
    }
}
