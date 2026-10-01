// Distances are centimetres. Adapters call triggerSensor; views only subscribe.
export const DEBUG_HARDWARE = import.meta.env?.VITE_DEBUG_HARDWARE !== 'false';
export function createHardwareBridge() {
  const listeners = new Map();
  let inside = false;
  const emit = (event, payload) => listeners.get(event)?.forEach(fn => fn(payload));
  const bridge = {
    subscribe(event, callback) { if (!listeners.has(event)) listeners.set(event,new Set()); listeners.get(event).add(callback); return () => listeners.get(event)?.delete(callback); },
    onSensorEnter() { if (!inside) { inside = true; emit('enter'); } },
    onSensorLeave() { if (inside) { inside = false; emit('leave'); } },
    onSensorDistanceChange(distance) { if (!Number.isFinite(distance) || distance < 0) return; emit('distance', distance); if (distance <= 25) bridge.onSensorEnter(); else if (distance >= 32) bridge.onSensorLeave(); },
    triggerSensor({ distance, detected }) { if (detected === false) { bridge.onSensorLeave(); return; } if (Number.isFinite(distance)) bridge.onSensorDistanceChange(distance); else if (detected === true) bridge.onSensorEnter(); },
    triggerIgnite() { emit('ignite'); },
    reset() { bridge.onSensorLeave(); },
    get detected() { return inside; },
  };
  return bridge;
}
export const hardwareBridge = createHardwareBridge();
export const triggerSensor = payload => hardwareBridge.triggerSensor(payload);
