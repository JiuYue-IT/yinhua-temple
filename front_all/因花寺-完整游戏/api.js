/* Shared session owner: iframe chapters reuse their parent's flow. */
(() => {
  'use strict';
  if (window.parent !== window && window.parent.TempleFlow) {
    window.TempleFlow = window.parent.TempleFlow;
    return;
  }
  class ApiError extends Error {
    constructor(message, code, status = 0) { super(message); this.code = code; this.status = status; }
  }
  const base = String(window.TEMPLE_API_BASE || '').replace(/\/+$/, '');
  const KEY = 'temple.direct-session';
  let saved = null;
  try { saved = JSON.parse(sessionStorage.getItem(KEY)); } catch {}
  let snapshot = saved?.snapshot || null, pending = saved?.pending || null;
  let epoch = 0, timer = null, flight = null, resetFlight = null, lastError = null;
  const listeners = new Set();
  function publish() {
    try { sessionStorage.setItem(KEY, JSON.stringify({ snapshot, pending })); } catch {}
    for (const fn of listeners) fn(snapshot, lastError);
  }
  async function call(path, method = 'GET', body) {
    let response;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      response = await fetch(base + '/api' + path, { method, cache: 'no-store', signal: controller.signal,
        headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
    } catch {
      throw new ApiError('暂时连不上服务，请确认后端已启动，再重试。', 'NETWORK_ERROR');
    } finally { clearTimeout(timeout); }
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new ApiError(data?.error?.message || `请求失败（${response.status}）`, data?.error?.code || 'HTTP_ERROR', response.status);
    if (!data) throw new ApiError('服务返回格式异常，请重试。', 'INVALID_RESPONSE');
    return data;
  }
  function accept(value) { snapshot = value; lastError = null; publish(); }
  function stop() { clearTimeout(timer); timer = null; }
  async function poll(run = epoch) {
    stop();
    if (!snapshot || ['complete', 'error'].includes(snapshot.status)) return;
    try {
      const value = await call('/sessions/' + encodeURIComponent(snapshot.id));
      if (run !== epoch) return;
      accept(value);
    } catch (e) {
      if (run !== epoch) return;
      lastError = e;
      if (e.status === 404) { snapshot = null; pending = null; }
      publish();
    }
    if (run === epoch && snapshot && ['generating', 'sign_drawing'].includes(snapshot.status)) {
      timer = setTimeout(() => poll(run), 1000);
    }
  }
  async function reset() {
    const active = flight;
    ++epoch; stop(); pending = null; snapshot = null; lastError = null; publish();
    flight = null;
    const operation = (async () => { if (active) await active.catch(() => {}); await call('/reset', 'POST'); })();
    resetFlight = operation;
    try { await operation; } finally { if (resetFlight === operation) resetFlight = null; }
  }
  async function submitWish(concern, { preset = false } = {}) {
    concern = String(concern).trim();
    if (!concern || Array.from(concern).length > 400) throw new ApiError('请写下心事或愿望（最多 400 字）。', 'VALIDATION_ERROR');
    if (flight) return flight;
    const request = pending?.wish?.concern === concern && pending.mode === (preset ? 'preset' : 'live') ? pending : {
      requestId: crypto.randomUUID(), experience: 'direct', mode: preset ? 'preset' : 'live',
      ...(preset ? { caseId: 'team-project' } : { wish: { concern, background: null, chosenPath: null, unchosenPath: null, priority: null } })
    };
    const run = ++epoch; stop();
    const operation = (async () => {
      if (resetFlight) await resetFlight;
      if (run !== epoch) throw new ApiError('本次提交已取消。', 'ABORTED');
      if (snapshot || !pending || pending.requestId !== request.requestId) {
        await call('/reset', 'POST');
        if (run !== epoch) throw new ApiError('本次提交已取消。', 'ABORTED');
        snapshot = null;
      }
      pending = request; lastError = null; publish();
      try {
        const value = await call('/sessions', 'POST', request);
        if (run !== epoch) throw new ApiError('本次提交已取消。', 'ABORTED');
        accept(value); pending = null; publish(); poll(run);
        return value;
      } catch (e) {
        if (run === epoch) { lastError = e; publish(); }
        throw e;
      }
    })();
    flight = operation;
    try { return await operation; } finally { if (flight === operation) flight = null; }
  }
  async function readyForSign() {
    const run = epoch, started = Date.now();
    for (;;) {
      if (run !== epoch) throw new ApiError('体验已重新开始。', 'ABORTED');
      if (!snapshot) throw new ApiError('请先回许愿池，写下你的心事。', 'NO_SESSION');
      if (snapshot.status === 'error') throw new ApiError(snapshot.error?.message || '这次回应未能生成，请重试。', snapshot.error?.code);
      if (['ready', 'sign_drawing', 'sign_ready', 'complete'].includes(snapshot.status)) return snapshot;
      if (Date.now() - started > 80000) throw new ApiError('回应仍未完成，请稍后重试。', 'CLIENT_TIMEOUT');
      if (lastError?.status === 404) throw lastError;
      await new Promise(resolve => setTimeout(resolve, 250));
    }
  }
  async function drawSign() {
    const run = epoch;
    let value = await readyForSign();
    if (value.status === 'complete' || value.status === 'sign_ready') return value;
    value = await call('/sessions/' + encodeURIComponent(value.id) + '/sign', 'POST');
    if (run !== epoch) throw new ApiError('体验已重新开始。', 'ABORTED');
    accept(value); poll(run);
    const started = Date.now();
    while (snapshot?.status === 'sign_drawing') {
      if (run !== epoch) throw new ApiError('体验已重新开始。', 'ABORTED');
      if (Date.now() - started > 12000) throw new ApiError('暂时未能取回签文，请重试。', 'CLIENT_TIMEOUT');
      await new Promise(resolve => setTimeout(resolve, 150));
    }
    if (snapshot?.status !== 'sign_ready') throw new ApiError('未能取得签文，请重试。', 'INVALID_STATE');
    return snapshot;
  }
  async function retryGeneration({ preset = false } = {}) {
    const concern = snapshot?.wish?.concern || pending?.wish?.concern;
    if (!concern) throw new ApiError('请先回许愿池，写下心事。', 'NO_SESSION');
    if (snapshot?.status !== 'error' && snapshot) { poll(); return snapshot; }
    return submitWish(concern, { preset });
  }
  async function save() {
    const run = epoch;
    const value = await drawSign();
    if (value.status === 'complete') return value;
    const complete = await call('/sessions/' + encodeURIComponent(value.id) + '/receipt', 'POST', {
      insight: value.reading.summary.message, nextStep: value.reading.detail.nextStep
    });
    if (run !== epoch) throw new ApiError('体验已重新开始。', 'ABORTED');
    accept(complete); return complete;
  }
  window.TempleFlow = { ApiError, call, submitWish, drawSign, retryGeneration, reset, save,
    get snapshot() { return snapshot; }, get error() { return lastError; },
    subscribe(fn) { listeners.add(fn); fn(snapshot, lastError); return () => listeners.delete(fn); }
  };
  if (snapshot) poll();
})();
