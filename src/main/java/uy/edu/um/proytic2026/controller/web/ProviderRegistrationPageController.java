package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.dto.RegisterProviderRequest;
import uy.edu.um.proytic2026.exception.DocumentAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.EmailAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.service.UserService;
import uy.edu.um.proytic2026.validation.ProviderRegistrationValidator;

/**
 * Página del formulario de registro de prestadores (CU-02).
 * Usa el mismo servicio y las mismas validaciones que POST /api/auth/register/provider.
 */
@Controller
@RequestMapping("/register/provider")
@RequiredArgsConstructor
public class ProviderRegistrationPageController {

    static final String FORM_VIEW = "register-provider";
    static final String SENT_VIEW = "register-provider-sent";

    private final UserService userService;
    private final ProviderRegistrationValidator providerRegistrationValidator;

    @InitBinder("form")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(providerRegistrationValidator);
    }

    @GetMapping
    public String showForm(@ModelAttribute("form") RegisterProviderRequest form) {
        return FORM_VIEW;
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("form") RegisterProviderRequest form, BindingResult result) {
        if (result.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            userService.registerProvider(form);
        } catch (EmailAlreadyRegisteredException ex) {
            // 5a. Correo ya usado como prestador → se informa y se ofrece iniciar sesión
            result.rejectValue("email", "taken", "ya está registrado como prestador. Iniciá sesión con esa cuenta");
            return FORM_VIEW;
        } catch (DocumentAlreadyRegisteredException ex) {
            result.rejectValue(ex.getField(), "taken", "ya está registrado por otro prestador");
            return FORM_VIEW;
        } catch (InvalidCredentialsException ex) {
            // El correo ya es de un turista: para sumarle el perfil de prestador, la contraseña tiene que ser la misma
            result.rejectValue("password", "mismatch",
                    "ese correo ya tiene una cuenta de turista. Ingresá la misma contraseña para agregarle el perfil de prestador");
            return FORM_VIEW;
        }
        // Post/Redirect/Get: si recargan la página de confirmación no se reenvía el formulario
        return "redirect:/register/provider/sent";
    }

    @GetMapping("/sent")
    public String sent() {
        return SENT_VIEW;
    }
}
