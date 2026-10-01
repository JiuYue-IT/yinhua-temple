/* Loading and recovery copy shared by the two reading stages. */
(() => {
  window.createReadingStatus = (host, retry) => {
    const status = document.createElement('div');
    status.className = 'reading-status'; status.setAttribute('role', 'status');
    const text = document.createElement('p');
    const again = document.createElement('button'); again.type = 'button'; again.textContent = '再试一次';
    const pool = document.createElement('button'); pool.type = 'button'; pool.textContent = '回许愿池';
    const preset = document.createElement('button'); preset.type = 'button'; preset.textContent = '查看预置案例';
    again.onclick = () => retry(false);
    preset.onclick = () => retry(true);
    pool.onclick = () => {
      if (window.parent !== window && window.parent.visitContinuation) window.parent.visitContinuation(1);
      else location.href = '../wish/index.html';
    };
    status.append(text, again, preset, pool); host.append(status);
    return {
      waiting() { status.hidden = false; text.textContent = '回应正在凝成，请稍候。'; again.hidden = preset.hidden = pool.hidden = true; },
      error(e) { status.hidden = false; text.textContent = e.message; again.hidden = e.code === 'NO_SESSION'; preset.hidden = e.code === 'NO_SESSION'; pool.hidden = false; },
      hide() { status.hidden = true; }
    };
  };
})();
