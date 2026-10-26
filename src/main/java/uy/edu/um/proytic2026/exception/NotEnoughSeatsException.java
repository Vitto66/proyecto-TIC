package uy.edu.um.proytic2026.exception;

import lombok.Getter;

/** CU-14 4a: no hay cupos suficientes para esa cantidad de participantes. */
@Getter
public class NotEnoughSeatsException extends RuntimeException {

    /** Lugares que quedan en esa fecha (puede ser 0). */
    private final long available;

    public NotEnoughSeatsException(long available) {
        super("No hay cupos suficientes: quedan " + available);
        this.available = available;
    }
}
