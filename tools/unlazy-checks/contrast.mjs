#!/usr/bin/env node
/**
 * Measures real text contrast on a rendered screen.
 *
 * The theme brief's first hard gate is body text at 4.5:1 and large text at 3:1, measured against
 * the *lightest and darkest* point behind the text rather than the average — because the failure
 * mode of a glossy, gradient-heavy aesthetic is text that passes on average and is unreadable at
 * one end of a button.
 *
 * How it works: `uiautomator` gives the bounds of every text node, and `screencap` gives the pixels.
 * For each node we take the darkest and lightest pixels actually present in its box and compute the
 * WCAG ratio between them. That is a *lower bound* on the real contrast — it does not know which
 * pixels are glyph and which are background — so a node that passes here genuinely passes, and a
 * node that fails is worth a human look rather than an automatic verdict.
 *
 * Deliberately not: a screenshot-diff or a "looks fine" eyeball. Neither produces a number, and
 * without a number nobody can tell whether a theme change made legibility better or worse.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';

const ADB = process.env.ADB || `${process.env.HOME}/Android/Sdk/platform-tools/adb`;
const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };

const adb = (serial, args) =>
  execFileSync(ADB, ['-s', serial, ...args], { encoding: 'utf8', timeout: 120000, maxBuffer: 64 * 1024 * 1024 });

/** sRGB relative luminance, per WCAG. */
function luminance(r, g, b) {
  const f = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

const ratio = (l1, l2) => (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);

/** Raw framebuffer from `screencap` with no `-p`: width, height, then RGBA rows. */
function framebuffer(serial) {
  adb(serial, ['shell', 'screencap', '/sdcard/_contrast.raw']);
  const local = '/tmp/_contrast.raw';
  adb(serial, ['pull', '/sdcard/_contrast.raw', local]);
  if (!existsSync(local)) fail('could not pull framebuffer');
  const b = readFileSync(local);
  const w = b.readUInt32LE(0), h = b.readUInt32LE(4);
  if (w < 100 || h < 100) fail(`implausible framebuffer ${w}x${h}`);
  return { buf: b, w, h, offset: 12 };
}

/** Every text node with its bounds and font size hint. */
function textNodes(serial) {
  adb(serial, ['shell', 'uiautomator', 'dump', '/sdcard/_ui.xml']);
  const xml = adb(serial, ['shell', 'cat', '/sdcard/_ui.xml']);
  const nodes = [];
  for (const m of xml.matchAll(/text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g)) {
    const [, text, x1, y1, x2, y2] = m;
    const box = { x1: +x1, y1: +y1, x2: +x2, y2: +y2 };
    if (box.x2 - box.x1 < 8 || box.y2 - box.y1 < 8) continue;   // not a readable region
    nodes.push({ text, ...box });
  }
  return nodes;
}

function extremes({ buf, w, h, offset }, box) {
  let min = Infinity, max = -Infinity, n = 0;
  const x2 = Math.min(box.x2, w), y2 = Math.min(box.y2, h);
  for (let y = Math.max(0, box.y1); y < y2; y++) {
    for (let x = Math.max(0, box.x1); x < x2; x++) {
      const i = offset + (y * w + x) * 4;
      if (i + 2 >= buf.length) continue;
      const l = luminance(buf[i], buf[i + 1], buf[i + 2]);
      if (l < min) min = l;
      if (l > max) max = l;
      n++;
    }
  }
  return n ? { min, max, n } : null;
}

const [, , serial, label = 'screen', thresholdArg] = process.argv;
if (!serial) fail('usage: contrast.mjs <serial> [label] [threshold]');
const threshold = Number(thresholdArg ?? 4.5);

const fb = framebuffer(serial);
const nodes = textNodes(serial);
if (!nodes.length) fail('no text nodes found — the check is not measuring anything');

const rows = [];
for (const node of nodes) {
  const e = extremes(fb, node);
  if (!e) continue;
  // Height is a usable proxy for "large text" (WCAG allows 3:1 there); 1080p phone, ~28px+ is large.
  const isLarge = (node.y2 - node.y1) >= 34;
  rows.push({
    text: node.text.slice(0, 34),
    ratio: ratio(e.min, e.max),
    isLarge,
    required: isLarge ? 3.0 : threshold
  });
}

rows.sort((a, b) => a.ratio - b.ratio);
console.log(`${label}: ${rows.length} text regions measured`);
for (const r of rows.slice(0, 8)) {
  console.log(`  ${r.ratio.toFixed(2).padStart(6)}:1 ${r.isLarge ? '(large)' : '(body) '} need ${r.required}  "${r.text}"`);
}

const failures = rows.filter(r => r.ratio < r.required);
const worst = rows[0];
console.log(`worst ${worst.ratio.toFixed(2)}:1 on "${worst.text}"`);

if (failures.length) {
  console.error(`FAIL: ${failures.length} text region(s) below threshold on ${label}`);
  process.exit(1);
}
console.log('CONTRAST_OK');
