// Load the canonical entry and all four embedded chapters; audit actual requests.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright-core');
const base = process.env.BASE || 'http://127.0.0.1:5183';

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    headless: true
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' });
    const errors = [], apiCalls = [], chapters = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('response', r => {
      if (r.status() >= 400) errors.push(`${r.status()} ${r.url()}`);
      if (new URL(r.url()).pathname.startsWith('/api/')) apiCalls.push(r.url());
    });
    await page.goto(base, { waitUntil: 'networkidle' });
    await page.waitForFunction(() => typeof window.visitContinuation === 'function');
    const entryTitle = await page.title();
    const routes = ['original/incense/index.html', 'original/chapters/wish/index.html',
                    'original/chapters/hall/index.html', 'original/chapters/bodhi/index.html'];
    for (let i = 0; i < routes.length; i++) {
      await page.evaluate(n => window.visitContinuation(n), i);
      await page.waitForFunction(route => document.querySelector('#continuation iframe')?.contentWindow.location.href.includes(route), routes[i]);
      const frame = page.frames().find(f => f.url().includes(routes[i]));
      // Video preloads can keep connections open; wait for the chapter UI instead.
      await frame.waitForLoadState('load');
      await frame.locator(['#root > *', '#approach', '#padHotspot', '#stage'][i]).first().waitFor();
      await page.waitForTimeout(600);
      chapters.push({ route: routes[i], title: await frame.title() });
      if (i === 1) {
        await frame.locator('#approach').click();
        await frame.locator('#coin').click();
        await frame.locator('#write').click();
        await frame.locator('#wish').fill('希望把项目的一小步做好');
        assert.equal(await frame.locator('#keep').isEnabled(), true);
        assert.equal(await frame.locator('#count').textContent(), '还可写 389 字');
      }
    }
    await page.evaluate(() => window.closeContinuation());
    await page.waitForTimeout(300);
    const report = { entryTitle, chapters, wishInput: 'passed; submission covered by direct.e2e.js', apiCalls, errors };
    fs.writeFileSync(path.join(__dirname, 'front-all.audit.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
    assert.deepEqual(errors, [], 'Canonical frontend has load/runtime errors');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
