package uy.edu.um.proytic2026.dto;

/** Un acompañante de la reserva para mostrar en pantalla. */
public record CompanionView(int position, String firstName, String lastName, String nationalId, String phone) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
