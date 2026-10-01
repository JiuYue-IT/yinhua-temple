export default function Atmosphere() {
  return <div className="atmosphere pointer-events-none absolute inset-0" aria-hidden="true">
    <svg className="tree-sway" viewBox="0 0 1672 941" preserveAspectRatio="none"><defs><clipPath id="tree-crowns"><path d="M0 0 H150 Q260 50 230 160 Q265 215 218 275 Q140 300 0 375Z M1672 55 Q1530 80 1580 170 Q1430 190 1460 270 Q1390 325 1490 390 L1672 453Z"/></clipPath></defs><image href="./assets/temple.png" width="1672" height="941" clipPath="url(#tree-crowns)"/></svg>
    {Array.from({length:22},(_,i)=><i key={i} className="petal" style={{left:`${(i*47)%100}%`, '--delay':`${-i*1.7}s`,'--duration':`${15+i%7}s`,'--drift':`${50+(i%5)*35}px`}}/>)}
    {[0,1,2,3,4,5].map(i=><i key={`r${i}`} className="ripple" style={{left:`${i<3?10+i*7:72+(i-3)*6}%`,top:`${59+i%3*8}%`,animationDelay:`${-i*1.3}s`}}/>)}
  </div>;
}
