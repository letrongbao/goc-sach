(() => {
  const notice = document.getElementById('order-updates');
  if (!notice) return;
  let socket, retry;
  const connect = () => {
    socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}${notice.dataset.endpoint}`);
    socket.onmessage = event => {
      try {
        const data = JSON.parse(event.data);
        if (!data.initial) { notice.hidden = false; notice.focus(); }
      } catch (_) { /* A malformed event never changes order data. */ }
    };
    socket.onclose = event => { if (event.code !== 1008) retry = setTimeout(connect, 10000); };
  };
  connect();
  window.addEventListener('pagehide', () => { clearTimeout(retry); socket.onclose = null; socket.close(); });
})();
