// Replace this listener with audio playback when licensed audio is available.
export function playSound(name) { window.dispatchEvent(new CustomEvent('temple:sound', { detail: { name } })); }
