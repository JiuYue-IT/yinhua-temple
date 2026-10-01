import {useEffect,useRef,useState} from 'react';
import gsap from 'gsap';

const paths={left:{name:'净心池',verb:'回观',description:'照见旧念，不必逐它。'},right:{name:'许愿池',verb:'前行',description:'未来未至，不必先忧。'}};
export default function IntentionChoice({onComplete}) {
 const root=useRef(),active=useRef(null),progress=useRef({left:0,right:0}),locked=useRef(false),exit=useRef();
 const [focus,setFocus]=useState(null),[chosen,setChosen]=useState(null);
 const select=side=>{if(locked.current)return;locked.current=true;setChosen(side);active.current=null;
  const reduce=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  exit.current=gsap.timeline().to(root.current.querySelector('.intention-wave'),{opacity:.7,scale:3,duration:reduce?.2:1.3,ease:'power2.out'}).to(root.current,{opacity:0,duration:reduce?.2:.8,delay:.4}).call(()=>onComplete({side,...paths[side]}));
 };
 const enter=side=>{if(locked.current)return;active.current=side;setFocus(side);};
 const leave=()=>{active.current=null;if(!locked.current)setFocus(null);};
 useEffect(()=>{
  const intro=gsap.fromTo(root.current,{opacity:0},{opacity:1,duration:1.2});
  const tick=(_t,delta)=>{if(locked.current)return;for(const side of ['left','right']){progress.current[side]=Math.max(0,Math.min(1,progress.current[side]+Math.min(delta,60)/1500*(active.current===side?1:-.65)));root.current.style.setProperty(`--${side}-hold`,progress.current[side]);if(progress.current[side]>=1){select(side);break;}}};
  const blur=()=>leave();gsap.ticker.add(tick);window.addEventListener('blur',blur);
  return()=>{intro.kill();exit.current?.kill();gsap.ticker.remove(tick);window.removeEventListener('blur',blur);};
 },[]);
 return <div ref={root} className="intention-choice" data-focus={focus||''} data-chosen={chosen||''} onPointerLeave={leave}>
  <header className="intention-heading"><span className="intention-seal">一念</span><h1>一念回观，一念前行。</h1><p aria-live="polite">{chosen?`${paths[chosen].verb}之念已定，借一炷香安放。`:'你今日所困，缘起何处？'}</p></header>
  <svg className="intention-mist" viewBox="0 0 1200 700" preserveAspectRatio="none" aria-hidden="true"><path d="M600 690C540 480 370 490 240 360S120 320 70 280"/><path d="M600 690C660 480 830 490 960 360S1080 320 1130 280"/></svg>
  {Object.entries(paths).map(([side,path])=><button key={side} disabled={!!chosen} className={`intention-pool intention-${side}`} onPointerEnter={e=>{if(e.pointerType!=='touch')enter(side);}} onPointerLeave={leave} onPointerDown={e=>{if(e.pointerType==='touch'){e.currentTarget.setPointerCapture(e.pointerId);enter(side);}}} onPointerUp={e=>{if(e.pointerType==='touch')leave();}} onPointerCancel={leave} onClick={()=>select(side)} onFocus={()=>setFocus(side)} onBlur={leave} aria-label={`选择${path.name}，${path.verb}`}>
   <svg className="intention-water" viewBox="0 0 400 260" aria-hidden="true">{[0,1,2,3].map(i=><ellipse key={i} cx="200" cy="160" rx={80+i*32} ry={16+i*13} style={{animationDelay:`${-i*1.2}s`}}/>)}</svg>
   <span className="intention-verb">{path.verb}</span><strong>{path.name}</strong><span className="intention-desc">{path.description}</span>
   <span className="intention-lantern" aria-hidden="true"/>
  </button>)}
  <p className="intention-instruction">让念头靠近它想去的地方<span>停留片刻，或轻触水面</span></p>
  <div className={`intention-wave ${chosen==='right'?'gold':''}`} aria-hidden="true"/>
 </div>;
}
