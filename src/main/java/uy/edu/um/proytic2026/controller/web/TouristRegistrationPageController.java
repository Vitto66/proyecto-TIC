package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;
import uy.edu.um.proytic2026.exception.EmailAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.NationalIdAlreadyRegisteredException;
import uy.edu.um.proytic2026.service.UserService;
import uy.edu.um.proytic2026.validation.TouristRegistrationValidator;

/**
 * Página del formulario de registro de turistas (CU-01).
 * Usa el mismo servicio y las mismas validaciones que POST /api/auth/register/tourist.
 */
@Controller
@RequestMapping("/register/tourist")
@RequiredArgsConstructor
public class TouristRegistrationPageController {

    static final String FORM_VIEW = "register-tourist";
    static final String DONE_VIEW = "register-tourist-done";

    private final UserService userService;
    private final TouristRegistrationValidator touristRegistrationValidator;

    @InitBinder("form")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(touristRegistrationValidator);
    }

    @GetMapping
    public String showForm(@ModelAttribute("form") RegisterTouristRequest form) {
        return FORM_VIEW;
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("form") RegisterTouristRequest form, BindingResult result) {
        // 4b. Datos inválidos → se marcan los campos
        if (result.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            userService.registerTourist(form);
        } catch (EmailAlreadyRegisteredException ex) {
            // 4a. Correo ya registrado → se informa y se ofrece iniciar sesión
            result.rejectValue("email", "taken", "ya tiene una cuenta de turista. Iniciá sesión con esa cuenta");
            return FORM_VIEW;
        } catch (NationalIdAlreadyRegisteredException ex) {
            result.rejectValue("nationalId", "taken", "ya está registrada por otro turista");
            return FORM_VIEW;
        } catch (InvalidCredentialsException ex) {
            // El correo ya es de un prestador: para sumarle el perfil de turista, la contraseña tiene que ser la misma
            result.rejectValue("password", "mismatch",
                    "ese correo ya tiene una cuenta de prestador. Ingresá la misma contraseña para agregarle el perfil de turista");
            return FORM_VIEW;
        } catch (DataIntegrityViolationException ex) {
            // Dos registros simultáneos con el mismo correo o cédula: la restricción UNIQUE de la base lo frena
            result.reject("duplicate", "Ya existe una cuenta con ese correo o esa cédula. Revisá los datos e intentá de nuevo.");
            return FORM_VIEW;
        }
        // Post/Redirect/Get: si recargan la página de confirmación no se reenvía el formulario
        return "redirect:/register/tourist/done";
    }

    @GetMapping("/done")
    public String done() {
        return DONE_VIEW;
    }
}
