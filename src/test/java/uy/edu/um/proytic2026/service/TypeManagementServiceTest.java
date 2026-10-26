package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.TypeRow;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.EquipmentType;
import uy.edu.um.proytic2026.entity.TypeCategory;
import uy.edu.um.proytic2026.exception.TypeInUseException;
import uy.edu.um.proytic2026.exception.TypeNameTakenException;
import uy.edu.um.proytic2026.exception.TypeNotFoundException;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.EquipmentTypeRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-13 Gestionar tipos de actividad y de equipamiento. */
@ExtendWith(MockitoExtension.class)
class TypeManagementServiceTest {

    @Mock private ActivityTypeRepository activityTypeRepository;
    @Mock private EquipmentTypeRepository equipmentTypeRepository;
    @Mock private ActivityRepository activityRepository;

    private TypeManagementService service;

    @BeforeEach
    void setUp() {
        service = new TypeManagementService(activityTypeRepository, equipmentTypeRepository, activityRepository);
    }

    @Test
    void list_includesInactiveTypesAndTheirUsage() {
        when(activityTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                ActivityType.builder().id(1).name("Cabalgata").active(false).build(),
                ActivityType.builder().id(2).name("Trekking").build()));
        when(activityRepository.countByActivityType()).thenReturn(List.<Object[]>of(new Object[]{2, 3L}));

        List<TypeRow> rows = service.list(TypeCategory.ACTIVITY);

        assertThat(rows).containsExactly(
                new TypeRow(1, "Cabalgata", false, 0),
                new TypeRow(2, "Trekking", true, 3));
        assertThat(rows.get(1).usageLabel()).isEqualTo("3 actividades");
    }

    @Test
    void create_normalizesTheNameAndSavesItActive() {
        String name = service.create(TypeCategory.EQUIPMENT, "  Linterna   frontal ");

        assertThat(name).isEqualTo("Linterna frontal");
        verify(equipmentTypeRepository).save(argThat(t -> t.getName().equals("Linterna frontal") && t.isActive()));
    }

    @Test
    void create_duplicateIgnoringCase_throws() {
        when(activityTypeRepository.existsByNameIgnoreCase("trekking")).thenReturn(true);

        assertThatThrownBy(() -> service.create(TypeCategory.ACTIVITY, "trekking"))
                .isInstanceOf(TypeNameTakenException.class);
        verify(activityTypeRepository, never()).save(any());
    }

    @Test
    void create_blankOrTooLong_throws() {
        assertThatThrownBy(() -> service.create(TypeCategory.ACTIVITY, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(TypeCategory.ACTIVITY, "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rename_allowsChangingOnlyTheCaseOfItsOwnName() {
        ActivityType type = ActivityType.builder().id(7).name("trekking").build();
        when(activityTypeRepository.existsByNameIgnoreCaseAndIdNot("Trekking", 7)).thenReturn(false);
        when(activityTypeRepository.findById(7)).thenReturn(Optional.of(type));

        service.rename(TypeCategory.ACTIVITY, 7, "Trekking");

        assertThat(type.getName()).isEqualTo("Trekking");
    }

    @Test
    void rename_toAnotherTypesName_throws() {
        when(equipmentTypeRepository.existsByNameIgnoreCaseAndIdNot("Casco", 3)).thenReturn(true);

        assertThatThrownBy(() -> service.rename(TypeCategory.EQUIPMENT, 3, "Casco"))
                .isInstanceOf(TypeNameTakenException.class);
    }

    @Test
    void delete_unusedType_deletesIt() {
        ActivityType type = ActivityType.builder().id(4).name("Golf").build();
        when(activityTypeRepository.findById(4)).thenReturn(Optional.of(type));
        when(activityRepository.countUsingActivityType(4)).thenReturn(0L);

        assertThat(service.delete(TypeCategory.ACTIVITY, 4)).isEqualTo("Golf");
        verify(activityTypeRepository).delete(type);
    }

    @Test
    void delete_typeInUse_throwsAndKeepsIt() {
        EquipmentType casco = EquipmentType.builder().id(3).name("Casco").build();
        when(equipmentTypeRepository.findById(3)).thenReturn(Optional.of(casco));
        when(activityRepository.countUsingEquipmentType(3)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(TypeCategory.EQUIPMENT, 3))
                .isInstanceOfSatisfying(TypeInUseException.class, ex -> {
                    assertThat(ex.getTypeName()).isEqualTo("Casco");
                    assertThat(ex.getUsageLabel()).isEqualTo("2 actividades");
                });
        verify(equipmentTypeRepository, never()).delete(any());
    }

    @Test
    void setActive_changesTheFlag() {
        ActivityType type = ActivityType.builder().id(5).name("Kayak").build();
        when(activityTypeRepository.findById(5)).thenReturn(Optional.of(type));

        service.setActive(TypeCategory.ACTIVITY, 5, false);

        assertThat(type.isActive()).isFalse();
    }

    @Test
    void unknownType_throwsNotFound() {
        when(activityTypeRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(TypeCategory.ACTIVITY, 99, true))
                .isInstanceOf(TypeNotFoundException.class);
    }
}
