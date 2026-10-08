/* Dissolve the actual letter strokes into a quiet, rightward breeze. */
(()=>{
  const intro=document.querySelector('#prologue');
  if(!intro||matchMedia('(prefers-reduced-motion: reduce)').matches)return;
  const lines=[...intro.querySelectorAll('.prologue-copy p')];
  const canvas=document.createElement('canvas');canvas.className='prologue-particles';canvas.setAttribute('aria-hidden','true');intro.append(canvas);
  const ctx=canvas.getContext('2d');let samples=[],start=performance.now(),raf,lastSound=-1;
  function prepare(){
    canvas.width=innerWidth;canvas.height=innerHeight;
    samples=lines.map(el=>{
      const style=getComputedStyle(el),rect=el.getBoundingClientRect();
      const size=parseFloat(style.fontSize),height=parseFloat(style.lineHeight);
      const rows=el.innerHTML.split(/<br\s*\/?\s*>/i).map(s=>s.replace(/<[^>]*>/g,''));
      const buffer=document.createElement('canvas');buffer.width=canvas.width;buffer.height=canvas.height;
      const b=buffer.getContext('2d',{willReadFrequently:true});
      b.font=`${style.fontWeight} ${size}px ${style.fontFamily}`;b.textAlign='center';b.textBaseline='middle';b.fillStyle=style.color;
      const cy=rect.top+20+height*rows.length/2;
      rows.forEach((row,i)=>b.fillText(row,rect.left+rect.width/2,cy+(i-(rows.length-1)/2)*height));
      const pixels=b.getImageData(0,0,buffer.width,buffer.height).data,points=[];
      const widths=rows.map(row=>b.measureText(row).width),left=rect.left+rect.width/2-Math.max(...widths)/2,right=rect.left+rect.width/2+Math.max(...widths)/2;
      for(let y=Math.max(0,Math.floor(cy-height*rows.length/2));y<Math.min(buffer.height,cy+height*rows.length/2);y+=2){
        for(let x=Math.max(0,Math.floor(left));x<Math.min(buffer.width,right);x+=2){
          const a=pixels[(y*buffer.width+x)*4+3];if(a<35)continue;
          const seed=((x*73+y*137)%997)/997;
          points.push({x,y,a:a/255,seed,release:(right-x)/Math.max(1,right-left)*.52});
        }
      }
      return {points,left,right,color:style.color};
    });
  }
  function frame(now){
    if(intro.hidden||intro.classList.contains('leaving')){ctx.clearRect(0,0,canvas.width,canvas.height);return;}
    ctx.clearRect(0,0,canvas.width,canvas.height);
    const elapsed=(now-start)/1000,index=Math.floor(elapsed/4),time=elapsed%4;
    lines.forEach((el,i)=>{
      el.style.opacity=i===index?Math.min(1,time/.8):0;
      el.style.clipPath='none';
      el.style.color=i===2&&time>.7?'#f2c8d3':'#e5e6df';
    });
    const sample=samples[index];
    if(sample&&time>2.7&&lastSound!==index){lastSound=index;window.dispatchEvent(new Event('temple-prologue-dissolve'));}
    if(sample&&time>2.7){
      const progress=(time-2.7)/1.3,cut=sample.right-progress/.52*(sample.right-sample.left);
      const rect=lines[index].getBoundingClientRect();
      lines[index].style.clipPath=`inset(0 ${Math.max(0,rect.right-cut)}px 0 0)`;
      ctx.fillStyle=sample.color;
      for(const point of sample.points){
        const age=progress-point.release;if(age<0)continue;
        const fade=Math.max(0,1-age/.48);ctx.globalAlpha=point.a*fade*.85;
        const x=point.x+age*(100+point.seed*170),y=point.y-age*(12+point.seed*32)+Math.sin(age*7+point.seed*9)*age*15;
        ctx.fillRect(x,y,.7+point.seed,1);
      }
      ctx.globalAlpha=1;
    }
    // The third verse brings a few blossoms into the otherwise quiet darkness.
    if(elapsed>=8.5&&elapsed<16){
      const fade=Math.min(1,(elapsed-8.5)/1.2)*Math.min(1,(16-elapsed)/1.5);
      // The third verse gathers a pink petal-shaped blossom from the dissolving type.
      const bloom=Math.min(1,Math.max(0,(elapsed-9.4)/1.15));
      const bx=canvas.width/2+(elapsed-10.2)*18,by=canvas.height/2+18+(elapsed-10.2)*28;
      ctx.save();ctx.translate(bx,by);ctx.globalAlpha=fade*bloom*.88;
      ctx.shadowColor='#f4b6ca';ctx.shadowBlur=18;
      for(let i=0;i<5;i++){
        ctx.save();ctx.rotate(i*Math.PI*2/5);ctx.scale(.9,1.35);
        ctx.fillStyle=i%2?'#f3c4d1':'#e9aabd';
        ctx.beginPath();ctx.moveTo(0,-2);ctx.bezierCurveTo(5,-7,10,-1,0,4);ctx.bezierCurveTo(-10,-1,-5,-7,0,-2);ctx.fill();ctx.restore();
      }
      ctx.shadowBlur=5;ctx.fillStyle='#f9e3a6';ctx.beginPath();ctx.arc(0,0,2.2,0,Math.PI*2);ctx.fill();
      ctx.restore();
    }
    if(index<lines.length)raf=requestAnimationFrame(frame);
  }
  intro.classList.add('particle-intro');prepare();raf=requestAnimationFrame(frame);
  document.fonts.ready.then(()=>{if(!intro.hidden)prepare();});
  const resize=()=>prepare();window.addEventListener('resize',resize);
  const observer=new MutationObserver(()=>{if(intro.hidden){cancelAnimationFrame(raf);window.removeEventListener('resize',resize);canvas.remove();observer.disconnect();}});
  observer.observe(intro,{attributes:true,attributeFilter:['hidden']});
})();
