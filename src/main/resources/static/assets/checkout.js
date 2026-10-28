(() => {
  let pending = false;
  document.addEventListener('change', async event => {
    if (event.target.id !== 'carrierId' || pending) return;
    const root = document.getElementById('checkout');
    const form = document.getElementById('checkout-quote');
    const address = root.querySelector('#addressId').value;
    const payment = root.querySelector('[name="paymentMethod"]:checked')?.value;
    const previousCarrier = root.querySelector('input[name="carrierId"]').value;
    const controls = [...root.querySelectorAll('input, select, button')];
    const states = controls.map(control => control.disabled);
    const url = new URL(form.action, location.href);
    url.search = new URLSearchParams(new FormData(form)).toString();
    pending = true;
    root.setAttribute('aria-busy', 'true');
    controls.forEach(control => { control.disabled = true; });
    const feedback = root.querySelector('#quote-feedback');
    feedback.textContent = 'Đang cập nhật phí giao hàng…';
    try {
      const response = await fetch(url, { credentials: 'same-origin' });
      const page = new DOMParser().parseFromString(await response.text(), 'text/html');
      const next = page.getElementById('checkout');
      const error = page.querySelector('.notice-stack .alert-danger');
      if (!response.ok || !next || error) throw new Error('quote');
      next.querySelector('#addressId').value = address;
      for (const radio of next.querySelectorAll('[name="paymentMethod"]')) {
        radio.checked = radio.value === payment;
      }
      next.querySelector('#quote-feedback').textContent = 'Đã cập nhật phí giao hàng.';
      root.replaceWith(next);
      next.querySelector('#carrierId').focus();
      history.replaceState(null, '', url);
    } catch (_) {
      root.querySelector('#carrierId').value = previousCarrier;
      controls.forEach((control, index) => { control.disabled = states[index]; });
      root.removeAttribute('aria-busy');
      feedback.textContent = 'Chưa cập nhật được phí giao hàng. Vui lòng chọn lại.';
    } finally {
      pending = false;
    }
  });
})();
