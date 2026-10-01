import {useEffect,useRef} from 'react';
import {createPortal} from 'react-dom';
import gsap from 'gsap';

// Portal escapes the 16:9 stage: the final veil covers the entire viewport.
export default function SmokeTransition({origin,onCovered,onReplay,completed}) {
 const root=useRef(),callback=useRef(onCovered);
 callback.current=onCovered;
 useEffect(()=>{
  const el=root.current;
  const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const clouds=el.querySelectorAll('.final-smoke-cloud');
  const width=window.innerWidth,height=window.innerHeight;
  const x=origin.x,y=origin.y;
  const tl=gsap.timeline();
  gsap.set(clouds,{left:x,top:y,xPercent:-50,yPercent:-50,scale:.12,opacity:0});
  if(!reduced){
   tl.to(el.querySelector('.final-smoke-threads'),{opacity:.7,duration:.8},0)
    .to(clouds,{opacity:.65,duration:1.6,stagger:.14,ease:'sine.out'},.3)
    .to(clouds,{x:i=>(i%3-1)*width*.4,y:i=>-height*(.12+Math.floor(i/3)*.2),scale:i=>1.4+(i%3)*.25,rotation:i=>(i%2?1:-1)*16,duration:4.2,stagger:.14,ease:'power1.inOut'},.3)
    .to(el.querySelector('.final-smoke-threads'),{opacity:0,duration:1.8},2.5);
  }
  tl.to(el.querySelector('.final-smoke-opaque'),{opacity:1,duration:reduced?1.2:2.3,ease:'sine.inOut'},reduced?0:2.8)
    .set(clouds,{visibility:'hidden'})
    .call(()=>callback.current());
  return()=>tl.kill();
 },[]);
 return createPortal(<div ref={root} className="final-smoke" data-covered={completed?'true':'false'} aria-label="香烟渐起，遮蔽寺庙">
  <svg className="final-smoke-threads" viewBox="0 0 600 600" style={{left:origin.x,top:origin.y}} aria-hidden="true"><defs><filter id="final-wisp"><feGaussianBlur stdDeviation="6"/></filter></defs><g filter="url(#final-wisp)" fill="none" stroke="#f1f0e8" strokeWidth="13">{[-1,0,1].map(i=><path key={i} d={`M${300+i*22} 590 C${200+i*40} 460 ${380+i*30} 360 300 290 S${190+i*60} 140 ${300+i*65} 10`}/>)}</g></svg>
  {Array.from({length:9},(_,i)=><div key={i} className={`final-smoke-cloud cloud-${i%3}`} aria-hidden="true"/>)}
  <div className="final-smoke-opaque" aria-hidden="true"><div className="final-smoke-drift"/></div>
  {completed&&<div className="final-smoke-rest" aria-live="polite"><p>一炷心香，万念归静。</p><button onClick={onReplay}>再上一炷香</button></div>}
 </div>,document.body);
}
