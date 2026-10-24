package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uy.edu.um.proytic2026.config.ClockConfig;
import uy.edu.um.proytic2026.dto.ActivityDateView;
import uy.edu.um.proytic2026.dto.ActivitySchedule;
import uy.edu.um.proytic2026.dto.AddActivityDateRequest;
import uy.edu.um.proytic2026.dto.AddDateResult;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityInstance;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.ActivityNotPublishedException;
import uy.edu.um.proytic2026.exception.DuplicateDateException;
import uy.edu.um.proytic2026.exception.PastDateException;
import uy.edu.um.proytic2026.repository.ActivityInstanceRepository;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ReservationRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-09 Definir fechas, horarios y cupos de una actividad. */
@ExtendWith(MockitoExtension.class)
class ActivityDateServiceTest {

    private static final Long PROVIDER_ID = 7L;
    private static final Long ACTIVITY_ID = 100L;

    /** "Ahora" para todos los tests: martes 20 de octubre de 2026, 12:00 en Uruguay. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityInstanceRepository instanceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private ActivityDateService service;

    private Activity activity;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ClockConfig.URUGUAY).toInstant(), ClockConfig.URUGUAY);
        service = new ActivityDateService(activityRepository, instanceRepository, reservationRepository, clock);

        Provider owner = Provider.builder().id(PROVIDER_ID).build();
        Business business = Business.builder().id(50L).provider(owner).build();
        // Dura 2 h 30 min
        activity = Activity.builder().id(ACTIVITY_ID).business(business).name("Kayak")
                .durationMinutes(150).active(true).build();
    }

    // ---------- Armado de datos ----------

    private void activityExists() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    private AddActivityDateRequest request(LocalDate date, LocalTime time, Integer capacity, boolean confirm) {
        return AddActivityDateRequest.builder().date(date).time(time).maxCapacity(capacity).confirmOverlap(confirm).build();
    }

    private ActivityInstance instanceAt(long id, LocalDateTime start, Integer capacity) {
        return ActivityInstance.builder().id(id).activity(activity).startAt(start).maxCapacity(capacity).build();
    }

    private void saveReturnsWithId() {
        when(instanceRepository.saveAndFlush(any(ActivityInstance.class))).thenAnswer(inv -> {
            ActivityInstance i = inv.getArgument(0);
            i.setId(500L);
            return i;
        });
    }

    private void noOtherDates() {
        when(instanceRepository.findByActivityIdAndStartAtBetweenOrderByStartAtAsc(eq(ACTIVITY_ID), any(), any()))
                .thenReturn(List.of());
    }

    // ---------- Pasos 2 a 4: agregar una fecha ----------

    @Test
    void addDate_futureDate_isSavedAsAvailable() {
        activityExists();
        noOtherDates();
        saveReturnsWithId();

        AddDateResult result = service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), 12, false));

        assertThat(result.needsConfirmation()).isFalse();
        ArgumentCaptor<ActivityInstance> saved = ArgumentCaptor.forClass(ActivityInstance.class);
        verify(instanceRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getActivity()).isSameAs(activity);
        assertThat(saved.getValue().getStartAt()).isEqualTo(LocalDateTime.of(DAY, LocalTime.of(10, 0)));
        assertThat(saved.getValue().getMaxCapacity()).isEqualTo(12);

        // Termina cuando termina la actividad: 10:00 + 2 h 30 min
        assertThat(result.saved().endAt()).isEqualTo(LocalDateTime.of(DAY, LocalTime.of(12, 30)));
        assertThat(result.saved().freeSeats()).isEqualTo(12);
    }

    @Test
    void addDate_withoutCapacity_hasNoSeatLimit() {
        // 2a. No indica cantidad máxima → sin límite de cupos
        activityExists();
        noOtherDates();
        saveReturnsWithId();

        AddDateResult result = service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), null, false));

        assertThat(result.saved().unlimited()).isTrue();
        assertThat(result.saved().capacityLabel()).isEqualTo("Sin límite de cupos");
    }

    @Test
    void addDate_pastDate_isRejected() {
        // 3a. Fecha u hora ya pasadas
        activityExists();

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID,
                request(NOW.toLocalDate().minusDays(1), LocalTime.of(10, 0), null, false)))
                .isInstanceOf(PastDateException.class);
        verify(instanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void addDate_todayButEarlierHour_isRejected() {
        activityExists();

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID,
                request(NOW.toLocalDate(), LocalTime.of(11, 59), null, false)))
                .isInstanceOf(PastDateException.class);
    }

    @Test
    void addDate_exactlyNow_isRejected() {
        activityExists();

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID,
                request(NOW.toLocalDate(), NOW.toLocalTime(), null, false)))
                .isInstanceOf(PastDateException.class);
    }

    @Test
    void addDate_sameStartAsAnother_isRejectedEvenIfConfirmed() {
        activityExists();
        when(instanceRepository.existsByActivityIdAndStartAt(ACTIVITY_ID, LocalDateTime.of(DAY, LocalTime.of(10, 0))))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), null, true)))
                .isInstanceOf(DuplicateDateException.class);
        verify(instanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void addDate_simultaneousRequests_uniqueConstraintIsReportedAsDuplicate() {
        activityExists();
        noOtherDates();
        when(instanceRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("activity_instance_activity_id_start_at_key"));

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), null, false)))
                .isInstanceOf(DuplicateDateException.class);
    }

    // ---------- 3b. Superposición ----------

    @Test
    void addDate_overlapping_asksForConfirmationAndDoesNotSave() {
        activityExists();
        // Ya hay una de 10:00 a 12:30; la nueva arranca a las 11:00
        ActivityInstance existing = instanceAt(1L, LocalDateTime.of(DAY, LocalTime.of(10, 0)), 10);
        when(instanceRepository.findByActivityIdAndStartAtBetweenOrderByStartAtAsc(eq(ACTIVITY_ID), any(), any()))
                .thenReturn(List.of(existing));
        when(reservationRepository.occupiedSeats(anyCollection(), anyCollection())).thenReturn(List.of());

        AddDateResult result = service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(11, 0), null, false));

        assertThat(result.needsConfirmation()).isTrue();
        assertThat(result.overlaps()).extracting(ActivityDateView::id).containsExactly(1L);
        verify(instanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void addDate_overlappingButConfirmed_isSaved() {
        activityExists();
        saveReturnsWithId();

        AddDateResult result = service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(11, 0), null, true));

        assertThat(result.needsConfirmation()).isFalse();
        verify(instanceRepository).saveAndFlush(any());
        // Con la confirmación ya no se vuelve a buscar superposiciones
        verify(instanceRepository, never()).findByActivityIdAndStartAtBetweenOrderByStartAtAsc(any(), any(), any());
    }

    @Test
    void addDate_rightWhenTheOtherEnds_doesNotOverlap() {
        activityExists();
        // La otra termina 12:30; la nueva empieza 12:30 → no se pisan
        ActivityInstance existing = instanceAt(1L, LocalDateTime.of(DAY, LocalTime.of(10, 0)), null);
        when(instanceRepository.findByActivityIdAndStartAtBetweenOrderByStartAtAsc(eq(ACTIVITY_ID), any(), any()))
                .thenReturn(List.of(existing));
        saveReturnsWithId();

        AddDateResult result = service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(12, 30), null, false));

        assertThat(result.needsConfirmation()).isFalse();
    }

    @Test
    void addDate_endsAfterTheOtherStarts_overlaps() {
        activityExists();
        // La nueva 8:00 a 10:30 se pisa con la de 10:00
        ActivityInstance existing = instanceAt(1L, LocalDateTime.of(DAY, LocalTime.of(10, 0)), null);
        when(instanceRepository.findByActivityIdAndStartAtBetweenOrderByStartAtAsc(eq(ACTIVITY_ID), any(), any()))
                .thenReturn(List.of(existing));
        when(reservationRepository.occupiedSeats(anyCollection(), anyCollection())).thenReturn(List.of());

        AddDateResult result = service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(8, 0), null, false));

        assertThat(result.needsConfirmation()).isTrue();
    }

    // ---------- Precondiciones ----------

    @Test
    void addDate_activityOfAnotherProvider_isNotFound() {
        activityExists();

        assertThatThrownBy(() -> service.addDate(99L, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), null, false)))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    @Test
    void addDate_unknownActivity_isNotFound() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), null, false)))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    @Test
    void addDate_inactiveActivity_isRejected() {
        activity.setActive(false);
        activityExists();

        assertThatThrownBy(() -> service.addDate(PROVIDER_ID, ACTIVITY_ID, request(DAY, LocalTime.of(10, 0), null, false)))
                .isInstanceOf(ActivityNotPublishedException.class);
    }

    // ---------- Paso 1: las fechas con sus cupos ----------

    @Test
    void schedule_splitsUpcomingAndPastAndCountsConfirmedSeats() {
        activityExists();
        ActivityInstance past = instanceAt(1L, NOW.minusDays(2), 10);
        ActivityInstance soon = instanceAt(2L, NOW.plusDays(1), 10);
        ActivityInstance later = instanceAt(3L, NOW.plusDays(5), null);
        when(instanceRepository.findByActivityIdOrderByStartAtAsc(ACTIVITY_ID)).thenReturn(List.of(past, soon, later));
        when(reservationRepository.occupiedSeats(anyCollection(), eq(ActivityDateService.SEAT_TAKING)))
                .thenReturn(List.of(seats(2L, 4L)));

        ActivitySchedule schedule = service.schedule(PROVIDER_ID, ACTIVITY_ID);

        assertThat(schedule.upcoming()).extracting(ActivityDateView::id).containsExactly(2L, 3L);
        assertThat(schedule.past()).extracting(ActivityDateView::id).containsExactly(1L);
        assertThat(schedule.upcoming().get(0).freeSeats()).isEqualTo(6);
        assertThat(schedule.upcoming().get(1).unlimited()).isTrue();
    }

    private static ReservationRepository.OccupiedSeats seats(Long instanceId, Long seats) {
        return new ReservationRepository.OccupiedSeats() {
            public Long getInstanceId() { return instanceId; }
            public Long getSeats() { return seats; }
        };
    }

    // ---------- Textos de cupos ----------

    @Test
    void capacityLabels() {
        LocalDateTime start = LocalDateTime.of(DAY, LocalTime.of(10, 0));
        LocalDateTime end = start.plusMinutes(150);

        assertThat(new ActivityDateView(1L, start, end, 12, 4).capacityLabel()).isEqualTo("8 lugares libres de 12");
        assertThat(new ActivityDateView(1L, start, end, 12, 11).capacityLabel()).isEqualTo("1 lugar libre de 12");
        assertThat(new ActivityDateView(1L, start, end, 12, 12).full()).isTrue();
        assertThat(new ActivityDateView(1L, start, end, null, 3).capacityLabel()).isEqualTo("Sin límite de cupos · 3 reservados");
        assertThat(new ActivityDateView(1L, start, end, null, 0).timeLabel()).isEqualTo("10:00 a 12:30");
        assertThat(new ActivityDateView(1L, start, end, null, 0).dayLabel()).contains("25 de octubre de 2026");
    }
}
