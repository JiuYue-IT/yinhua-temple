import {useState,useRef,useEffect,useCallback} from 'react';
import gsap from 'gsap';
import Atmosphere from './components/Atmosphere.jsx';
import Incense from './components/Incense.jsx';
import IntentionChoice from './components/IntentionChoice.jsx';
import SmokeTransition from './components/SmokeTransition.jsx';
import {transition,isSensing} from './stateMachine.js';
import {hardwareBridge,DEBUG_HARDWARE} from './services/hardwareBridge.js';
import {playSound} from './services/soundService.js';

export default function App({onSceneComplete}) {
 const [run,setRun]=useState(0);
 return <Ritual key={run} onReplay={()=>setRun(n=>n+1)} onSceneComplete={onSceneComplete}/>;
}
function Ritual({onReplay,onSceneComplete}) {
 const [state,setState]=useState('TempleOverview');
 const [ready,setReady]=useState(false),[failed,setFailed]=useState(false),[near,setNear]=useState(false);
 const [inserting,setInserting]=useState(false);
 const [intention,setIntention]=useState(null);
 const [smokeOrigin,setSmokeOrigin]=useState({x:0,y:0});
 const root=useRef(),camera=useRef(),incense=useRef(),hint=useRef(),veil=useRef(),flash=useRef();
 const current=useRef(state),heat=useRef({value:0}),active=useRef(false),drag=useRef(null),sent=useRef(false);
 current.current=state;
 const go=useCallback(next=>setState(prev=>transition(prev,next)),[]);
 const reduced=useRef(window.matchMedia('(prefers-reduced-motion: reduce)').matches);
 const duration = n => reduced.current?Math.min(n,.25):n;
 useEffect(()=>{let alive=true; Promise.all(['temple','burner','incense'].map(name=>new Promise((resolve,reject)=>{const img=new Image();img.onload=resolve;img.onerror=reject;img.src=`./assets/${name}.png`;}))).then(()=>alive&&setReady(true)).catch(()=>alive&&setFailed(true));return()=>{alive=false;};},[]);
 useEffect(()=>{
  const enter=()=>{active.current=true;if(isSensing(current.current))go('SensorApproaching');};
  const leave=()=>{active.current=false;};
  const ignite=()=>{if(isSensing(current.current))go('Igniting');};
  const off=[hardwareBridge.subscribe('enter',enter),hardwareBridge.subscribe('leave',leave),hardwareBridge.subscribe('ignite',ignite)];
  const key=e=>{if(e.code==='Space'&&DEBUG_HARDWARE&&isSensing(current.current)){e.preventDefault();hardwareBridge.triggerIgnite();}};
  const blur=()=>hardwareBridge.onSensorLeave();
  window.addEventListener('keydown',key);window.addEventListener('blur',blur);
  const tick=(_time,delta)=>{
   if(!isSensing(current.current))return;
   const dt=Math.min(delta,60)/1000;
   heat.current.value=Math.max(0,Math.min(1,heat.current.value+dt*(active.current?1:-.45)));
   root.current?.style.setProperty('--heat',heat.current.value);
   root.current?.style.setProperty('--ember-color',gsap.utils.interpolate('#67251e','#ffd886',heat.current.value));
   if(heat.current.value>=1)go('Igniting');
   else if(!active.current&&heat.current.value===0&&current.current==='SensorApproaching')go('WaitingForSensor');
  };
  gsap.ticker.add(tick);
  return()=>{off.forEach(fn=>fn());gsap.ticker.remove(tick);window.removeEventListener('keydown',key);window.removeEventListener('blur',blur);hardwareBridge.reset();};
 },[go]);
 useEffect(()=>{
  if(!ready)return;
  const tl=gsap.timeline();
  const text=(value)=>{tl.call(()=>{hint.current.textContent=value;gsap.set(hint.current,{opacity:0});}).to(hint.current,{opacity:1,duration:duration(.6)});};
  switch(state){
   case 'TempleOverview':gsap.set(incense.current,{yPercent:-100,y:80});tl.to({}, {duration:1.25}).call(()=>go('CameraMoving'));break;
   case 'CameraMoving':tl.to(camera.current,{scale:1.65,xPercent:0,yPercent:0,duration:duration(2.8),ease:'power2.inOut'}).to(veil.current,{opacity:.26,duration:duration(2.8)},0).call(()=>go('IncenseReady'));break;
   case 'IncenseReady':tl.to({}, {duration:.5}).to(incense.current,{opacity:1,y:0,duration:duration(.8),ease:'power2.out'});text('取三炷香');tl.to(hint.current,{opacity:0,delay:1.5,duration:.5}).call(()=>go('IntentionChoice'));break;
   case 'IntentionChoice':tl.to(veil.current,{opacity:.66,duration:1.2});break;
   case 'WaitingForSensor':tl.to(veil.current,{opacity:.26,duration:1});text('将香移近炉火');if(hardwareBridge.detected){active.current=true;tl.call(()=>go('SensorApproaching'));}break;
   case 'SensorApproaching':text('再近一些');break;
   case 'Igniting':playSound('ignite');hint.current.textContent='';tl.to(flash.current,{opacity:.22,duration:.18}).to(flash.current,{opacity:0,duration:.7}).call(()=>go('IncenseBurning'));break;
   case 'IncenseBurning':text('香已燃');tl.to(hint.current,{opacity:0,delay:1,duration:.5}).call(()=>go('PlacingIncense'));break;
   case 'PlacingIncense':
    if(!inserting){text('将心香，轻放入炉');break;}
    // First align above the opening, then lower the feet behind the front rim.
    gsap.killTweensOf(incense.current);
    tl.to(hint.current,{opacity:0,duration:duration(.25)})
      .to(incense.current,{left:'50%',top:'65%',x:0,y:0,scale:.5,duration:duration(.75),ease:'power2.inOut'},0)
      .to(incense.current.querySelectorAll('.incense-stick'),{rotation:0,height:'72%',duration:duration(.75),ease:'power2.inOut'},0)
      .set(incense.current,{zIndex:4})
      .to({}, {duration:duration(.18)})
      .to(incense.current,{top:'74%',duration:duration(1.15),ease:'power2.inOut'})
      .call(()=>go('IncensePlaced'));
    break;
   case 'IncensePlaced':playSound('settle');tl.to({}, {duration:1});text('香已入炉，心事可问。');tl.to(hint.current,{opacity:0,delay:1.5,duration:.6}).call(()=>go('SmokeTransition'));break;
   case 'SmokeTransition':{const rect=incense.current.querySelector('.stick-1 .ember').getBoundingClientRect();setSmokeOrigin({x:rect.left+rect.width/2,y:rect.top});break;}
   case 'Completed':if(!sent.current){sent.current=true;onSceneComplete?.({intention});window.dispatchEvent(new CustomEvent('temple:scene-complete',{detail:{intention}}));window.parent.postMessage({type:'incense-complete'},location.origin);}break;
  }
  return()=>tl.kill();
 },[state,ready,inserting,go,onSceneComplete]);
 const place=()=>{setNear(false);if(current.current==='PlacingIncense'&&!inserting)setInserting(true);};
 const restore=()=>{setNear(false);gsap.to(incense.current,{x:0,y:0,duration:.6,ease:'power2.out'});};
 const pointerDown=e=>{if(state!=='PlacingIncense'||inserting)return;e.preventDefault();e.currentTarget.setPointerCapture(e.pointerId);drag.current={x:e.clientX,y:e.clientY,moved:false};};
 const pointerMove=e=>{if(!drag.current)return;const rect=root.current.getBoundingClientRect();const x=e.clientX-drag.current.x,y=e.clientY-drag.current.y;drag.current.moved ||= Math.hypot(x,y)>8;const targetX=rect.left+rect.width*.5,targetY=rect.top+rect.height*.68;const bottomX=rect.left+rect.width*.5+x,bottomY=rect.top+rect.height*1.08+y;const close=Math.hypot((bottomX-targetX)/rect.width,(bottomY-targetY)/rect.height)<.17;drag.current.near=close;setNear(close);gsap.to(incense.current,{x:close?x*.7:x,y:close?y*.7+(targetY-(rect.top+rect.height*1.08))*.3:y,duration:.12,overwrite:true});};
 const pointerUp=()=>{if(!drag.current)return;const d=drag.current;drag.current=null;if(!d.moved||d.near)place();else restore();};
 const cancel=()=>{drag.current=null;restore();};
 const burning=['IncenseBurning','PlacingIncense','IncensePlaced','SmokeTransition','Completed'].includes(state);
 const sensing=isSensing(state);
 return <main className="min-h-dvh w-full overflow-hidden bg-[#203b3a] flex items-center justify-center">
  <section ref={root} className={`ritual relative isolate overflow-hidden ${near?'near-drop':''} ${state==='SensorApproaching'?'approaching':''} ${state==='IncensePlaced'||state==='SmokeTransition'?'placed':''}`} data-state={state} data-intention={intention?.side||''} aria-label="寺庙点香仪式">
   <div ref={camera} className="camera absolute inset-0 pointer-events-none">
    <img className="scene-image" src="./assets/temple.png" alt="樱花与水池环绕的东方寺庙"/>
    <svg className="courtyard-paving" viewBox="0 0 1672 941" preserveAspectRatio="none" aria-hidden="true"><defs><pattern id="paving-patch" width="160" height="30" patternUnits="userSpaceOnUse"><image href="./assets/temple.png" x="-752" y="-506" width="1672" height="941"/></pattern></defs><path d="M821 540H850L858 568L885 573L883 608L925 615L954 608L950 654L914 680L916 842L902 862H769L754 842L752 680L718 654L718 608L746 617L790 608L785 573L813 568Z" fill="url(#paving-patch)"/><image href="./assets/burner.png" x="712" y="538" width="248" height="326" preserveAspectRatio="none"/></svg>
    <Atmosphere/>
   </div>
   <div ref={veil} className="scene-veil absolute inset-0 pointer-events-none"/>
   <div className={`burner-layer ${['TempleOverview','CameraMoving'].includes(state)?'hidden-burner':''}`} aria-hidden="true"><img src="./assets/burner.png"/><svg className="bowl-light" viewBox="0 0 300 80"><ellipse cx="150" cy="40" rx="114" ry="23"/></svg></div>
   <Incense ref={incense} burning={burning} igniting={state==='Igniting'} interactive={state==='PlacingIncense'&&!inserting} onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={pointerUp} onPointerCancel={cancel} onKeyDown={e=>{if(e.key==='Enter'||e.code==='Space'){e.preventDefault();place();}}}/>
   <div className={`burner-front ${inserting?'visible-front':''}`} aria-hidden="true"><img src="./assets/burner.png"/></div>
   <div className={`sensor-aura ${sensing?'active':''}`} aria-hidden="true"><svg viewBox="0 0 400 160"><defs><radialGradient id="warm"><stop stopColor="#ffd8a0" stopOpacity=".7"/><stop offset="1" stopColor="#ffd8a0" stopOpacity="0"/></radialGradient></defs><ellipse cx="200" cy="95" rx="195" ry="60" fill="url(#warm)"/>{[0,1,2,3,4].map(i=><circle key={i} className="gold-mote" cx={160+i*20} cy="30" r="1.5" style={{animationDelay:`${i*.4}s`}}/>)}</svg></div>
   {DEBUG_HARDWARE&&sensing&&<div className="sensor-zone" aria-hidden="true" onPointerEnter={()=>hardwareBridge.onSensorEnter()} onPointerLeave={()=>hardwareBridge.onSensorLeave()} onPointerDown={e=>{e.currentTarget.setPointerCapture(e.pointerId);hardwareBridge.onSensorEnter();}} onPointerUp={()=>hardwareBridge.onSensorLeave()} onPointerCancel={()=>hardwareBridge.onSensorLeave()}/>}
   {state==='IntentionChoice'&&<IntentionChoice onComplete={choice=>{setIntention(choice);hardwareBridge.reset();active.current=false;heat.current.value=0;go('WaitingForSensor');}}/>}
   <p ref={hint} className="ritual-hint pointer-events-none" aria-live="polite"/>
   <div ref={flash} className="warm-flash pointer-events-none absolute inset-0"/>
   {(state==='SmokeTransition'||state==='Completed')&&smokeOrigin.x>0&&<SmokeTransition origin={smokeOrigin} onCovered={()=>go('Completed')} completed={state==='Completed'} onReplay={onReplay}/>}

   {DEBUG_HARDWARE&&sensing&&<button className="debug-button" onClick={()=>hardwareBridge.onSensorEnter()}>模拟感应</button>}
   {!ready&&<div className="loading absolute inset-0 flex items-center justify-center"><p>{failed?'素材加载失败，请刷新重试':'入寺，静心'}</p></div>}
  </section>
 </main>;
}
