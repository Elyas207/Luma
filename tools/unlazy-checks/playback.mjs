#!/usr/bin/env node
/**
 * Playback oracle: drives a device and measures whether audio actually advances.
 *
 * This deliberately measures the *media session position over time* rather than a log line saying a
 * url was resolved. Resolution succeeding has repeatedly looked like success while no audio was
 * produced, so only a moving playhead counts.
 */
import { execFileSync } from 'node:child_process';

const ADB = process.env.ADB || `${process.env.HOME}/Android/Sdk/platform-tools/adb`;
// Overridable so the same oracles can be run against a release build, whose package
// has no .debug suffix. A test suite that can only exercise the debug variant cannot
// tell you whether the thing you are about to ship works.
const PKG = process.env.LUMA_PKG || 'me.knighthat.kreate.debug';

const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };
const ok = (t) => { console.log(t); process.exit(0); };
const sleep = (ms) => execFileSync('sleep', [String(ms / 1000)]);

function adb(serial, args, opts = {}) {
  return execFileSync(ADB, ['-s', serial, ...args],
    { encoding: 'utf8', timeout: opts.timeout || 120000, maxBuffer: 32 * 1024 * 1024 });
}
const shell = (s, cmd) => adb(s, ['shell', ...cmd]);

/**
 * Current position of Luma's *live* media session, or null when it has none.
 *
 * `dumpsys media_session` keeps entries for sessions that are gone: after a run of force-stops this
 * device listed seven mentions of the package, one PLAYING at 64s and three stale ERROR(7) rows at
 * position 0. Reading the first match — which this did — meant the oracle reported a hard failure
 * while audio was plainly playing, and it reported it *reproducibly*, which is exactly how a broken
 * test gets believed.
 *
 * So: parse every state block, and take the one the framework updated most recently. `updated=` is
 * a monotonic timestamp, so the freshest row is the session that is actually running.
 */
function session(serial) {
  let out = '';
  try { out = shell(serial, ['dumpsys', 'media_session']); } catch { return null; }

  const candidates = [];
  for (const m of out.matchAll(
    /state=PlaybackState \{state=([A-Z]+)\((\d)\), position=(-?\d+), buffered position=(-?\d+)[^}]*?updated=(\d+)/g
  )) {
    // Only states belonging to this package: look back a little for the package name.
    const before = out.slice(Math.max(0, m.index - 3000), m.index);
    if (!before.includes(PKG)) continue;
    candidates.push({
      state: m[1], code: Number(m[2]), position: Number(m[3]),
      buffered: Number(m[4]), updated: Number(m[5])
    });
  }
  if (!candidates.length) return null;
  candidates.sort((a, b) => b.updated - a.updated);
  return candidates[0];
}

function uiTexts(serial) {
  try {
    shell(serial, ['uiautomator', 'dump', '/sdcard/ui.xml']);
    const xml = shell(serial, ['cat', '/sdcard/ui.xml']);
    return [...xml.matchAll(/text="([^"]+)"/g)].map(m => m[1]);
  } catch { return []; }
}

function tapText(serial, want) {
  shell(serial, ['uiautomator', 'dump', '/sdcard/ui.xml']);
  const xml = shell(serial, ['cat', '/sdcard/ui.xml']);
  const re = new RegExp(`text="${want.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`);
  const m = xml.match(re);
  if (!m) return false;
  const x = Math.floor((Number(m[1]) + Number(m[3])) / 2);
  const y = Math.floor((Number(m[2]) + Number(m[4])) / 2);
  shell(serial, ['input', 'tap', String(x), String(y)]);
  return true;
}

function tapDesc(serial, want) {
  shell(serial, ['uiautomator', 'dump', '/sdcard/ui.xml']);
  const xml = shell(serial, ['cat', '/sdcard/ui.xml']);
  const re = new RegExp(`content-desc="${want}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`);
  const m = xml.match(re);
  if (!m) return false;
  shell(serial, ['input', 'tap',
    String(Math.floor((Number(m[1]) + Number(m[3])) / 2)),
    String(Math.floor((Number(m[2]) + Number(m[4])) / 2))]);
  return true;
}

/** Launch, search for `query`, and start the first result. */
function startFirstResult(serial, query) {
  shell(serial, ['am', 'force-stop', PKG]);
  shell(serial, ['monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1']);
  sleep(13000);
  // Home has two shapes: on a fresh install it offers a "Search" text link, and once there is
  // history it leads with what you were playing and search collapses to an icon. Accept either, or
  // the oracle starts failing the moment the app has been used.
  if (!tapText(serial, 'Search') && !tapDesc(serial, 'Search'))
    fail('could not reach Search from home (tried the text link and the icon)');
  sleep(4500);
  shell(serial, ['input', 'text', query]);
  sleep(2000);
  shell(serial, ['input', 'keyevent', '66']);
  sleep(13000);

  // The results list reads: "Songs", "RESULTS FOR …", <title>, <artist>, <duration>, …
  // Take the entry straight after the header rather than guessing: an earlier version filtered by
  // `length > 6`, which silently skipped short titles like "Yellow" and tapped the *artist* row
  // instead — that opens the artist page, so nothing ever played and the oracle blamed the app.
  const texts = uiTexts(serial);
  const header = texts.findIndex(t => t.startsWith('RESULTS FOR'));
  const title = header >= 0
    ? texts.slice(header + 1).find(t => t !== 'Songs' && !/^\d+:\d+$/.test(t))
    : undefined;
  if (!title) fail(`no search results rendered for "${query}" (saw: ${texts.slice(0, 6).join(' | ')})`);
  if (!tapText(serial, title)) fail(`could not tap result "${title}"`);
  console.log(`started: ${title}`);
  return title;
}

/** Poll the session until it plays, then confirm the playhead keeps moving. */
function measure(serial, { settleMs = 45000, windowMs = 40000, minAdvanceMs = 15000 } = {}) {
  const deadline = Date.now() + settleMs;
  let first = null;
  while (Date.now() < deadline) {
    const s = session(serial);
    if (s && s.code === 3 && s.position > 0) { first = s; break; }
    sleep(3000);
  }
  if (!first) {
    const s = session(serial);
    fail(`never reached PLAYING with a moving position (last state=${s ? s.state + ' pos=' + s.position : 'none'})`);
  }
  console.log(`playing at position=${first.position}ms`);

  const end = Date.now() + windowMs;
  let last = first;
  while (Date.now() < end) {
    sleep(5000);
    const s = session(serial);
    if (!s) fail('media session disappeared mid-playback');
    if (s.code === 7) fail(`playback errored after ${last.position}ms of audio`);
    if (s.position > last.position) last = s;
  }
  const advanced = last.position - first.position;
  console.log(`advanced ${advanced}ms over the observation window (from ${first.position} to ${last.position})`);
  if (advanced < minAdvanceMs)
    fail(`playhead advanced only ${advanced}ms; expected at least ${minAdvanceMs}ms of continuous audio`);
  return last;
}

const [, , cmd, serial, ...rest] = process.argv;

if (cmd === 'plays') {
  const query = rest[0] || 'maher';
  startFirstResult(serial, query);
  measure(serial);
  ok('PLAYBACK_ADVANCES_OK');
}

// Beyond one chunk: the transport splits fetches at 256 KB, so audio continuing well past the
// first chunk boundary is what proves continuation rather than a lucky first range.
if (cmd === 'sustained') {
  const query = rest[0] || 'maher';
  startFirstResult(serial, query);
  measure(serial, { settleMs: 45000, windowMs: 90000, minAdvanceMs: 60000 });
  ok('PLAYBACK_SUSTAINED_OK');
}

if (cmd === 'transport') {
  const query = rest[0] || 'maher';
  startFirstResult(serial, query);
  const before = measure(serial, { settleMs: 45000, windowMs: 15000, minAdvanceMs: 5000 });

  // Pause must actually stop the playhead.
  shell(serial, ['cmd', 'media_session', 'dispatch', 'pause']);
  sleep(4000);
  const paused = session(serial);
  if (!paused || paused.code === 3) fail('pause did not leave the PLAYING state');
  const p1 = paused.position;
  sleep(5000);
  const p2 = session(serial).position;
  if (Math.abs(p2 - p1) > 1500) fail(`position moved ${p2 - p1}ms while paused`);
  console.log('pause holds the playhead');

  // Resume must move it again.
  shell(serial, ['cmd', 'media_session', 'dispatch', 'play']);
  sleep(8000);
  const resumed = session(serial);
  if (!resumed || resumed.code !== 3) fail(`resume did not return to PLAYING (state=${resumed && resumed.state})`);
  if (resumed.position <= p2) fail('resume did not advance the playhead');
  console.log('resume advances the playhead');
  ok('PLAYBACK_TRANSPORT_OK');
}

// Audio focus: the car case. A navigation prompt or a call takes transient focus, and Luma must
// yield and then come back on its own — a music app that stays silent after a Maps instruction is
// useless in a car. An incoming call is a real focus loss the emulator can generate on demand.
if (cmd === 'focus') {
  const query = rest[0] || 'maher';
  startFirstResult(serial, query);
  const before = measure(serial, { settleMs: 60000, windowMs: 15000, minAdvanceMs: 5000 });

  adb(serial, ['emu', 'gsm', 'call', '5551234']);
  sleep(9000);
  // Judge by the playhead, not the state flag: a session can still advertise PLAYING while the
  // audio has actually stopped, and the thing that matters in a car is whether the track is
  // running away underneath the navigation prompt.
  const during = session(serial);
  if (!during) fail('media session vanished during the call');
  sleep(6000);
  const during2 = session(serial);
  const drift = during2.position - during.position;
  if (drift > 1500)
    fail(`playhead advanced ${drift}ms during an incoming call (state=${during2.state}) — audio did not yield`);
  console.log(`call takes focus: state=${during2.state}, playhead held (${drift}ms drift)`);

  adb(serial, ['emu', 'gsm', 'cancel', '5551234']);
  sleep(14000);
  const after = session(serial);
  if (!after) fail('media session vanished after the call');
  if (after.code !== 3) fail(`did not resume after the call ended (state=${after.state})`);
  sleep(6000);
  const moving = session(serial);
  if (moving.position <= after.position) fail('resumed state reported but the playhead is not moving');
  console.log(`resumed and advancing (${after.position} -> ${moving.position})`);
  ok('PLAYBACK_AUDIOFOCUS_OK');
}

fail(`unknown subcommand: ${cmd}`);
