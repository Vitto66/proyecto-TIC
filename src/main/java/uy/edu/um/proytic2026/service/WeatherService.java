package uy.edu.um.proytic2026.service;

import uy.edu.um.proytic2026.dto.DailyForecast;

import java.time.LocalDate;
import java.util.Map;

/**
 * Pronóstico del clima para el lugar de una actividad (CU-06 paso 5). Es una interfaz para poder
 * reemplazar la API real en los tests.
 */
public interface WeatherService {

    /**
     * El pronóstico de los próximos días para una localidad de Uruguay.
     *
     * @return un pronóstico por día; vacío si la API no responde o no encuentra el lugar (5a).
     *         Nunca lanza excepciones: sin clima, el detalle se muestra igual.
     */
    Map<LocalDate, DailyForecast> forecast(String locality, String department);
}
