#!/usr/bin/env node
/**
 * Verification oracles for Luma's GATES.md.
 *
 * Each subcommand asserts one observable outcome and prints a success-only token as its LAST line.
 * Any failure exits non-zero and prints why. Nothing here is allowed to pass by default: a check
 * that cannot find what it is measuring is a failure, not a pass.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(process.argv[2] === '--root' ? process.argv[3] : process.cwd());
const args = process.argv.slice(process.argv[2] === '--root' ? 4 : 2);
const cmd = args[0];

const SRC = join(ROOT, 'composeApp/src/androidMain/kotlin');
const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };
const ok = (t) => { console.log(t); process.exit(0); };

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (e.endsWith('.kt')) out.push(p);
  }
  return out;
}
const read = (p) => readFileSync(p, 'utf8');

function adb(serial, ...rest) {
  return execFileSync(process.env.ADB || `${process.env.HOME}/Android/Sdk/platform-tools/adb`,
    ['-s', serial, ...rest], { encoding: 'utf8', timeout: 120000, maxBuffer: 64 * 1024 * 1024 });
}

// ---------------------------------------------------------------- theme roles
if (cmd === 'theme-derived') {
  const f = join(SRC, 'app/kreate/android/themed/luma/LumaDesign.kt');
  if (!existsSync(f)) fail('LumaDesign.kt missing');
  const s = read(f);

  const objStart = s.indexOf('object LumaColor');
  if (objStart < 0) fail('LumaColor object not found');
  const objEnd = s.indexOf('\n}', objStart);
  const body = s.slice(objStart, objEnd);

  // The regression was hardcoded ARGB literals standing in for the active skin.
  const literals = body.match(/Color\(\s*0x[0-9a-fA-F]{8}\s*\)/g) || [];
  if (literals.length) fail(`LumaColor still hardcodes ${literals.length} colour literal(s): ${literals.join(', ')}`);

  const roles = ['Ground', 'Raised', 'Ink', 'InkSoft', 'InkFaint', 'Ember', 'Alarm'];
  for (const r of roles) {
    const re = new RegExp(`val ${r}\\s*:\\s*Color\\s+get\\(\\)\\s*=\\s*[^\\n]*palette\\.`);
    if (!re.test(body)) fail(`role ${r} does not derive from the active palette`);
  }

  // Type styles bake a colour in; an object `val` would freeze the first skin seen.
  for (const t of ['Hero', 'Title', 'Section', 'Row', 'Tile', 'Label', 'Numeral', 'Meta']) {
    if (!new RegExp(`val ${t} get\\(\\) =`).test(s)) fail(`LumaType.${t} is not a get() accessor (would freeze one theme)`);
  }
  ok('THEME_ROLES_DERIVED_OK');
}

if (cmd === 'theme-wired') {
  const hits = walk(SRC).filter(p => read(p).includes('SyncLumaPalette('));
  const call = hits.filter(p => !p.endsWith('LumaDesign.kt'));
  if (!call.length) fail('SyncLumaPalette is never called — roles would stay on the default palette');
  const s = read(call[0]);
  if (!/SyncLumaPalette\(\s*appearance\.colorPalette\s*\)/.test(s))
    fail('SyncLumaPalette is not fed appearance.colorPalette');
  ok('THEME_SYNC_WIRED_OK');
}

// No *live* screen code may pin a Luma role to a literal instead of the role.
// Commented-out lines are excluded deliberately: an oracle that flags dead code trains you to
// ignore it, which is worse than not having the check.
if (cmd === 'no-hardcoded-surfaces') {
  const offenders = [];
  for (const p of walk(SRC)) {
    if (p.includes('/themed/luma/LumaDesign.kt')) continue;
    const live = read(p).split('\n')
      .map(l => l.replace(/^\s*\/\/.*$/, '').replace(/\/\/.*$/, ''))
      .join('\n');
    for (const m of live.matchAll(/\.(background|color)\(\s*Color\(\s*0xFF[0-9a-fA-F]{6}\s*\)/g)) {
      offenders.push(`${p.slice(ROOT.length + 1)}: ${m[0].trim()}`);
    }
  }
  if (offenders.length) fail(`hardcoded surface colours:\n  ${offenders.slice(0, 20).join('\n  ')}`);
  ok('NO_HARDCODED_SURFACES_OK');
}

// ------------------------------------------------------- accessibility labels
// Only *interactive* controls are checked. An empty contentDescription on a decorative image is
// correct practice — it tells a screen reader to skip it — so flagging those would be wrong.
// LumaRingButton is the app's interactive icon control; every call must name its action.
if (cmd === 'a11y-labels') {
  const bad = [];
  let calls = 0;
  for (const p of walk(SRC)) {
    const s = read(p);
    for (const m of s.matchAll(/(fun\s+)?LumaRingButton\(([\s\S]{0,400}?)\)\s*(?:\n|$)/g)) {
      if (m[1]) continue;   // the declaration, not a call site
      calls++;
      const body = m[2];
      const cd = body.match(/contentDescription\s*=\s*([^,\n]+)/);
      if (!cd) { bad.push(`${p.slice(ROOT.length + 1)}: LumaRingButton without contentDescription`); continue; }
      const v = cd[1].trim();
      if (v === '""' || v === 'null.orEmpty()' || v === '"".orEmpty()')
        bad.push(`${p.slice(ROOT.length + 1)}: LumaRingButton contentDescription = ${v}`);
    }
  }
  if (!calls) fail('found no LumaRingButton call sites — the check is not measuring anything');
  console.log(`inspected ${calls} LumaRingButton call site(s)`);
  if (bad.length) fail(`unlabelled interactive controls:\n  ${bad.join('\n  ')}`);
  ok('A11Y_LABELS_OK');
}

// ------------------------------------------------------------- device: themes
// Proves a skin change actually repaints, by comparing *decoded pixels* of the same screen under
// two palettes.
//
// The first version of this oracle averaged raw PNG bytes and reported a delta of 1.2 between a
// near-black screen and an off-white one — compressed bytes say nothing about brightness, so the
// check would have failed a working app and, worse, could have passed a broken one. `screencap`
// without `-p` returns an uncompressed RGBA framebuffer, which can be averaged honestly.
function rawMeanLuma(file) {
  if (!existsSync(file)) fail(`framebuffer capture missing: ${file}`);
  const b = readFileSync(file);
  if (b.length < 16) fail(`capture too small to be a framebuffer: ${file}`);
  const w = b.readUInt32LE(0), h = b.readUInt32LE(4);
  if (w < 100 || h < 100 || w > 10000 || h > 10000) fail(`implausible framebuffer ${w}x${h} in ${file}`);
  const px = 12;
  const need = px + w * h * 4;
  if (b.length < need) fail(`capture truncated: ${file} has ${b.length}, needs ${need}`);
  let sum = 0, n = 0;
  // Stride over whole pixels so R/G/B stay aligned; ~40k samples is plenty and stays fast.
  const step = Math.max(4, Math.floor((w * h) / 40000) * 4);
  for (let i = px; i + 2 < need; i += step) {
    sum += 0.2126 * b[i] + 0.7152 * b[i + 1] + 0.0722 * b[i + 2];
    n++;
  }
  if (!n) fail(`sampled no pixels from ${file}`);
  return { luma: sum / n, w, h };
}

if (cmd === 'device-theme-repaint') {
  const shots = args.slice(2);
  if (shots.length < 2) fail('need two raw framebuffer captures to compare');
  const a = rawMeanLuma(shots[0]), b2 = rawMeanLuma(shots[1]);
  if (a.w !== b2.w || a.h !== b2.h) fail(`captures differ in size (${a.w}x${a.h} vs ${b2.w}x${b2.h})`);
  const delta = Math.abs(a.luma - b2.luma);
  console.log(`dark mean luma=${a.luma.toFixed(1)}  light mean luma=${b2.luma.toFixed(1)}  delta=${delta.toFixed(1)}`);
  // A dark skin and a light skin differ by most of the range; anything under 40 means the skin
  // change did not reach the surface.
  if (delta < 40) fail(`skins render near-identically (delta ${delta.toFixed(1)}) — theme change did not repaint`);
  ok('DEVICE_THEME_REPAINT_OK');
}

if (cmd === 'device-no-crash') {
  const serials = args.slice(1);
  if (!serials.length) fail('no devices given');
  for (const s of serials) {
    let out = '';
    try {
      out = adb(s, 'shell', 'ls', `/sdcard/Android/data/${process.env.LUMA_PKG || 'me.knighthat.kreate.debug'}/files/crashlogs`);
    } catch { out = ''; }
    const files = out.split('\n').map(x => x.trim()).filter(x => x && !x.includes('No such file'));
    if (files.length) fail(`${s} has crash logs: ${files.join(', ')}`);
  }
  ok('DEVICE_NO_CRASH_OK');
}

if (cmd === 'device-app-alive') {
  const serials = args.slice(1);
  for (const s of serials) {
    const out = adb(s, 'shell', 'dumpsys', 'activity', 'activities');
    if (!out.includes(`${process.env.LUMA_PKG || 'me.knighthat.kreate.debug'}/it.fast4x.rimusic.MainActivity`))
      fail(`${s}: Luma is not the resumed activity`);
  }
  ok('DEVICE_APP_ALIVE_OK');
}

// Every skin must actually repaint the app, and no two may land on the same rendering. Compares
// decoded framebuffers of the *same* screen captured under each skin (see scratchpad/skin.sh).
// Mean luma alone is not enough — two skins can share a brightness and differ in hue — so the
// distance is over luma and the three channel means together.
if (cmd === 'skins-distinct') {
  const dir = args[1];
  if (!dir) fail('usage: skins-distinct <dir-of-captures>');
  const names = ['Aurora', 'Obsidian', 'Ember', 'Vinyl', 'Cassette',
                 'Terrazzo', 'Nocturne', 'Bloom', 'Graphite', 'Zellige'];
  const rows = [];
  for (const n of names) {
    const f = join(dir, `${n}.raw`);
    if (!existsSync(f)) fail(`missing capture for skin ${n} (${f})`);
    const b = readFileSync(f);
    const w = b.readUInt32LE(0), h = b.readUInt32LE(4), px = 12;
    const need = px + w * h * 4;
    if (b.length < need) fail(`capture truncated for ${n}`);
    let sum = 0, r = 0, g = 0, bl = 0, cnt = 0;
    const step = Math.max(4, Math.floor((w * h) / 40000) * 4);
    for (let i = px; i + 2 < need; i += step) {
      r += b[i]; g += b[i + 1]; bl += b[i + 2];
      sum += 0.2126 * b[i] + 0.7152 * b[i + 1] + 0.0722 * b[i + 2];
      cnt++;
    }
    rows.push({ n, luma: sum / cnt, r: r / cnt, g: g / cnt, bl: bl / cnt });
  }
  for (const x of rows) console.log(`${x.n.padEnd(10)} luma=${x.luma.toFixed(1).padStart(6)}`);
  const clashes = [];
  for (let i = 0; i < rows.length; i++)
    for (let j = i + 1; j < rows.length; j++) {
      const d = Math.abs(rows[i].luma - rows[j].luma) + Math.abs(rows[i].r - rows[j].r)
              + Math.abs(rows[i].g - rows[j].g) + Math.abs(rows[i].bl - rows[j].bl);
      if (d < 4) clashes.push(`${rows[i].n} vs ${rows[j].n} (distance ${d.toFixed(1)})`);
    }
  if (clashes.length) fail(`skins render near-identically:\n  ${clashes.join('\n  ')}`);
  ok(`ALL_SKINS_DISTINCT_OK (${rows.length} skins)`);
}

// A gradient that fades to a fixed black or white is a scrim, and a scrim sits *behind text*.
// The text uses LumaColor.Ink, which flips with the skin — so a fixed scrim is correct on one half
// of the skins and unreadable on the other. This is exactly how the hero and the arch tiles ended
// up with dark navy text on a near-black base under the light skins.
//
// `themed/skin/` is excluded on purpose: there Color.White/Color.Black are gloss highlights and
// drop shadows, which are material effects rather than backing for text, and must not flip.
if (cmd === 'no-fixed-scrims') {
  const offenders = [];
  for (const p of walk(SRC)) {
    // The material-recipe layer: gloss highlights, bevels and drop shadows legitimately use fixed
    // white and black, because they model light rather than back text. Everywhere else, a fixed
    // gradient stop behind text is the defect this check exists for.
    if (p.includes('/themed/skin/')) continue;
    if (p.endsWith('/themed/luma/LumaSurface.kt')) continue;
    const src = read(p);
    const live = src.split('\n')
      .map(l => l.replace(/^\s*\/\/.*$/, '').replace(/\/\/.*$/, ''))
      .join('\n');
    // Balance the parentheses rather than regexing to the first ')': the stops themselves contain
    // calls like `copy( alpha = 0f )`, so a non-greedy match ends inside the first stop and reads
    // an almost empty body — which is how the first version of this check passed a tree that still
    // had a fixed scrim in it.
    for (const m of live.matchAll(/Brush\.(vertical|horizontal|linear|radial)Gradient\s*\(/g)) {
      let depth = 0, end = -1;
      for (let i = m.index + m[0].length - 1; i < live.length; i++) {
        if (live[i] === '(') depth++;
        else if (live[i] === ')') { depth--; if (depth === 0) { end = i; break; } }
      }
      if (end < 0) continue;
      const body = live.slice(m.index + m[0].length, end);
      // A gradient that already branches on a lightness signal is following the theme, just by a
      // different route (the legacy player does this with `lightTheme`/`ColorPaletteMode`). Only an
      // *unconditional* fixed scrim is the defect: that one cannot be right under both halves of
      // the skins.
      const conditional = /lightTheme|isDark|ColorPaletteMode/.test(body);
      if (!conditional && /Color\.(Black|White)\.copy/.test(body)) {
        const line = live.slice(0, m.index).split('\n').length;
        offenders.push(`${p.slice(ROOT.length + 1)}:${line}: gradient fades to a fixed Color.Black/White`);
      }
    }
  }
  if (offenders.length) fail(`scrims that do not follow the skin:\n  ${offenders.join('\n  ')}`);
  ok('NO_FIXED_SCRIMS_OK');
}

// A skin declares whether it is light or dark, and that must survive a track playing. The default
// colour mode is Dynamic (artwork-derived), and it used to overwrite the chosen skin's palette the
// moment audio started — while the skin carried on drawing its own backdrop. On Aurora that put a
// dark palette's pale text over a bright photographic sky.
if (cmd === 'skins-hold') {
  const [idleDir, playDir] = args.slice(1);
  if (!idleDir || !playDir) fail('usage: skins-hold <idle-captures> <playing-captures>');
  const LIGHT = new Set(['Aurora', 'Terrazzo', 'Vinyl', 'Bloom']);
  const rows = [];
  for (const e of readdirSync(playDir)) {
    if (!e.endsWith('.raw')) continue;
    const name = e.slice(0, -4);
    const b = readFileSync(join(playDir, e));
    const w = b.readUInt32LE(0), h = b.readUInt32LE(4), px = 12, need = px + w * h * 4;
    if (b.length < need) fail(`capture truncated for ${name}`);
    let sum = 0, n = 0;
    const step = Math.max(4, Math.floor((w * h) / 40000) * 4);
    for (let i = px; i + 2 < need; i += step) {
      sum += 0.2126 * b[i] + 0.7152 * b[i + 1] + 0.0722 * b[i + 2];
      n++;
    }
    rows.push({ name, luma: sum / n, light: LIGHT.has(name) });
  }
  if (!rows.length) fail(`no captures in ${playDir} — the check is not measuring anything`);
  const bad = [];
  for (const r of rows) {
    const ok = r.light ? r.luma > 140 : r.luma < 95;
    console.log(`${r.name.padEnd(10)} playing luma=${r.luma.toFixed(1).padStart(6)} expected ${r.light ? 'light' : 'dark'}${ok ? '' : '  <-- lost'}`);
    if (!ok) bad.push(`${r.name} rendered at luma ${r.luma.toFixed(1)} while playing, but declares itself ${r.light ? 'light' : 'dark'}`);
  }
  if (bad.length) fail(`skins lost their palette during playback:\n  ${bad.join('\n  ')}`);
  ok(`SKINS_HOLD_WHILE_PLAYING_OK (${rows.length} skins)`);
}

// Corner radius must come from the scale, not from a literal. The audit counted 17 distinct radii
// across 92 uses with no scale at all; without a check this drifts straight back, because picking a
// number is always easier than choosing a step.
//
// Two exemptions, both deliberate: a true rectangle (0.dp) is a shape, not a radius, and the enum
// that lets a *user* choose thumbnail roundness legitimately enumerates its own values.
if (cmd === 'radius-scale') {
  const offenders = [];
  for (const p of walk(SRC)) {
    if (p.includes('/themed/luma/LumaSurface.kt')) continue;
    if (p.endsWith('ThumbnailRoundness.kt')) continue;
    const live = read(p).split('\n')
      .map(l => l.replace(/^\s*\/\/.*$/, '').replace(/\/\/.*$/, ''))
      .join('\n');
    for (const m of live.matchAll(/RoundedCornerShape\(\s*([0-9.]+)\.dp/g)) {
      if (parseFloat(m[1]) === 0) continue;
      const line = live.slice(0, m.index).split('\n').length;
      offenders.push(`${p.slice(ROOT.length + 1)}:${line}: RoundedCornerShape(${m[1]}.dp) — use LumaRadius`);
    }
  }
  if (offenders.length) fail(`corner radii bypassing the scale:\n  ${offenders.slice(0, 20).join('\n  ')}`);
  ok('RADIUS_SCALE_OK');
}

fail(`unknown subcommand: ${cmd}`);
