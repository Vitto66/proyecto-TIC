package uy.edu.um.proytic2026.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Si suben un archivo más grande de lo que acepta el servidor, el pedido se corta antes de
 * llegar al formulario. En lugar de una página de error, se vuelve a "Mis actividades" con un aviso.
 * (Va sin filtro de controladores porque el error ocurre antes de saber qué controlador atiende.)
 */
@ControllerAdvice
public class UploadLimitAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request) {
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("activityLevel", "warning");
        flash.put("activityMessage",
                "La foto era demasiado pesada y no se guardó ningún cambio. Elegí una foto de menos de 3 MB.");
        return "redirect:/provider/activities";
    }
}
