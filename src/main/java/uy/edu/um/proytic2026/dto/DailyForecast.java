package uy.edu.um.proytic2026.dto;

import java.time.LocalDate;

/**
 * Pronóstico de un día para el lugar de una actividad (CU-06 paso 5).
 *
 * @param weatherCode              código del clima de la OMM (WMO) que usa Open-Meteo.
 * @param precipitationProbability probabilidad máxima de lluvia del día, en %, o null si no se sabe.
 */
public record DailyForecast(LocalDate date, int weatherCode, double maxTemperature, double minTemperature,
                            Integer precipitationProbability) {

    /** Descripción en español del código del clima. */
    public String description() {
        return switch (weatherCode) {
            case 0 -> "Despejado";
            case 1 -> "Mayormente despejado";
            case 2 -> "Parcialmente nublado";
            case 3 -> "Nublado";
            case 45, 48 -> "Niebla";
            case 51, 53, 55, 56, 57 -> "Llovizna";
            case 61, 66 -> "Lluvia débil";
            case 63 -> "Lluvia";
            case 65, 67 -> "Lluvia fuerte";
            case 71, 73, 75, 77, 85, 86 -> "Nieve";
            case 80, 81 -> "Chaparrones";
            case 82 -> "Chaparrones fuertes";
            case 95, 96, 97, 99 -> "Tormenta";
            default -> "Sin datos";
        };
    }

    /** Un ícono simple para el clima. */
    public String icon() {
        if (weatherCode <= 1) return "☀️";
        if (weatherCode == 2) return "⛅";
        if (weatherCode == 3 || weatherCode == 45 || weatherCode == 48) return "☁️";
        if (weatherCode >= 95) return "⛈️";
        if (weatherCode >= 71 && weatherCode <= 77 || weatherCode == 85 || weatherCode == 86) return "❄️";
        return "🌧️";
    }

    /** "24° / 15°". */
    public String temperatureLabel() {
        return Math.round(maxTemperature) + "° / " + Math.round(minTemperature) + "°";
    }

    /** "20 % de lluvia", o null si no se sabe. */
    public String rainLabel() {
        return precipitationProbability == null ? null : precipitationProbability + " % de lluvia";
    }
}
