package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.LoginOptions;
import uy.edu.um.proytic2026.entity.Admin;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.ProviderRejectedException;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Tests unitarios del CU-04 Iniciar sesión: credenciales, perfiles disponibles y bloqueos. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "ana@mail.com";
    private static final String PASSWORD = "secreta123";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    // ---------- Armado de cuentas de prueba ----------

    private User account() {
        return User.builder().id(1L).email(EMAIL).passwordHash(passwordEncoder.encode(PASSWORD)).build();
    }

    private User withTourist(User user) {
        user.setTourist(Tourist.builder().user(user).firstName("Ana").lastName("Pérez").nationalId("12345678").build());
        return user;
    }

    private User withProvider(User user, ProviderStatus status) {
        user.setProvider(Provider.builder().user(user).personType(PersonType.PHYSICAL)
                .firstName("Juan").status(status).build());
        return user;
    }

    private User withAdmin(User user) {
        user.setAdmin(Admin.builder().user(user).name("Admin Mnatural").build());
        return user;
    }

    private void accountExists(User user) {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    }

    // ---------- authenticate ----------

    @Test
    void authenticate_validTourist_returnsTouristProfile() {
        accountExists(withTourist(account()));

        LoginOptions options = authService.authenticate(EMAIL, PASSWORD);

        assertThat(options.userId()).isEqualTo(1L);
        assertThat(options.roles()).containsExactly(Role.TOURIST);
    }

    @Test
    void authenticate_emailIsTrimmedAndCaseInsensitive() {
        accountExists(withTourist(account()));

        LoginOptions options = authService.authenticate("  Ana@MAIL.com ", PASSWORD);

        assertThat(options.roles()).containsExactly(Role.TOURIST);
    }

    @Test
    void authenticate_wrongPassword_throwsInvalidCredentials() {
        accountExists(withTourist(account()));

        assertThatThrownBy(() -> authService.authenticate(EMAIL, "otraClave99"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void authenticate_unknownEmail_throwsTheSameErrorAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        // 2a. El mensaje no revela si falló el correo o la contraseña
        assertThatThrownBy(() -> authService.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
    }

    @Test
    void authenticate_rejectedProviderOnly_isBlocked() {
        accountExists(withProvider(account(), ProviderStatus.REJECTED));

        // 2b. Prestador RECHAZADO → se bloquea el acceso
        assertThatThrownBy(() -> authService.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(ProviderRejectedException.class);
    }

    @Test
    void authenticate_rejectedProviderWithWrongPassword_doesNotRevealTheRejection() {
        accountExists(withProvider(account(), ProviderStatus.REJECTED));

        assertThatThrownBy(() -> authService.authenticate(EMAIL, "otraClave99"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void authenticate_rejectedProviderWhoIsAlsoTourist_canOnlyEnterAsTourist() {
        accountExists(withProvider(withTourist(account()), ProviderStatus.REJECTED));

        LoginOptions options = authService.authenticate(EMAIL, PASSWORD);

        assertThat(options.roles()).containsExactly(Role.TOURIST);
    }

    @Test
    void authenticate_pendingProvider_canEnterAsProvider() {
        accountExists(withProvider(account(), ProviderStatus.PENDING));

        assertThat(authService.authenticate(EMAIL, PASSWORD).roles()).containsExactly(Role.PROVIDER);
    }

    @Test
    void authenticate_touristAndProvider_offersBothProfiles() {
        // 2c. El mismo correo con dos perfiles: el sistema pregunta con cuál ingresar
        accountExists(withProvider(withTourist(account()), ProviderStatus.APPROVED));

        assertThat(authService.authenticate(EMAIL, PASSWORD).roles())
                .containsExactly(Role.TOURIST, Role.PROVIDER);
    }

    @Test
    void authenticate_admin_returnsAdminProfile() {
        accountExists(withAdmin(account()));

        assertThat(authService.authenticate(EMAIL, PASSWORD).roles()).containsExactly(Role.ADMIN);
    }

    @Test
    void authenticate_accountWithoutAnyProfile_throwsInvalidCredentials() {
        accountExists(account());

        assertThatThrownBy(() -> authService.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ---------- principalFor ----------

    @Test
    void principalFor_chosenProfile_buildsIdentityWithoutPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(withTourist(account())));

        AuthenticatedUser principal = authService.principalFor(1L, Role.TOURIST);

        assertThat(principal.userId()).isEqualTo(1L);
        assertThat(principal.email()).isEqualTo(EMAIL);
        assertThat(principal.role()).isEqualTo(Role.TOURIST);
        assertThat(principal.displayName()).isEqualTo("Ana");
    }

    @Test
    void principalFor_legalProvider_showsBusinessName() {
        User user = account();
        user.setProvider(Provider.builder().user(user).personType(PersonType.LEGAL)
                .businessName("Kayak Laguna de Rocha").status(ProviderStatus.APPROVED).build());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(authService.principalFor(1L, Role.PROVIDER).displayName()).isEqualTo("Kayak Laguna de Rocha");
    }

    @Test
    void principalFor_roleTheAccountDoesNotHave_throwsInvalidCredentials() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(withTourist(account())));

        // El perfil llega desde el navegador: no se puede entrar como administrador sin serlo
        assertThatThrownBy(() -> authService.principalFor(1L, Role.ADMIN))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void principalFor_rejectedProvider_throwsInvalidCredentials() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(withProvider(account(), ProviderStatus.REJECTED)));

        assertThatThrownBy(() -> authService.principalFor(1L, Role.PROVIDER))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void principalFor_unknownUser_throwsInvalidCredentials() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.principalFor(99L, Role.TOURIST))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ---------- providerStatus ----------

    @Test
    void providerStatus_returnsTheStatusOfTheProviderProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(withProvider(account(), ProviderStatus.PENDING)));

        assertThat(authService.providerStatus(1L)).contains(ProviderStatus.PENDING);
    }

    @Test
    void providerStatus_accountWithoutProviderProfile_isEmpty() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(withTourist(account())));

        assertThat(authService.providerStatus(1L)).isEmpty();
    }
}
