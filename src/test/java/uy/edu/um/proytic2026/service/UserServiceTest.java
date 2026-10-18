package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;
import uy.edu.um.proytic2026.dto.UserResponse;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.EmailAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.NationalIdAlreadyRegisteredException;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Tests unitarios del CU-01 Registrarse como turista. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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

    private RegisterTouristRequest request(String email, String password) {
        return new RegisterTouristRequest("Ana", "Pérez", "12345678", email, password, null);
    }

    @Test
    void registerTourist_newEmail_createsAccountWithHashedPassword() {
        when(userRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(touristRepository.save(any(Tourist.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.registerTourist(request("  Ana@Mail.com ", "secreta123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("ana@mail.com");
        assertThat(response.roles()).containsExactly("TOURIST");
        verify(userRepository).save(argThat(u ->
                !u.getPasswordHash().equals("secreta123")
                        && passwordEncoder.matches("secreta123", u.getPasswordHash())));
    }

    @Test
    void registerTourist_emailAlreadyTourist_throwsConflict() {
        User existing = User.builder().id(1L).email("ana@mail.com").passwordHash("x").build();
        existing.setTourist(Tourist.builder().user(existing).build());
        when(userRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.registerTourist(request("ana@mail.com", "secreta123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(touristRepository, never()).save(any());
    }

    @Test
    void registerTourist_nationalIdTaken_throwsConflict() {
        when(touristRepository.existsByNationalId("12345678")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerTourist(request("ana@mail.com", "secreta123")))
                .isInstanceOf(NationalIdAlreadyRegisteredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerTourist_existingProviderWithSamePassword_addsTouristProfile() {
        User existing = User.builder().id(7L).email("ana@mail.com")
                .passwordHash(passwordEncoder.encode("secreta123")).build();
        existing.setProvider(Provider.builder().user(existing).personType(PersonType.PHYSICAL).build());
        when(userRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(existing));
        when(touristRepository.save(any(Tourist.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.registerTourist(request("ana@mail.com", "secreta123"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.roles()).containsExactly("TOURIST", "PROVIDER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerTourist_existingProviderWithWrongPassword_throwsInvalidCredentials() {
        User existing = User.builder().id(7L).email("ana@mail.com")
                .passwordHash(passwordEncoder.encode("otraClave99")).build();
        existing.setProvider(Provider.builder().user(existing).personType(PersonType.PHYSICAL).build());
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.registerTourist(request("ana@mail.com", "secreta123")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(touristRepository, never()).save(any());
    }
}
