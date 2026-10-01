// Real local Spring HTTP + deterministic local model stub; no real AI or hardware.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright-core');
const BASE = process.env.BASE || 'http://127.0.0.1:18080';
const reading = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../server/src/main/resources/ai/direct-preset.json')));
let delay = 300, malformed = false;
const modelCalls = [];
const model = http.createServer(async (req, res) => {
  let body = ''; for await (const chunk of req) body += chunk;
  const request = JSON.parse(body);
  modelCalls.push(request);
  const result = structuredClone(reading);
  result.summary.title = '把一小步做好';
  result.summary.message = '先明确一件今天能完成的小事，让行动给你一些事实。';
  result.detail.basis = '基于本次输入的心事；缺少具体背景，可能性需要在现实中验证。';
  await new Promise(r => setTimeout(r, delay));
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content: malformed ? '{}' : JSON.stringify(result) } }] }));
});
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  await new Promise(resolve => model.listen(18081, '127.0.0.1', resolve));
  const browser = await chromium.launch({ executablePath: process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const results = {}, errors = [], api = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' });
    page.on('pageerror', e => errors.push(e.message));
    page.on('response', r => { if (new URL(r.url()).pathname.startsWith('/api/')) api.push(`${r.request().method()} ${new URL(r.url()).pathname} ${r.status()}`); });
    await page.goto(BASE, { waitUntil: 'load' });
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
    assert.equal(modelCalls.length, 1, 'Model must already be called at the pool');
    const input = JSON.parse(modelCalls[0].messages[1].content.split('\n').slice(1).join('\n'));
    assert.equal(input.concern, concern);assert.equal(input.chosenPath, null);
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
    assert.equal(await hall.locator('#readingTitle').textContent(), '把一小步做好');
    await hall.locator('#readingNext').click();
    assert.equal(await hall.locator('#readingText').textContent(), '先明确一件今天能完成的小事，让行动给你一些事实。');
    await page.screenshot({ path: path.join(__dirname, 'direct-summary.png') });
    assert.equal(await hall.locator('#readingPanel').textContent().then(t => t.includes(reading.detail.understanding)), false);
    await hall.locator('#readingNext').click();
    console.log('hall summary read; moving to fruit');
    await page.waitForFunction(() => document.querySelector('#continuation iframe').contentWindow.location.href.includes('/bodhi/'));
    const bodhi = page.frames().find(f => f.url().includes('/bodhi/'));await bodhi.waitForLoadState('load');
    await bodhi.locator('#skip').click();await bodhi.locator('#signReveal.show').waitFor();
    await bodhi.waitForFunction(() => document.querySelectorAll('.detail-section').length === 5);
    console.log('fruit detail loaded');
    assert.ok((await bodhi.locator('#readingDetails').textContent()).includes(reading.detail.understanding));
    await page.screenshot({ path: path.join(__dirname, 'direct-detail.png') });
    await page.setViewportSize({ width: 390, height: 844 });await sleep(300);
    const card=await bodhi.locator('.sign-sheet').boundingBox();assert.ok(card.width<=390 && card.height<=844);
    assert.ok(await bodhi.locator('.sign-sheet').evaluate(el=>el.scrollHeight>el.clientHeight));
    await page.screenshot({ path: path.join(__dirname, 'direct-detail-mobile.png') });
    await bodhi.locator('#saveThought').click();
    await page.locator('#journey-end').waitFor();
    assert.equal(modelCalls.length, 1);assert.equal(api.some(line => line.includes('/choice')), false);
    await page.setViewportSize({ width: 1280, height: 800 });
    results.fullFlow = 'pool early generation → hall summary → fruit detail → saved';
    results.mobileDetail = 'scrollable card fits 390x844; save button reachable';

    // Late response: the hall uses a true wait and updates when the model returns.
    await page.evaluate(() => window.TempleFlow.reset());delay = 1800;
    await page.evaluate(() => window.TempleFlow.submitWish('迟到的回应也要正确等待'));
    const waiting = await visit(2, '/hall/');
    const delayed = page.evaluate(() => window.TempleFlow.drawSign());
    assert.equal(await page.evaluate(() => window.TempleFlow.snapshot.status), 'generating');
    const late = await delayed;assert.equal(late.status, 'sign_ready');
    results.delayedModel = 'waited for readiness before drawing';

    // Malformed result never becomes a fabricated sign; user can retry.
    await page.evaluate(() => window.TempleFlow.reset());malformed=true;delay=100;
    await page.evaluate(() => window.TempleFlow.submitWish('失败后需要重试'));
    await page.waitForFunction(() => window.TempleFlow.snapshot.status === 'error');
    const failed = await page.evaluate(async () => { try { await TempleFlow.drawSign();return null; } catch(e){return e.code;} });
    assert.equal(failed, 'AI_FORMAT_ERROR');
    malformed=false;await page.evaluate(() => window.TempleFlow.retryGeneration());
    const recovered = await page.evaluate(() => window.TempleFlow.drawSign());assert.equal(recovered.status, 'sign_ready');
    results.errorRetry = 'format error → retry → sign_ready';
    await page.evaluate(() => window.TempleFlow.reset());
    assert.deepEqual(errors, []);
    const report={results,modelCalls:modelCalls.length,api,errors};
    fs.writeFileSync(path.join(__dirname,'direct.e2e.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify(report,null,2));
  } finally { await browser.close();await new Promise(r => model.close(r)); }
})().catch(e=>{console.error(e);model.close();process.exitCode=1;});
