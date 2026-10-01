/* Local assets and vendor libraries: this experience needs no network connection. */
gsap.registerPlugin(ScrollTrigger);
const $ = (s) => document.querySelector(s);
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const hero = $('#hero'), opening = $('#opening');
let state = 'arrival', chosen = null, soundOn = false, audio, master, wind, water, bellTimer, fireTimer, musicTimer;
let approachTween;
history.scrollRestoration = 'manual';
scrollTo(0, 0);
document.body.style.overflow='hidden';
let prologueTimer;
function endPrologue(){
  clearTimeout(prologueTimer);
  const intro=$('#prologue');if(intro.hidden)return;
  intro.classList.add('leaving');document.body.style.overflow='';scrollTo(0,0);
  setTimeout(()=>{intro.hidden=true;intro.style.display='none';ScrollTrigger.refresh();},1500);
}
function skipPrologueNow(){
  clearTimeout(prologueTimer);const intro=$('#prologue');intro.hidden=true;intro.style.display='none';document.body.style.overflow='';scrollTo(0,0);ScrollTrigger.refresh();$('#approach').focus({preventScroll:true});
}
$('#skipPrologue').addEventListener('click',skipPrologueNow);
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('#prologue').hidden){e.preventDefault();skipPrologueNow();}});
prologueTimer=setTimeout(endPrologue,reduced?9000:16500);
if (reduced) hero.removeAttribute('autoplay');
hero.addEventListener('error', () => { hero.style.display = 'none'; });
hero.pause();
if (reduced) hero.pause();

for (let i = 0; i < 13; i++) {
  const p = document.createElement('i'); p.className = 'petal';
  p.style.cssText = `--x:${(i * 37) % 100}%;--duration:${13 + i % 6}s;--delay:${-i * 1.7}s`;
  $('#petals').append(p);
}
const scrollTimeline = gsap.timeline({paused:true,defaults:{ease:'none'}})
  .to('#arrival',{scale:1.19,duration:1},0)
  .to('#arrival',{autoAlpha:0,duration:.3},.55)
  .to('#heroTitleArt',{opacity:0,duration:reduced?.2:.16},.02)
  .fromTo(['#treeLeft','#treeRight'],{autoAlpha:0},{autoAlpha:1,duration:.35},.25)
  .fromTo('#question',{autoAlpha:0},{autoAlpha:1,duration:.3},.65)
  .to('#temple',{scale:1.12,duration:1},0)
  .to('#treeLeft',{xPercent:reduced ? 0 : -61,scale:1.13,duration:1},0)
  .to('#treeRight',{xPercent:reduced ? 0 : 61,scale:1.13,duration:1},0)
  .to('#question',{y:reduced ? 0 : -13,duration:1},0);
const trigger = ScrollTrigger.create({trigger:'#journey',start:'top top',end:'bottom bottom',animation:scrollTimeline,scrub:reduced ? true : .75,onUpdate(self){
  $('#progressFill').style.transform=`scaleX(${self.progress})`;
  requestAnimationFrame(()=>renderTitleParticles(self.progress));
  $('#cueText').textContent=self.progress > .85 ? '带着此刻的心，叩门' : '向前，慢慢走';
}});

// Sample original ink strokes so scattered fragments follow the supplied lettering.
const particleCanvas=$('#titleParticles'),particleContext=particleCanvas.getContext('2d');
const inkParticles=[];let particleProgress=0;
const titleSource=new Image();titleSource.src='assets/hero-yinhua.png';
titleSource.onload=()=>{
  const sample=document.createElement('canvas');sample.width=1024;sample.height=576;
  const ctx=sample.getContext('2d',{willReadFrequently:true});ctx.drawImage(titleSource,0,0,1024,576);
  const pixels=ctx.getImageData(0,0,1024,576).data;
  for(let y=100;y<467;y+=2)for(let x=437;x<601;x+=2){
    const i=(y*1024+x)*4;if(pixels[i]>95||pixels[i+1]>95||pixels[i+2]>95)continue;
    const seed=((x*73+y*137)%997)/997,angle=seed*Math.PI*2;
    const grain=((x*193+y*47)%991)/991;
    inkParticles.push({x,y,dx:Math.cos(angle)*(100+grain*260),dy:Math.sin(angle)*(85+grain*180)-grain*35,delay:((y-100)/367)*.08+grain*.08,color:seed>.7?'#e6a9b4':'#243c35',size:.38+grain*.52,seed});
  }
  renderTitleParticles(particleProgress);
};
function renderTitleParticles(progress){
  particleProgress=progress;if(!particleCanvas||!inkParticles.length)return;
  const w=particleCanvas.clientWidth,h=particleCanvas.clientHeight;
  if(particleCanvas.width!==Math.round(w)||particleCanvas.height!==Math.round(h)){particleCanvas.width=Math.round(w);particleCanvas.height=Math.round(h);}
  particleContext.clearRect(0,0,w,h);if(reduced||progress<.015||progress>.85)return;
  const p=Math.min(1,Math.max(0,(progress-.02)/.58));
  for(const dot of inkParticles){
    const t=Math.max(0,(p-dot.delay)/(1-dot.delay));
    particleContext.globalAlpha=Math.min(1,progress/.10)*Math.pow(1-t,1.35)*(.45+dot.seed*.35);
    particleContext.fillStyle=dot.color;
    const drift=t*t*(3-2*t);
    particleContext.beginPath();particleContext.ellipse((dot.x+dot.dx*drift)*w/1024,(dot.y+dot.dy*drift+Math.sin(t*5+dot.seed*6)*t*9)*h/576,dot.size*w/1024*(1-t*.25),dot.size*h/576*(.7+dot.seed*.3),t*2+dot.seed*6,0,Math.PI*2);particleContext.fill();
  }
  particleContext.globalAlpha=1;
}

function approach(callback) {
  approachTween?.kill();
  const pos={y:window.scrollY};
  approachTween=gsap.to(pos,{y:trigger.end,duration:reduced ? 0 : 1.7,ease:'power2.inOut',onUpdate:()=>scrollTo(0,pos.y),onComplete:callback});
}
$('#approach').addEventListener('click',()=>{
  if(state !== 'arrival') return;
  if(trigger.progress > .95) $('[data-choice]').focus(); else approach();
});

document.querySelectorAll('[data-choice]').forEach(button=>{
  button.addEventListener('click',()=>choose(button.dataset.choice));
});

// Both targets are real keyboard/touch buttons, aligned with the video first frame.
const knockButtons = [...document.querySelectorAll('.knock-target')];
knockButtons.forEach(button=>{
  const respond=()=>{
    if(state!=='knock-ready')return;
    document.body.classList.add('near-ring');
    if(!reduced) gsap.fromTo(button.querySelector('.knock-ring'),{rotation:-9,yPercent:0},{rotation:0,yPercent:0,duration:.9,ease:'elastic.out(1,.25)',overwrite:true});
  };
  button.addEventListener('pointerenter',respond);button.addEventListener('focus',respond);
  button.addEventListener('pointerleave',()=>document.body.classList.remove('near-ring'));
  button.addEventListener('blur',()=>document.body.classList.remove('near-ring'));
  button.addEventListener('click',knock);
});
const hand = $('#handCursor');
const finePointer=matchMedia('(hover: hover) and (pointer: fine)');
document.addEventListener('pointermove',event=>{
  if(!finePointer.matches || event.pointerType==='touch')return;
  document.body.classList.add('hand-cursor');
  hand.style.left=`${event.clientX}px`;hand.style.top=`${event.clientY}px`;
});
document.documentElement.addEventListener('pointerleave',()=>document.body.classList.remove('hand-cursor'));
document.addEventListener('keydown',event=>{if(event.key==='Tab')document.body.classList.remove('hand-cursor');});
finePointer.addEventListener('change',()=>{if(!finePointer.matches)document.body.classList.remove('hand-cursor');});

function knockSound(){
  if(!audio || !soundOn)return;
  [0,.22].forEach(delay=>{
    const t=audio.currentTime+delay,o=audio.createOscillator(),g=audio.createGain();
    o.type='triangle';o.frequency.setValueAtTime(185,t);o.frequency.exponentialRampToValueAtTime(65,t+.14);
    g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.35,t+.007);g.gain.exponentialRampToValueAtTime(.0001,t+.18);
    o.connect(g).connect(master);o.start(t);o.stop(t+.2);
  });
}
function waitForKnock(){
  window.dispatchEvent(new CustomEvent('temple-stage',{detail:1}));state='knock-ready';document.body.classList.add('knock-ready');
  $('#knockTargets').hidden=false;$('#knockInstruction').hidden=false;
  knockButtons.forEach(b=>b.disabled=false);
  if(reduced) gsap.set('#temple',{scale:1.35});
  else gsap.to('#temple',{scale:1.35,duration:1.4,ease:'power2.inOut'});
  // Announce the new action without forcing a persistent mouse hover state.
  $('#notice').textContent='';$('#knockInstruction').setAttribute('role','status');
}
function knock(){
  if(state!=='knock-ready')return;
  state='knocking';knockButtons.forEach(b=>b.disabled=true);knockSound();
  $('#knockInstruction').hidden=true;document.body.classList.remove('near-ring');

    const tl=gsap.timeline({onComplete:()=>{
    $('#knockTargets').hidden=true;document.body.classList.remove('knock-ready');startOpening();
  }});
  if(reduced)tl.to({}, {duration:.05});
  else tl.to('#handCursor',{x:-6,y:-9,rotation:-5,duration:.12,yoyo:true,repeat:3})
    .to('.knock-ring',{rotation:0,yPercent:0,duration:.1},0).to({}, {duration:.25});
}

function bell(volume=.22){
  if(!audio || !soundOn) return;
  const t=audio.currentTime;
  [174.6,349.7,477.1,704.5,946.3].forEach((f,i)=>{
    const o=audio.createOscillator(), g=audio.createGain(); o.frequency.value=f;
    g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(volume/(i+1),t+.012);
    g.gain.exponentialRampToValueAtTime(.0001,t+7-i*.7);
    o.connect(g).connect(master);o.start(t);o.stop(t+8);
  });
}
function createNoiseBuffer(seconds=4){
  const buffer=audio.createBuffer(1,audio.sampleRate*seconds,audio.sampleRate),data=buffer.getChannelData(0);let previous=0;
  for(let i=0;i<data.length;i++){previous=(previous+Math.random()*.04-.02)/1.025;data[i]=previous;}
  return buffer;
}
function waterSound(){
  if(!audio || !soundOn)return;
  const t=audio.currentTime;
  const source=audio.createBufferSource(),filter=audio.createBiquadFilter(),gain=audio.createGain();
  source.buffer=createNoiseBuffer(2.5);source.loop=false;filter.type='bandpass';filter.frequency.value=900;filter.Q.value=.45;
  gain.gain.setValueAtTime(.0001,t);gain.gain.exponentialRampToValueAtTime(.12,t+.2);gain.gain.exponentialRampToValueAtTime(.0001,t+2.3);
  source.connect(filter).connect(gain).connect(master);source.start(t);source.stop(t+2.4);
}
function fireSound(){
  if(!audio || !soundOn)return;
  const t=audio.currentTime,o=audio.createOscillator(),g=audio.createGain();o.type='triangle';o.frequency.setValueAtTime(90+Math.random()*35,t);o.frequency.exponentialRampToValueAtTime(42,t+.16);
  g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.07,t+.015);g.gain.exponentialRampToValueAtTime(.0001,t+.23);o.connect(g).connect(master);o.start(t);o.stop(t+.25);
}
function musicTone(){
  if(!audio || !soundOn)return;
  const notes=[261.63,293.66,392,440,523.25,440,392,293.66],index=Math.floor(Date.now()/1800)%notes.length;
  const t=audio.currentTime,o=audio.createOscillator(),g=audio.createGain();o.type='sine';o.frequency.value=notes[index];
  g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.045,t+.14);g.gain.exponentialRampToValueAtTime(.0001,t+1.65);
  o.connect(g).connect(master);o.start(t);o.stop(t+1.7);
}
async function toggleSound(keepMusic=false){
  return; // Audio disabled at its source.

  if(!audio){
    const Context=window.AudioContext || window.webkitAudioContext;
    if(!Context){$('#notice').textContent='当前浏览器暂不支持环境音。';return;}
    audio=new Context();master=audio.createGain();master.gain.value=0;master.connect(audio.destination);
    const buffer=createNoiseBuffer(4);
    wind=audio.createBufferSource();wind.buffer=buffer;wind.loop=true;
    const filter=audio.createBiquadFilter();filter.type='lowpass';filter.frequency.value=700;
    const windGain=audio.createGain();windGain.gain.value=.045;wind.connect(filter).connect(windGain).connect(master);/* Continuous wind disabled. */
    water=audio.createBufferSource();water.buffer=createNoiseBuffer(3);water.loop=true;
    const waterFilter=audio.createBiquadFilter();waterFilter.type='lowpass';waterFilter.frequency.value=420;waterFilter.Q.value=.35;
    const waterGain=audio.createGain();waterGain.gain.value=.025;water.connect(waterFilter).connect(waterGain).connect(master);/* Continuous water noise disabled. */
  }
  await audio.resume();soundOn=!soundOn;master.gain.setTargetAtTime(soundOn?.48:0,audio.currentTime,.6);
  $('#sound').setAttribute('aria-pressed',String(soundOn));$('#sound').setAttribute('aria-label',soundOn?'关闭风声与钟声':'开启风声与钟声');
  $('#soundLabel').textContent=soundOn?'声起':'听风';
  if(!keepMusic)window.setTempleMusic?.(soundOn);
  clearInterval(bellTimer);clearInterval(fireTimer);clearInterval(musicTimer);/* Only direct interaction sounds remain. */
}
$('#sound').addEventListener('click',()=>toggleSound().catch(()=>{$('#notice').textContent='请再次点击听风，开启环境音。';}));
document.addEventListener('visibilitychange',()=>{if(audio){if(document.hidden)audio.suspend();else if(soundOn)audio.resume();}});

function choose(choice){
  if(state!=='arrival')return;
  if(!audio) toggleSound().catch(()=>{});
  state='approaching';chosen=choice;
  document.querySelectorAll('[data-choice]').forEach(b=>{b.disabled=true;b.classList.toggle('selected',b.dataset.choice===choice);b.classList.toggle('unselected',b.dataset.choice!==choice);});
  gsap.to(['#question','#approach'],{autoAlpha:0,delay:reduced?0:.45,duration:reduced?0:.6});
  approach(()=>{
    scrollTimeline.progress(1); trigger.disable(false);document.body.style.overflow='hidden';
    waitForKnock();
  });
}
function startOpening(){
  bell(.28);finish();
}
function showFallback(){
  $('#notice').textContent='开门影像暂不可播放，已为你打开山门。';finish();
}
opening.addEventListener('ended',finish);
opening.addEventListener('error',()=>{if(state==='opening')showFallback();});
function finish(){
  if(state==='hall' || state==='finished')return;
  window.dispatchEvent(new CustomEvent('temple-stage',{detail:2}));state='hall';opening.pause();document.body.classList.add('finished');
    gsap.set('#opened',{opacity:1});gsap.set([opening,'#closed','#videoGate'],{opacity:0});
  document.body.classList.remove('opening');
  startHallExperience({reduced, chime:()=>bell(.12)});
}
$('#replay').addEventListener('click',()=>{
  resetHallExperience();
  approachTween?.kill();opening.pause();opening.currentTime=0;
  gsap.killTweensOf(['#completion','#question','#approach',opening,'#closed']);
    gsap.killTweensOf('#videoGate');
    gsap.set([opening,'#opened','#videoGate'],{opacity:0});gsap.set('#closed',{opacity:1});
  $('#completion').hidden=true;$('#notice').textContent='';document.body.classList.remove('opening','finished','knock-ready','near-ring');
  $('#knockTargets').hidden=true;$('#knockInstruction').hidden=true;knockButtons.forEach(b=>b.disabled=true);
  gsap.killTweensOf('#temple');gsap.set('#handCursor',{x:0,y:0,rotation:0});
  document.body.style.overflow='';state='arrival';chosen=null;
  document.querySelectorAll('[data-choice]').forEach(b=>{b.disabled=false;b.classList.remove('selected','unselected');});
  scrollTo(0,0);trigger.enable();ScrollTrigger.refresh();scrollTimeline.progress(0);
  gsap.set(['#question','#approach'],{autoAlpha:1});
  $('#cueText').textContent='向前，慢慢走';$('#progressFill').style.transform='scaleX(0)';
  $('#approach').focus({preventScroll:true});
});
window.addEventListener('load',()=>{scrollTo(0,0);ScrollTrigger.refresh();scrollTimeline.progress(0);});

// Shared audio bus: hall instruments obey the existing sound toggle.
window.templeAudio=()=>({context:audio,master,enabled:soundOn});

window.templeNavigate=place=>{
 window.closeContinuation?.();skipPrologueNow();$('#replay').click();
 if(place==='home'){window.dispatchEvent(new CustomEvent('temple-stage',{detail:0}));return;}
 scrollTo(0,trigger.end);trigger.update();scrollTimeline.progress(1);trigger.getTween()?.progress(1);renderTitleParticles(1);
 if(place==='gate'){gsap.set('#question',{autoAlpha:1});window.dispatchEvent(new CustomEvent('temple-stage',{detail:1}));return;}
 trigger.disable(false);document.body.style.overflow='hidden';finish();
};

let mapGateAnnounced=false;window.addEventListener('scroll',()=>{if(state==='arrival'&&trigger.progress>.65&&!mapGateAnnounced){mapGateAnnounced=true;window.dispatchEvent(new CustomEvent('temple-stage',{detail:1}));}},{passive:true});

window.toggleTempleAmbience=()=>toggleSound(true);
