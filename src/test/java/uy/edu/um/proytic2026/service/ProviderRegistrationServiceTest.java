package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.proytic2026.dto.RegisterProviderRequest;
import uy.edu.um.proytic2026.dto.UserResponse;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.DocumentAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.EmailAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-02 Registrarse como prestador. */
@ExtendWith(MockitoExtension.class)
class ProviderRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TouristRepository touristRepository;

    @Mock
    private ProviderRepository providerRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, touristRepository, providerRepository, passwordEncoder);
    }

    private RegisterProviderRequest physical(String email) {
        return RegisterProviderRequest.builder()
                .personType(PersonType.PHYSICAL)
                .firstName(" Juan ").lastName("Rodríguez").document("45678901")
                .address("Ruta 10 km 264, La Paloma")
                .email(email).password("secreta123")
                .build();
    }

    private RegisterProviderRequest legal(String email) {
        return RegisterProviderRequest.builder()
                .personType(PersonType.LEGAL)
                .rut("211234560018").legalName("Laguna Aventuras S.A.").businessName("Kayak Laguna de Rocha")
                .address("Av. Costanera 123, Rocha")
                .email(email).password("secreta123")
                .build();
    }

    private void newAccountIsSaved() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerProvider_physicalPerson_createsPendingProviderWithHashedPassword() {
        newAccountIsSaved();

        UserResponse response = userService.registerProvider(physical(" Juan@Mail.com "));

        assertThat(response.email()).isEqualTo("juan@mail.com");
        assertThat(response.roles()).containsExactly("PROVIDER");
        assertThat(response.providerStatus()).isEqualTo("PENDING");
        assertThat(response.firstName()).isEqualTo("Juan");

        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(providerRepository).save(saved.capture());
        Provider p = saved.getValue();
        assertThat(p.getStatus()).isEqualTo(ProviderStatus.PENDING);
        assertThat(p.getDocument()).isEqualTo("45678901");
        assertThat(p.getAddress()).isEqualTo("Ruta 10 km 264, La Paloma");
        assertThat(p.getRut()).isNull();

        verify(userRepository).save(argThat(u -> passwordEncoder.matches("secreta123", u.getPasswordHash())));
    }

    @Test
    void registerProvider_legalPerson_storesOnlyCompanyData() {
        newAccountIsSaved();
        RegisterProviderRequest request = legal("info@laguna.uy");
        request.setFirstName("ignorado"); // dato de persona física que no corresponde

        userService.registerProvider(request);

        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(providerRepository).save(saved.capture());
        Provider p = saved.getValue();
        assertThat(p.getPersonType()).isEqualTo(PersonType.LEGAL);
        assertThat(p.getRut()).isEqualTo("211234560018");
        assertThat(p.getLegalName()).isEqualTo("Laguna Aventuras S.A.");
        assertThat(p.getBusinessName()).isEqualTo("Kayak Laguna de Rocha");
        assertThat(p.getFirstName()).isNull();
        assertThat(p.getDocument()).isNull();
    }

    @Test
    void registerProvider_emailAlreadyProvider_throwsConflict() {
        User existing = User.builder().id(3L).email("juan@mail.com").passwordHash("x").build();
        existing.setProvider(Provider.builder().user(existing).personType(PersonType.PHYSICAL).build());
        when(userRepository.findByEmailIgnoreCase("juan@mail.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.registerProvider(physical("juan@mail.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("prestador");
        verify(providerRepository, never()).save(any());
    }

    @Test
    void registerProvider_documentTaken_throwsConflictOnDocumentField() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(providerRepository.existsByDocument("45678901")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerProvider(physical("juan@mail.com")))
                .isInstanceOf(DocumentAlreadyRegisteredException.class)
                .extracting("field").isEqualTo("document");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerProvider_rutTaken_throwsConflictOnRutField() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(providerRepository.existsByRut("211234560018")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerProvider(legal("info@laguna.uy")))
                .isInstanceOf(DocumentAlreadyRegisteredException.class)
                .extracting("field").isEqualTo("rut");
    }

    @Test
    void registerProvider_existingTouristWithSamePassword_addsProviderProfile() {
        User existing = User.builder().id(7L).email("juan@mail.com")
                .passwordHash(passwordEncoder.encode("secreta123")).build();
        existing.setTourist(Tourist.builder().user(existing).firstName("Juan").lastName("Rodríguez").build());
        when(userRepository.findByEmailIgnoreCase("juan@mail.com")).thenReturn(Optional.of(existing));
        when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.registerProvider(physical("juan@mail.com"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.roles()).containsExactly("TOURIST", "PROVIDER");
        assertThat(response.providerStatus()).isEqualTo("PENDING");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerProvider_existingTouristWithWrongPassword_throwsInvalidCredentials() {
        User existing = User.builder().id(7L).email("juan@mail.com")
                .passwordHash(passwordEncoder.encode("otraClave99")).build();
        existing.setTourist(Tourist.builder().user(existing).build());
        when(userRepository.findByEmailIgnoreCase("juan@mail.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.registerProvider(physical("juan@mail.com")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(providerRepository, never()).save(any());
    }
}
