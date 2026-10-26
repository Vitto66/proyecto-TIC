// CU-15: formulario de tarjeta de Mercado Pago (Card Payment Brick).
// Con la clave pública, Mercado Pago convierte la tarjeta en un token en el navegador: el número y
// el código de seguridad no pasan por nuestro servidor. Acá solo se manda el token, y el servidor
// cobra con el access token (que es secreto y nunca llega al navegador).
(function () {
    const container = document.getElementById('cardPaymentBrick_container');
    const form = document.getElementById('mp-pay-form');
    const loading = document.getElementById('mp-loading');
    const error = document.getElementById('mp-error');
    if (!container || !form) return;

    function fail(reason) {
        if (reason) console.error('Mercado Pago:', reason);
        if (loading) loading.remove();
        if (error) error.hidden = false;
    }

    if (typeof MercadoPago === 'undefined') {
        fail('no cargó el SDK (https://sdk.mercadopago.com/js/v2)');
        return;
    }

    const mp = new MercadoPago(container.dataset.publicKey, { locale: 'es-UY' });

    mp.bricks().create('cardPayment', 'cardPaymentBrick_container', {
        initialization: {
            amount: Number(container.dataset.amount),
            payer: { email: container.dataset.email }
        },
        customization: {
            paymentMethods: { maxInstallments: 1 },
            visual: { style: { theme: 'default' } }
        },
        callbacks: {
            onReady: () => { if (loading) loading.remove(); },
            onSubmit: (arg) => {
                // Según la versión, llega el formulario directo o dentro de { formData }
                const data = arg && arg.formData ? arg.formData : arg;
                const identification = (data.payer && data.payer.identification) || {};
                form.elements.token.value = data.token || '';
                form.elements.paymentMethodId.value = data.payment_method_id || '';
                form.elements.issuerId.value = data.issuer_id || '';
                form.elements.installments.value = data.installments || 1;
                form.elements.documentType.value = identification.type || '';
                form.elements.documentNumber.value = identification.number || '';
                form.submit();
                // La página se va a recargar con el resultado: el formulario queda "procesando"
                return new Promise(() => {});
            },
            onError: (err) => console.error('Mercado Pago:', err)
        }
    }).catch(fail);
})();
