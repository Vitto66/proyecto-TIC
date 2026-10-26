// Campos que solo aceptan números (cédula, RUT): no deja escribir letras ni símbolos.
// Si se pega "1.234.567-8", quedan solo los dígitos. El servidor igual vuelve a validar.
// Escucha en todo el documento, así también funciona con campos que se agregan después
// (por ejemplo, las cédulas de los acompañantes al reservar).
(function () {
    const SELECTOR = 'input[data-digits-only]';

    function clean(input) {
        let digits = input.value.replace(/\D/g, '');
        if (input.maxLength > 0) digits = digits.slice(0, input.maxLength);
        if (digits !== input.value) input.value = digits;
    }

    document.addEventListener('beforeinput', event => {
        if (event.target.matches && event.target.matches(SELECTOR)
                && event.data && /\D/.test(event.data)) {
            event.preventDefault();
        }
    });

    // Al pegar se sacan puntos y guiones antes de aplicar el largo máximo
    document.addEventListener('paste', event => {
        const input = event.target;
        if (!input.matches || !input.matches(SELECTOR)) return;
        const text = event.clipboardData && event.clipboardData.getData('text');
        if (text == null) return;
        event.preventDefault();
        input.setRangeText(text.replace(/\D/g, ''), input.selectionStart, input.selectionEnd, 'end');
        clean(input);
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });

    document.addEventListener('input', event => {
        if (event.target.matches && event.target.matches(SELECTOR)) clean(event.target);
    });

    document.querySelectorAll(SELECTOR).forEach(clean);
})();
