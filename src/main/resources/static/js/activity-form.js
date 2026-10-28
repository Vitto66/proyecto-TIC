// Formulario del CU-08 Publicar una actividad.
// El departamento filtra las localidades, y se valida antes de enviar (incluido que haya al
// menos un tipo de actividad). Las validaciones definitivas las hace el servidor (ActivityService).
(function () {
    const form = document.getElementById('activity-form');
    if (!form) return;

    // ---------- Departamento → localidades ----------
    const department = document.getElementById('departmentId');
    const locality = document.getElementById('localityId');
    const emptyHelp = document.getElementById('locality-help');
    const localityOptions = Array.from(locality.querySelectorAll('option[data-department]'));

    function filterLocalities() {
        const selected = department.value;
        let available = 0;
        localityOptions.forEach(option => {
            const matches = selected !== '' && option.dataset.department === selected;
            option.hidden = !matches;
            option.disabled = !matches;
            if (matches) available++;
        });
        const current = locality.selectedOptions[0];
        if (current && current.disabled) locality.value = '';
        locality.disabled = selected === '' || available === 0;
        emptyHelp.hidden = !(selected !== '' && available === 0);
    }

    department.addEventListener('change', filterLocalities);

    // ---------- Al menos un tipo de actividad ----------
    const typesFieldset = document.getElementById('types-fieldset');
    const typeBoxes = Array.from(form.querySelectorAll('input[name="activityTypeIds"]'));

    function typesMissing() {
        return typeBoxes.length > 0 && !typeBoxes.some(box => box.checked);
    }

    typeBoxes.forEach(box => box.addEventListener('change', () => {
        typesFieldset.classList.toggle('is-missing', typesMissing());
    }));

    // Al tocar un campo marcado por el servidor, se saca la marca de error
    form.querySelectorAll('.is-invalid').forEach(field => {
        const clear = () => field.classList.remove('is-invalid');
        field.addEventListener('input', clear, { once: true });
        field.addEventListener('change', clear, { once: true });
    });

    form.addEventListener('submit', event => {
        const missing = typesMissing();
        typesFieldset.classList.toggle('is-missing', missing);
        if (!form.checkValidity() || missing) {
            event.preventDefault();
            event.stopPropagation();
            const firstInvalid = form.querySelector(':invalid') || (missing ? typeBoxes[0] : null);
            if (firstInvalid) firstInvalid.focus();
        }
        form.classList.add('was-validated');
    });

    filterLocalities();

    // ---------- Foto de portada ----------
    // Se muestra la vista previa y, si es grande, se achica acá (máx. 1600 px de ancho, JPG) para
    // que suba rápido y entre en el límite de 3 MB. Sin JavaScript se sube tal cual.
    const photo = document.getElementById('photo');
    if (photo) {
        const MAX_BYTES = 3 * 1024 * 1024;
        const MAX_SIDE = 1600;
        const previewBox = document.getElementById('photo-preview');
        const previewImg = document.getElementById('photo-preview-img');
        const emptyText = previewBox.querySelector('.photo-empty');
        const nameText = document.getElementById('photo-name');
        const errorBox = document.getElementById('photo-error-js');
        const remove = document.getElementById('removePhoto');
        const originalSrc = previewImg.getAttribute('src');

        function showError(message) {
            errorBox.textContent = message;
            errorBox.hidden = !message;
        }

        function showPreview(src) {
            previewImg.hidden = !src;
            if (src) previewImg.src = src; else previewImg.removeAttribute('src');
            if (emptyText) emptyText.hidden = !!src;
            previewBox.classList.toggle('has-photo', !!src);
        }

        function sizeLabel(bytes) {
            return bytes > 1024 * 1024 ? (bytes / 1024 / 1024).toFixed(1).replace('.', ',') + ' MB'
                : Math.max(1, Math.round(bytes / 1024)) + ' KB';
        }

        function loadImage(file) {
            return new Promise((resolve, reject) => {
                const img = new Image();
                img.onload = () => resolve(img);
                img.onerror = reject;
                img.src = URL.createObjectURL(file);
            });
        }

        async function shrink(file) {
            const img = await loadImage(file);
            const scale = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
            if (scale === 1 && file.size <= 600 * 1024) return file; // ya es liviana
            const canvas = document.createElement('canvas');
            canvas.width = Math.round(img.naturalWidth * scale);
            canvas.height = Math.round(img.naturalHeight * scale);
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = '#ffffff'; // las partes transparentes de un PNG quedan blancas
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
            if (!blob || blob.size >= file.size) return file;
            return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', {type: 'image/jpeg'});
        }

        photo.addEventListener('change', async () => {
            showError('');
            const file = photo.files[0];
            if (!file) {
                showPreview(originalSrc);
                nameText.textContent = '';
                return;
            }
            if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
                photo.value = '';
                showPreview(originalSrc);
                nameText.textContent = '';
                showError('Elegí una imagen JPG, PNG o WebP.');
                return;
            }
            if (remove) remove.checked = false;
            nameText.textContent = 'Preparando la foto…';
            let ready = file;
            try {
                ready = await shrink(file);
                if (ready !== file && window.DataTransfer) {
                    const list = new DataTransfer();
                    list.items.add(ready);
                    photo.files = list.files;
                }
            } catch (e) {
                ready = file; // si el navegador no puede achicarla, se sube como está
            }
            showPreview(URL.createObjectURL(ready));
            nameText.textContent = ready.name + ' · ' + sizeLabel(ready.size);
            if (ready.size > MAX_BYTES) {
                photo.value = '';
                showPreview(originalSrc);
                nameText.textContent = '';
                showError('La foto pesa más de 3 MB. Elegí una más liviana.');
            }
        });

        // Sacar la foto: la vista previa queda vacía (se puede volver a marcar)
        if (remove) {
            remove.addEventListener('change', () => {
                if (remove.checked) {
                    photo.value = '';
                    nameText.textContent = '';
                }
                showPreview(remove.checked ? null : originalSrc);
            });
        }
    }
})();
