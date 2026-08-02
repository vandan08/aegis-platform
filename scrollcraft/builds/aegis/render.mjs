// Flies the camera through world.html and writes a PNG per frame.
//
// This is the step that replaces a text-to-video model. The camera path is deterministic,
// so the seam law in worldflight.md section 6 is satisfied by construction: leg N's last
// frame and leg N+1's first frame are the same camera position, computed from the same
// spline. There is no chaining, no reroll, and nothing to match by eye.
//
//   node render.mjs --preview          seven keyframe stills, for composition review
//   node render.mjs                    the full frame sequence
import { chromium } from 'playwright-core';
import { mkdirSync, rmSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PREVIEW = process.argv.includes('--preview');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const FPS = 25;

// Seconds of film per leg. The peak gets 12s and every other leg 8s, which is how it
// ends up with the largest span on the track WITHOUT breaking the one-pace rule:
// weight/seconds stays 0.216vh per second everywhere.
const LEGS = [8, 8, 8, 12, 8, 8];

const browser = await chromium.launch({ executablePath: CHROME });
const page = await browser.newPage({
  viewport: { width: 1600, height: 900 },
  deviceScaleFactor: 1,
});
await page.goto(pathToFileURL(resolve('world/world.html')).href, { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(1200);

if (PREVIEW) {
  mkdirSync('lab/keys', { recursive: true });
  for (let i = 0; i < 7; i++) {
    const c = await page.evaluate((u) => window.setCamera(u), i);
    await page.waitForTimeout(250);
    await page.screenshot({ path: `lab/keys/k${i}.png` });
    console.log(`k${i}  x=${c.x.toFixed(0)} y=${c.y.toFixed(0)} s=${c.s.toFixed(3)}`);
  }
  await browser.close();
  process.exit(0);
}

for (let leg = 0; leg < LEGS.length; leg++) {
  const dir = `raw/frames/leg${leg + 1}`;
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });

  const frames = LEGS[leg] * FPS;
  for (let f = 0; f < frames; f++) {
    // u runs 0..1 across the leg, offset by the leg index, so the spline is continuous
    // across the whole flight rather than restarted per leg.
    const u = leg + f / frames;
    await page.evaluate((v) => window.setCamera(v), u);
    await page.screenshot({ path: `${dir}/${String(f).padStart(4, '0')}.png` });
  }
  // The leg's final frame is the next leg's first frame. Written explicitly so the
  // encoded clips actually share it rather than nearly sharing it.
  await page.evaluate((v) => window.setCamera(v), leg + 1);
  await page.screenshot({ path: `${dir}/${String(frames).padStart(4, '0')}.png` });
  console.log(`leg ${leg + 1}: ${frames + 1} frames`);
}

await browser.close();
console.log('done');
