package uy.edu.um.proytic2026.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Seguridad de la aplicación (CU-04 y RF-02).
 * <p>
 * El inicio de sesión no usa el formulario automático de Spring Security: lo maneja
 * LoginPageController, porque un mismo correo puede tener varios perfiles y hay que
 * preguntar con cuál se ingresa. Spring Security se ocupa de la sesión, de los roles
 * y de proteger las rutas.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Donde se guarda la identidad en la sesión HTTP. LoginPageController lo usa al iniciar sesión. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Las cuentas se validan en AuthService, no acá. Declarar este bean evita que Spring Boot
     * cree un usuario de prueba con una contraseña generada; no encuentra a nadie a propósito.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("El inicio de sesión se valida en AuthService");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Vidriera pública: se navega sin cuenta (RF-03)
                        .requestMatchers("/", "/login", "/login/**", "/register/**", "/error",
                                "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                        // Vidriera de actividades y su detalle (CU-05, CU-06)
                        .requestMatchers(HttpMethod.GET, "/activities", "/activities/**").permitAll()
                        // API para otras plataformas (RF-10): de solo lectura y sin usuario
                        .requestMatchers("/api/**").permitAll()
                        // Consola de la base H2, solo existe en desarrollo
                        .requestMatchers("/h2-console/**").permitAll()
                        // Cada perfil entra únicamente a su espacio
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/provider/**").hasRole("PROVIDER")
                        .requestMatchers("/tourist/**").hasRole("TOURIST")
                        .anyRequest().authenticated())
                // Los formularios de Thymeleaf mandan el token CSRF solos; la API lo recibe de otros sistemas
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                // Quien no inició sesión y pide una página protegida va al login
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout"));
        return http.build();
    }
}
