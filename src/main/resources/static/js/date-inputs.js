// Campos de fecha en formato día/mes/año (dd/mm/aaaa), sin importar el idioma del navegador.
// El campo nativo <input type="date"> muestra el formato del sistema (en un navegador en inglés,
// mm/dd/yyyy). Con flatpickr se ve y se escribe dd/mm/aaaa, y al servidor se sigue mandando
// aaaa-mm-dd, así que no cambia nada del lado del servidor.
// Si flatpickr no carga (sin internet), queda el campo de fecha del navegador y todo sigue andando.
(function () {
    if (typeof window.flatpickr !== 'function') return;

    document.querySelectorAll('input[type="date"]').forEach(input => {
        const id = input.id;
        const picker = window.flatpickr(input, {
            locale: 'es',
            dateFormat: 'Y-m-d',   // lo que se envía
            altInput: true,
            altFormat: 'd/m/Y',    // lo que se ve: 25/12/2026
            allowInput: true,      // también se puede escribir
            disableMobile: true,   // el mismo selector en el celular
            minDate: input.min || null,
            maxDate: input.max || null,
            // Si el servidor había marcado el campo con error, se limpia al elegir otra fecha
            onChange: (dates, value, instance) => instance.altInput.classList.remove('is-invalid')
        });

        // El campo visible pasa a ser el que tiene el id (para la etiqueta) y el "obligatorio"
        const visible = picker.altInput;
        visible.placeholder = 'dd/mm/aaaa';
        visible.required = input.required;
        visible.setAttribute('inputmode', 'numeric');
        visible.setAttribute('autocomplete', 'off');
        if (id) {
            input.id = id + '-value';
            visible.id = id;
        }
    });
})();
