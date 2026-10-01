(() => {
 // Block destination connections as well as suspending contexts.
 if(window.AudioNode){const connect=AudioNode.prototype.connect;AudioNode.prototype.connect=function(destination,...args){if(destination===this.context.destination)return destination;return connect.call(this,destination,...args);};}
 const Native=window.AudioContext||window.webkitAudioContext;
 if(Native){function SilentContext(...args){const ctx=new Native(...args);ctx.suspend().catch(()=>{});ctx.resume=()=>Promise.resolve();return ctx;}SilentContext.prototype=Native.prototype;window.AudioContext=SilentContext;window.webkitAudioContext=SilentContext;}
 const play=HTMLMediaElement.prototype.play;HTMLMediaElement.prototype.play=function(){this.muted=true;this.volume=0;return play.call(this);};
 const silence=()=>document.querySelectorAll('audio,video').forEach(m=>{m.muted=true;m.volume=0;});
 document.addEventListener('DOMContentLoaded',silence);document.addEventListener('play',silence,true);
 const style=document.createElement('style');style.textContent='#sound,.sound,#audioToggle{display:none!important}';document.head.append(style);
})();