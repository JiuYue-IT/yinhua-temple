(() => {
 const stage=document.querySelector('#stage'),scene=document.querySelector('#scene');
 const canvas=document.createElement('canvas');canvas.id='blossomDust';stage.append(canvas);const ctx=canvas.getContext('2d');
 const source=new Image();source.src=scene.src;let dots=[],amount=0,target=0,start=0,raf;
 source.onload=()=>{const c=document.createElement('canvas');c.width=832;c.height=470;const g=c.getContext('2d',{willReadFrequently:true});g.drawImage(source,0,0,832,470);const data=g.getImageData(0,0,832,470).data;for(let y=0;y<470;y+=3)for(let x=0;x<832;x+=3){const i=(y*832+x)*4,r=data[i],green=data[i+1],b=data[i+2];if(r>170&&r>green*1.08&&b>green*.95){const seed=((x*79+y*131)%997)/997;dots.push({x:x/832,y:y/470,seed,size:.45+seed*.9,color:`rgb(${r},${green},${b})`});}}};
 function draw(){const w=stage.clientWidth,h=stage.clientHeight;if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}ctx.clearRect(0,0,w,h);if(amount<=0||amount>=1)return;const scale=Math.max(w/832,h/470)*1.055,ow=832*scale,oh=470*scale;for(const d of dots){const t=Math.max(0,Math.min(1,(amount-d.seed*.15)/.85));ctx.globalAlpha=Math.sin(t*Math.PI)*(1-t)*.95;ctx.fillStyle=d.color;ctx.beginPath();ctx.arc(d.x*ow+(w-ow)/2+Math.sin(d.seed*15+t*3)*t*65,d.y*oh+(h-oh)/2-t*(60+d.seed*150),d.size*(1-t*.6),0,Math.PI*2);ctx.fill();}ctx.globalAlpha=1;}
 function animate(time){if(!start)start=time;const t=Math.min(1,(time-start)/1600);amount=amount+(target-amount)*Math.min(1,.06+t*.06);if(Math.abs(target-amount)<.002)amount=target;stage.style.setProperty('--blossom-clear',amount);draw();if(amount!==target)raf=requestAnimationFrame(animate);}
 function update(){const index=Number(stage.dataset.progress||0);target=index===0?0:index===1?.22:index===2?.7:1;cancelAnimationFrame(raf);start=0;raf=requestAnimationFrame(animate);}
 new MutationObserver(update).observe(stage,{attributes:true,attributeFilter:['data-progress']});update();window.addEventListener('resize',draw);
})();
