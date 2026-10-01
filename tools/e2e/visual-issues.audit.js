const {chromium}=require('playwright-core');
const fs=require('node:fs');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1280,height:800},reducedMotion:'reduce'});
  await page.goto('http://127.0.0.1:8080');
  await page.keyboard.press('Escape');
  await page.evaluate(()=>visitContinuation(0));
  await page.waitForFunction(()=>document.querySelector('#continuation iframe')?.contentWindow.location.href.includes('/incense/'));
  const frame=page.frames().find(f=>f.url().includes('/incense/'));
  await frame.waitForLoadState('load');
  await frame.waitForFunction(()=>document.querySelector('.ritual')?.dataset.state==='IntentionChoice');
  const report={};
  const hands=async()=>Promise.all(page.frames().map(f=>f.evaluate(()=>({url:location.pathname,state:document.querySelector('.ritual')?.dataset.state,hands:[...document.querySelectorAll('.unified-hand,#handCursor,#hand')].map(el=>({tag:el.tagName,id:el.id,class:el.className,display:getComputedStyle(el).display,rect:el.getBoundingClientRect().toJSON()}))}))));
  await page.mouse.move(100,100);
  // The map belongs to the parent document; returning to the iframe must hide its cursor.
  await page.locator('#mapToggle').click();
  await page.locator('#mapClose').click();
  report.before=await hands();
  await frame.locator('.intention-right').click();
  await page.mouse.move(1050,200);
  await page.waitForTimeout(600);
  report.after=await hands();
  await page.screenshot({path:path.join(__dirname,'incense-first-click.png')});
  report.images=await frame.evaluate(()=>[...document.querySelectorAll('img,image')].map(el=>({tag:el.tagName,src:el.getAttribute('src')||el.getAttribute('href'),width:el.naturalWidth,height:el.naturalHeight})));
  report.frameImage=await page.evaluate(async()=>{const im=new Image();im.src='assets/courtyard-refined-frames/frame_000001.webp';await im.decode();return {width:im.naturalWidth,height:im.naturalHeight,decodedMiB:im.naturalWidth*im.naturalHeight*4*122/1024/1024};});
  fs.writeFileSync(path.join(__dirname,'visual-issues.audit.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report,null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
