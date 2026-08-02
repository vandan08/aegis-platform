// The signature move cannot be checked by the scroll harness: the harness never clicks a
// button, so the page has to release itself under WebDriver or the whole flight freezes.
// That release is exactly the behaviour a real reader never gets, which means the real
// behaviour needs its own test. This forces the non-automated path and drives it.
import { chromium } from 'playwright-core';

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const URL = 'http://localhost:4510/';
let failures = 0;
const ok = (name, pass, detail = '') => {
  if (!pass) { failures++; }
  console.log(`  ${pass ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ' + detail : ''}`);
};

const browser = await chromium.launch({ executablePath: CHROME });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
// Make the page believe it is being read by a person, not driven by a harness.
await ctx.addInitScript(() => {
  Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true });
});
const page = await ctx.newPage();
await page.goto(URL, { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);

const track = await page.evaluate(() => document.documentElement.scrollHeight);

// --- it does not fire early -------------------------------------------------
await page.evaluate((y) => scrollTo(0, y), Math.round(track * 0.30));
await page.waitForTimeout(500);
ok('stays shut before the peak',
   (await page.getAttribute('#refuse', 'data-open')) === 'false');

// --- it fires at the peak ---------------------------------------------------
await page.evaluate((y) => scrollTo(0, y), Math.round(track * 0.68));
await page.waitForTimeout(700);
ok('fires inside the peak leg',
   (await page.getAttribute('#refuse', 'data-open')) === 'true');

ok('focus moves to the one control',
   await page.evaluate(() => document.activeElement === document.getElementById('refuse-ok')));

ok('it reports real attributes read from this browser',
   (await page.textContent('#refuse-json')).includes('"viewport": "1440x900"'));

// --- the lock actually holds ------------------------------------------------
const before = await page.evaluate(() => scrollY);
await page.mouse.wheel(0, 1200);
await page.waitForTimeout(400);
const after = await page.evaluate(() => scrollY);
ok('scroll is held while the refusal stands', before === after, `${before} -> ${after}`);
ok('layout is untouched while it holds (the track does not collapse)',
   (await page.evaluate(() => document.documentElement.scrollHeight)) === track);

// --- Escape releases it -----------------------------------------------------
await page.keyboard.press('Escape');
await page.waitForTimeout(500);
ok('Escape dismisses it', (await page.getAttribute('#refuse', 'data-open')) === 'false');
const restored = await page.evaluate(() => scrollY);
ok('the reader is left exactly where they were', Math.abs(restored - before) < 4, `${before} -> ${restored}`);

await page.mouse.wheel(0, 900);
await page.waitForTimeout(400);
ok('the wheel works again after dismissal', (await page.evaluate(() => scrollY)) > restored);

// --- it refuses you once, not on every pass ---------------------------------
await page.evaluate((y) => scrollTo(0, y), Math.round(track * 0.40));
await page.waitForTimeout(300);
await page.evaluate((y) => scrollTo(0, y), Math.round(track * 0.70));
await page.waitForTimeout(700);
ok('does not fire a second time in the same session',
   (await page.getAttribute('#refuse', 'data-open')) === 'false');

// --- the button path, on a fresh session ------------------------------------
const page2 = await ctx.newPage();
await page2.goto(URL, { waitUntil: 'networkidle' });
await page2.waitForTimeout(1000);
await page2.evaluate(() => sessionStorage.removeItem('aegis.refused'));
await page2.evaluate((y) => scrollTo(0, y), Math.round(track * 0.70));
await page2.waitForTimeout(700);
await page2.click('#refuse-ok');
await page2.waitForTimeout(400);
ok('the acknowledge button dismisses it',
   (await page2.getAttribute('#refuse', 'data-open')) === 'false');

await browser.close();
console.log(failures ? `\n${failures} failure(s)` : '\nall refusal checks pass');
process.exit(failures ? 1 : 0);
