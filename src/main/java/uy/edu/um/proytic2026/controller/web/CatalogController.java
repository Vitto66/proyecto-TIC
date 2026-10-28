package uy.edu.um.proytic2026.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.proytic2026.dto.ActivitySearch;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.CatalogCalendar;
import uy.edu.um.proytic2026.service.ActivityPhotoService;
import uy.edu.um.proytic2026.service.CatalogService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-05 Explorar actividades publicadas: la vidriera pública. No hace falta cuenta (RF-03);
 * SecurityConfig deja /activities abierto.
 */
@Controller
@RequiredArgsConstructor
public class CatalogController {

    static final String VIEW = "catalog";

    private final CatalogService catalogService;
    private final ActivityPhotoService photoService;

    /**
     * La vidriera, como lista de tarjetas o como calendario con las fechas disponibles
     * (view=calendar, y month=2026-11 para elegir el mes). Los filtros sirven para las dos vistas.
     */
    @GetMapping("/activities")
    public String explore(@ModelAttribute("search") ActivitySearch search, BindingResult binding,
                          @RequestParam(required = false) String view, @RequestParam(required = false) String month,
                          @AuthenticationPrincipal AuthenticatedUser current, Model model, HttpServletRequest request) {
        boolean calendarView = "calendar".equals(view);
        String path = request.getContextPath() + "/activities";
        String query = request.getQueryString();
        model.addAttribute("calendarView", calendarView);
        model.addAttribute("listUrl", withParams(path, query, Map.of("view", "", "month", "")));
        model.addAttribute("calendarUrl", withParams(path, query, Map.of("view", "calendar", "month", "")));
        if (calendarView) {
            CatalogCalendar calendar = catalogService.calendar(search, parseMonth(month));
            model.addAttribute("calendar", calendar);
            if (calendar.prevMonth() != null) {
                model.addAttribute("prevMonthUrl", withParams(path, query, Map.of("view", "calendar", "month", calendar.prevMonth())));
            }
            model.addAttribute("nextMonthUrl", withParams(path, query, Map.of("view", "calendar", "month", calendar.nextMonth())));
        }
        // Un filtro con un valor que no se entiende (texto en el precio, por ejemplo) queda vacío
        // y se avisa, pero el resto de los filtros se aplica igual.
        model.addAttribute("invalidFilters", binding.hasErrors());
        model.addAttribute("result", catalogService.search(search));
        model.addAttribute("photos", photoService.urls());
        model.addAttribute("filters", catalogService.filters());
        model.addAttribute("currentUser", current);
        if (current != null) {
            model.addAttribute("spacePath", RoleHome.pathFor(current.role()));
        }
        return VIEW;
    }

    private static YearMonth parseMonth(String value) {
        try {
            return value == null || value.isBlank() ? null : YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * La misma búsqueda con algunos parámetros cambiados: los de {@code changes} se reemplazan (un
     * valor vacío los saca) y se omiten los filtros vacíos, así la dirección queda limpia.
     */
    static String withParams(String path, String query, Map<String, String> changes) {
        List<String> parts = new ArrayList<>();
        if (query != null) {
            for (String part : query.split("&")) {
                String key = part.split("=", 2)[0];
                if (part.isEmpty() || part.endsWith("=") || !part.contains("=") || changes.containsKey(key)) continue;
                parts.add(part);
            }
        }
        new LinkedHashMap<>(changes).forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                parts.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return parts.isEmpty() ? path : path + "?" + String.join("&", parts);
    }
}
