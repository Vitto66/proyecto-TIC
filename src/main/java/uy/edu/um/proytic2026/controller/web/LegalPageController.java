package uy.edu.um.proytic2026.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Páginas legales: términos y condiciones de uso y política de privacidad.
 * Son públicas (se leen antes de crear la cuenta) y se enlazan desde el pie de página
 * y desde los formularios de registro, donde hay que aceptarlas.
 */
@Controller
public class LegalPageController {

    static final String TERMS_VIEW = "legal-terms";
    static final String PRIVACY_VIEW = "legal-privacy";

    @GetMapping("/terminos")
    public String terms() {
        return TERMS_VIEW;
    }

    @GetMapping("/privacidad")
    public String privacy() {
        return PRIVACY_VIEW;
    }
}
