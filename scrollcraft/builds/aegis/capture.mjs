// Captures the real running Aegis platform. Every texture in the world comes from here:
// the page argues "this is real, not a toy", so a drawn approximation of the product would
// undercut the one thing it exists to prove.
//
// Usage:  node capture.mjs <access-token>
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const TOKEN = process.argv[2];
if (!TOKEN) { console.error('usage: node capture.mjs <access-token>'); process.exit(1); }

const OUT = 'raw';
mkdirSync(OUT, { recursive: true });

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const browser = await chromium.launch({ executablePath: CHROME });
const ctx = await browser.newContext({
  viewport: { width: 1500, height: 940 },
  deviceScaleFactor: 2,
  colorScheme: 'dark',
});
const page = await ctx.newPage();

async function shot(name, el) {
  const target = el ? await page.locator(el) : page;
  await target.screenshot({ path: `${OUT}/${name}.png` });
  console.log('  captured', name);
}

// ---- 1. the console, signed out ------------------------------------------
await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
await page.waitForTimeout(600);
await shot('console-signedout');

// ---- 2. the console, signed in, every scenario run -----------------------
// The token is minted through the API rather than by driving the login form.
await page.evaluate((t) => sessionStorage.setItem('aegis.token', t), TOKEN);
await page.reload({ waitUntil: 'networkidle' });
await page.waitForTimeout(500);

await shot('identity-panel', '#signin-panel');
await shot('token-json', '.panel:has(#token-json)');

// Run the scenarios so the result cards carry real responses.
await page.evaluate(() => {
  ['read', 'own', 'other', 'write', 'notoken', 'tampered']
    .forEach((id) => document.getElementById('run-' + id)?.click());
});
await page.waitForTimeout(2500);
await page.evaluate(() => document.getElementById('run-flood')?.click());
await page.waitForTimeout(4000);

await shot('console-live');

// Individual scenario cards: these are the hero textures of the deep legs.
const cards = await page.locator('.sc').all();
for (let i = 0; i < cards.length; i++) {
  await cards[i].screenshot({ path: `${OUT}/card-${i}.png` });
}
console.log('  captured', cards.length, 'scenario cards');

// The request log, which is the proof that all of it actually happened.
await page.evaluate(() => document.querySelector('.logwrap')?.scrollIntoView());
await page.waitForTimeout(400);
await shot('request-log', '.panel:has(#log)');

// ---- 3. the auth server's own surfaces -----------------------------------
await page.goto('http://localhost:9000/login', { waitUntil: 'networkidle' });
await page.waitForTimeout(600);
await shot('login');

await page.goto('http://localhost:9000/oauth2/jwks', { waitUntil: 'networkidle' });
await shot('jwks');

await browser.close();
console.log('done');
