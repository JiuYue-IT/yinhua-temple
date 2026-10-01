const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright-core');
const BASE='http://127.0.0.1:8080';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 const results={},errors=[],api=[];
 try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' });
    page.on('pageerror', e => errors.push(e.message));
    page.on('response', r => { if (new URL(r.url()).pathname.startsWith('/api/')) api.push(`${r.request().method()} ${new URL(r.url()).pathname} ${r.status()}`); });
    await page.goto(BASE, { waitUntil: 'load' });
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => typeof window.visitContinuation === 'function');
    async function visit(index, route) {
      await page.evaluate(n => window.visitContinuation(n), index);
      await page.waitForFunction(p => document.querySelector('#continuation iframe')?.contentWindow.location.href.includes(p), route);
      const f = page.frames().find(f => f.url().includes(route));
      await f.waitForLoadState('load'); return f;
    }
    const pool = await visit(1, '/wish/');
    await pool.locator('#approach').click();await pool.locator('#coin').click();await pool.locator('#write').click();
    const concern = '我希望把项目做好，也担心时间不够，想找到一件今天能完成的小事。'.repeat(2);
    await pool.locator('#wish').fill(concern);await pool.locator('#keep').click();
    await pool.locator('#target').waitFor({ state: 'visible' });
    console.log('pool accepted; model started');
    const started=Date.now();
    await page.waitForFunction(()=>TempleFlow.snapshot && TempleFlow.snapshot.status!=='generating',null,{timeout:75000});
    const generated=await page.evaluate(()=>({status:TempleFlow.snapshot.status,error:TempleFlow.snapshot.error}));
    assert.equal(generated.status,'ready',JSON.stringify(generated));
    results.generationSeconds=Number(((Date.now()-started)/1000).toFixed(1));
    console.log('real generation ready in '+results.generationSeconds+' seconds');
    await pool.locator('#target').focus();await pool.locator('#target').press('Enter');
    await pool.locator('#ending').waitFor({ state: 'visible' });
    await pool.locator('#continue').click();
    await page.waitForFunction(() => document.querySelector('#continuation iframe').contentWindow.location.href.includes('/hall/'), { timeout: 15000 });
    const hall = page.frames().find(f => f.url().includes('/hall/'));await hall.waitForLoadState('load');
    await hall.locator('#padHotspot').click();await sleep(1800);await hall.locator('body').press(' ');await sleep(1000);
    for (let i=0;i<10;i++){await hall.locator('body').press('ArrowDown');await sleep(30);}
    for (let i=0;i<11;i++){await hall.locator('body').press('ArrowUp');await sleep(30);}
    await hall.locator('#videoBridge.show').waitFor({ timeout: 10000 });
    for (let i=0;i<25;i++){await hall.locator('body').press('ArrowDown');await sleep(30);}
    await hall.locator('#lotLayer.active').waitFor({ timeout: 8000 });
    await hall.locator('body').press(' ');await hall.locator('#lotTube.drop-ready').waitFor({ timeout: 6000 });
    await hall.locator('body').press(' ');await hall.locator('#lotSign.visible').waitFor({ timeout: 10000 });
    console.log('hall sign dropped');
    await hall.locator('body').press('Enter');await hall.locator('#readingPanel.show').waitFor();
    const liveReading=await page.evaluate(()=>TempleFlow.snapshot.reading); assert.ok(liveReading.summary.title); assert.ok(liveReading.detail.nextStep); results.reading=liveReading;
    await hall.locator('#readingNext').click();
    assert.equal(await hall.locator('#readingText').textContent(),liveReading.summary.message);
    await page.screenshot({ path: path.join(__dirname, 'live-summary.png') });
    assert.equal(await hall.locator('#readingPanel').textContent().then(t => t.includes(liveReading.detail.understanding)), false);
    await hall.locator('#readingNext').click();
    console.log('hall summary read; moving to fruit');
    await page.waitForFunction(() => document.querySelector('#continuation iframe').contentWindow.location.href.includes('/bodhi/'));
    const bodhi = page.frames().find(f => f.url().includes('/bodhi/'));await bodhi.waitForLoadState('load');
    await bodhi.locator('#skip').click();await bodhi.locator('#signReveal.show').waitFor();
    await bodhi.waitForFunction(() => document.querySelectorAll('.detail-section').length === 5);
    console.log('fruit detail loaded');
    assert.ok((await bodhi.locator('#readingDetails').textContent()).includes(liveReading.detail.understanding));
    await page.screenshot({ path: path.join(__dirname, 'live-detail.png') });
    await page.setViewportSize({ width: 390, height: 844 });await sleep(300);
    const card=await bodhi.locator('.sign-sheet').boundingBox();assert.ok(card.width<=390 && card.height<=844);
    assert.ok(await bodhi.locator('.sign-sheet').evaluate(el=>el.scrollHeight>el.clientHeight));
    await page.screenshot({ path: path.join(__dirname, 'live-detail-mobile.png') });
    await bodhi.locator('#saveThought').click();
    await page.locator('#journey-end').waitFor();
assert.equal(api.some(line => line.includes('/choice')), false);
    await page.setViewportSize({ width: 1280, height: 800 });
    results.fullFlow = 'pool early generation → hall summary → fruit detail → saved';
    results.mobileDetail = 'scrollable card fits 390x844; save button reachable';


    results.completeStatus=await page.evaluate(()=>TempleFlow.snapshot.status);
    assert.equal(results.completeStatus,'complete');
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(__dirname,'live-ai.audit.json'),JSON.stringify({results,errors,api},null,2));
    console.log(JSON.stringify({seconds:results.generationSeconds,status:results.completeStatus,title:results.reading.summary.title,errors},null,2));
 } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1});
