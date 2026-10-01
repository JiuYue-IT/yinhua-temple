import { forwardRef } from 'react';
export default forwardRef(function Incense({burning, igniting, interactive, onPointerDown,onPointerMove,onPointerUp,onPointerCancel,onKeyDown},ref) {
 return <div ref={ref} className={`incense-bundle ${burning?'is-burning':''} ${igniting?'is-igniting':''}`} role="button" tabIndex={interactive?0:-1} aria-label="将三炷香放入香炉，按回车或拖动" aria-disabled={!interactive} onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={onPointerCancel} onKeyDown={onKeyDown}>
  {[0,1,2].map(i=><div className={`incense-stick stick-${i}`} key={i}>
   <svg className="stick-texture" viewBox="796 60 82 881" preserveAspectRatio="none" aria-hidden="true"><image href="./assets/incense.png" width="1672" height="941"/></svg>
   <i className="ember"/><svg className="flame" viewBox="0 0 24 60" aria-hidden="true"><path fill="#f39a45" d="M12 0C15 17 26 28 22 44C20 63 1 63 2 44C1 30 13 18 12 0Z"/><path fill="#ffe7a0" d="M12 24C13 35 20 41 17 51C14 62 5 55 7 47Z"/></svg>
   <svg className="smoke-line" viewBox="0 0 100 300" aria-hidden="true"><path d="M50 300 C20 260 80 235 48 200 S15 150 48 110 S80 55 45 0"/><path d="M50 300 C75 255 20 240 55 190 S82 135 48 90 S25 40 52 0"/></svg>
   {[0,1,2].map(j=><i key={j} className="spark" style={{'--sx':`${(j-1)*23}px`,'--sd':`${j*.09}s`}}/>)}
  </div>)}
 </div>;
});
