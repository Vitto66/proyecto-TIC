// CU-14: cuando van 2 o más personas, aparece un bloque por cada acompañante para cargar
// nombre, apellido, cédula y teléfono. La persona 1 es el turista, que ya tiene cuenta, salvo que
// marque que es solo el adulto responsable: entonces todos los que van son acompañantes.
// Si se baja la cantidad, lo escrito se guarda por si se vuelve a subir.
// Sin JavaScript también funciona: el servidor arma los bloques y pide completarlos.
(function () {
    const input = document.getElementById('participants');
    const section = document.getElementById('companions-section');
    const list = document.getElementById('companions');
    const template = document.getElementById('companion-template');
    if (!input || !section || !list || !template) return;

    const responsible = document.getElementById('responsibleOnly1') || document.querySelector('input[name="responsibleOnly"]');
    const me = document.getElementById('companion-me');
    const responsibleMe = document.getElementById('responsible-me');
    const MAX_PEOPLE = 50;
    const removed = [];

    function cards() {
        return list.querySelectorAll('[data-companion]');
    }

    function newCard(index) {
        const html = template.innerHTML
            .replaceAll('__I__', String(index))
            .replaceAll('__N__', String(index + firstNumber()));
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html.trim();
        return wrapper.firstElementChild;
    }

    // Adulto responsable: el turista no va, así que todos los participantes son acompañantes
    function responsibleOnly() {
        return responsible ? responsible.checked : false;
    }

    function firstNumber() {
        return responsibleOnly() ? 1 : 2;
    }

    function sync() {
        const people = parseInt(input.value, 10);
        const wanted = Number.isFinite(people)
            ? Math.max(0, Math.min(people, MAX_PEOPLE) - (responsibleOnly() ? 0 : 1)) : 0;

        let current = cards();
        for (let i = current.length - 1; i >= wanted; i--) {
            removed.push(current[i]);
            current[i].remove();
        }
        for (let i = cards().length; i < wanted; i++) {
            list.appendChild(removed.length ? removed.pop() : newCard(i));
        }

        cards().forEach((card, i) => {
            card.querySelectorAll('[data-person-number]').forEach(n => { n.textContent = String(i + firstNumber()); });
        });
        if (me) me.hidden = responsibleOnly();
        if (responsibleMe) responsibleMe.hidden = !responsibleOnly();

        const wasHidden = section.hidden;
        section.hidden = wanted === 0;
        // Al aparecer el bloque, se lleva la vista al primer acompañante vacío
        if (wasHidden && !section.hidden) {
            const first = list.querySelector('input');
            if (first && !first.value) section.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    }

    input.addEventListener('input', sync);
    input.addEventListener('change', sync);
    if (responsible) responsible.addEventListener('change', sync);
    sync();
})();
