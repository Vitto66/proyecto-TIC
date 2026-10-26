// Campos que solo aceptan números (cédula, RUT): no deja escribir letras ni símbolos.
// Si se pega "1.234.567-8", quedan solo los dígitos. El servidor igual vuelve a validar.
(function () {
    function clean(input) {
        let digits = input.value.replace(/\D/g, '');
        if (input.maxLength > 0) digits = digits.slice(0, input.maxLength);
        if (digits !== input.value) input.value = digits;
    }

    document.querySelectorAll('input[data-digits-only]').forEach(input => {
        input.addEventListener('beforeinput', event => {
            if (event.data && /\D/.test(event.data)) event.preventDefault();
        });

        // Al pegar se sacan puntos y guiones antes de aplicar el largo máximo
        input.addEventListener('paste', event => {
            const text = event.clipboardData && event.clipboardData.getData('text');
            if (text == null) return;
            event.preventDefault();
            const digits = text.replace(/\D/g, '');
            input.setRangeText(digits, input.selectionStart, input.selectionEnd, 'end');
            clean(input);
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });

        input.addEventListener('input', () => clean(input));
        clean(input);
    });
})();
