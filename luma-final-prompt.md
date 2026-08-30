# LUMA — FINAL OBSESSIVE PRODUCT AUDIT, QA, OPTIMISATION & POLISH PHASE

I am going to leave you to work on this.

This is now the most serious quality-control phase of the project.

I do NOT want a quick review.

I do NOT want you to check a few obvious bugs and tell me everything is working.

I want you to perform an extremely deep, systematic, obsessive audit of the ENTIRE Luma application.

Your mindset throughout this task should be:

THINK HARD → INSPECT EVERYTHING → TEST EVERYTHING → QUESTION EVERYTHING → FIND WEAKNESSES → FIX THEM → OPTIMISE → TEST AGAIN → THINK EVEN HARDER.

Repeat that cycle continuously.

Do not assume anything.

Do not trust that something works simply because:

- The code looks correct
- The UI looks correct
- It worked once
- There are no obvious errors
- A button appears to be connected
- A feature was previously implemented

VERIFY IT.

Actually trace the behaviour.

Actually inspect the implementation.

Actually test realistic user journeys.

Actually test failure scenarios.

Actually think about what happens when users behave unexpectedly.

This application needs to be treated as if it is about to be released to real users.

The goal is to make Luma feel as close to genuinely flawless, polished and production-ready as possible.

---

# THE CORE RULE: DO NOT SKIP ANYTHING

You must systematically work through the ENTIRE application.

Do not focus only on the major screens.

Do not ignore smaller screens.

Do not ignore settings.

Do not ignore modals.

Do not ignore empty states.

Do not ignore functionality that seems minor.

Small broken things destroy the feeling of a polished product.

If the user can:

- See it
- Tap it
- Open it
- Change it
- Configure it
- Navigate to it
- Depend on it

Then it needs to be audited.

I want you to actively search for things that I have forgotten to mention.

Do not rely entirely on this checklist.

Use your own judgement.

Explore the app.

Trace every feature.

Look through the codebase.

Understand how everything connects.

Build a complete mental model of the application before making careless changes.

---

# PHASE 1 — UNDERSTAND THE ENTIRE APPLICATION FIRST

Before blindly fixing random issues, deeply understand the application.

Map out:

- The application's architecture
- Major screens
- Navigation structure
- State management
- Playback architecture
- Network/media loading
- Theme architecture
- Personalisation logic
- Device-specific behaviour
- Car Mode behaviour
- QR/device transfer behaviour
- Data persistence
- Settings
- Responsive layout system

Understand:

WHAT EXISTS.

WHAT IS CONNECTED.

WHAT IS PARTIALLY IMPLEMENTED.

WHAT IS ONLY UI.

WHAT IS ACTUALLY FUNCTIONAL.

WHAT IS BROKEN.

WHAT IS INCONSISTENT.

WHAT COULD BREAK.

Do not make random changes without understanding downstream effects.

Think about dependencies.

A change that fixes one screen but breaks another is not a successful fix.

---

# PHASE 2 — COMPLETE USER JOURNEY AUDIT

Think like multiple different real users.

Trace complete journeys from beginning to end.

For example:

## USER JOURNEY: FIRST-TIME USER

Test:

1. Opening the app for the first time.
2. Onboarding.
3. Initial preferences.
4. Theme selection.
5. Entering the app.
6. Discovering content.
7. Playing something.
8. Creating/saving something.
9. Returning later.

Ask:

- Is anything confusing?
- Are there unnecessary steps?
- Does anything feel unfinished?
- Does the app explain itself naturally?
- Does onboarding create value?
- Is anything annoying?

---

## USER JOURNEY: DAILY USER

Imagine someone opening Luma multiple times per day.

Test:

- Returning to recently played content.
- Resuming playback.
- Finding favourites.
- Using the queue.
- Using search.
- Changing content quickly.
- Letting autoplay continue.
- Switching devices.

Ask:

> Is this genuinely pleasant to use every day?

The app should not require unnecessary thinking.

Common actions should be fast.

---

## USER JOURNEY: CAR USER

Imagine:

1. User enters their car.
2. Their Android tablet turns on.
3. They open Google Maps or Waze.
4. They need navigation.
5. They want audio playing.
6. They open or return to Luma.
7. They want to quickly play something familiar.
8. Navigation starts giving instructions.
9. They need to skip something.
10. They need to change content with minimal distraction.

Audit the entire experience around this realistic scenario.

Ask:

- What would annoy the driver?
- What requires too many taps?
- What is difficult to read?
- What controls are too small?
- What information is unnecessary while driving?
- What should intelligently appear first?

Fix those problems.

---

# PHASE 3 — COMPLETE NAVIGATION AUDIT

Navigation must be audited obsessively.

Trace EVERY navigation route.

Check:

## Primary navigation

- Home
- Search
- Library
- Personalisation
- Settings
- Car Mode
- Any other primary destinations

## Secondary navigation

- Artist/creator pages
- Reciter pages
- Albums
- Playlists
- Media details
- Queue
- Now Playing
- Theme selection
- Device management
- Downloads

## Temporary navigation

- Modals
- Bottom sheets
- Menus
- Dropdowns
- Context menus
- Dialogs

For every route, ask:

- Does it open correctly?
- Does it always open correctly?
- Does it pass the correct data?
- Does back navigation work?
- Does closing restore the previous state?
- Can navigation state become corrupted?
- Can rapid navigation break anything?
- Does it behave correctly after theme changes?
- Does it behave correctly on different devices?

There should be ZERO dead ends.

ZERO buttons that silently fail.

ZERO confusing back behaviour.

ZERO inaccessible screens.

ZERO routes that lead to blank or broken content.

---

# PHASE 4 — AUDIT EVERY INTERACTIVE ELEMENT

Literally inspect every interactive element.

For every:

- Button
- Icon
- Toggle
- Slider
- Dropdown
- Input
- Search field
- Filter
- Tab
- Menu item
- Card
- Media item
- Gesture

Ask:

1. What is this supposed to do?
2. Does it actually do it?
3. Does the UI immediately communicate the result?
4. Does state update correctly?
5. Does it persist correctly?
6. What happens if pressed repeatedly?
7. What happens if the network fails?
8. What happens if the user leaves the screen immediately?
9. Does the result remain correct when returning?

Do not leave decorative controls that imply functionality without actually providing it.

---

# PHASE 5 — PLAYBACK SYSTEM: EXTREME RELIABILITY AUDIT

Playback is the heart of Luma.

This needs one of the deepest audits in the entire project.

Test:

## Media loading

- Fast connections
- Slow connections
- Failed requests
- Missing media
- Invalid URLs
- Interrupted loading
- Switching media during loading

## Playback controls

- Play
- Pause
- Resume
- Previous
- Next
- Seeking forward
- Seeking backward
- Rapid seeking
- Progress updates
- Duration
- End of media

## Rapid interactions

Test users rapidly:

- Pressing play/pause repeatedly
- Skipping multiple tracks
- Switching content during loading
- Opening/closing the player
- Changing queues

The application should never become confused about what is actually playing.

The UI state and actual media state must ALWAYS remain synchronised.

Look specifically for:

- Race conditions
- Stale callbacks
- Duplicate requests
- Multiple simultaneous playback instances
- Incorrect progress
- Stuck buffering
- UI showing incorrect media
- Previous media continuing
- Audio overlapping
- Autoplay starting unexpectedly

Fix the root causes rather than adding superficial patches.

---

# PHASE 6 — AUDIO FOCUS, BACKGROUND BEHAVIOUR & SYSTEM INTEGRATION

Because this app will be used heavily in a car environment, deeply audit media integration with Android.

Think about:

- Audio focus
- Temporary interruptions
- Other apps playing audio
- Navigation instructions
- Bluetooth
- System media controls
- Notifications
- App switching
- Background behaviour

When Luma loses audio focus, behaviour should be intelligent.

For example:

- Temporary navigation instructions may require ducking.
- Longer interruptions may require pausing.
- Audio should recover smoothly afterwards when appropriate.

Avoid abrupt or confusing behaviour.

Make sure the app behaves like a proper Android media application rather than an isolated web/app experience.

---

# PHASE 7 — GOOGLE MAPS / WAZE / GPS COEXISTENCE

This is EXTREMELY important.

Luma will frequently be used while another application is handling navigation.

Do NOT assume Luma is always fullscreen.

Test and optimise:

- Split-screen
- Multi-window
- Resizing
- Reduced available width
- Reduced available height
- Returning from Maps/Waze
- Keeping media alive while Maps is visible

The app should respond gracefully when its available space changes.

Do not let layouts collapse simply because the window is smaller.

Create responsive behaviour for realistic split-screen car usage.

Think about useful compact experiences such as:

- Compact Now Playing
- Essential controls only
- Compact queue
- Driving Companion mode

The user should be able to navigate with Maps/Waze while Luma continues being an excellent media companion.

Do not fake deep integration that Android does not support.

Research what is realistically possible and implement proper platform-compatible behaviour.

---

# PHASE 8 — PHONE EXPERIENCE: AUDIT AS A SEPARATE PRODUCT

The phone version needs to be treated as its own experience.

Do NOT just check whether it fits on a smaller screen.

Think deeply about:

## Ergonomics

- Thumb reach
- Bottom controls
- One-handed use
- Touch target sizes

## Navigation

- Bottom navigation
- Back behaviour
- Screen transitions
- Deep navigation

## Content

- How much information belongs on screen?
- Is anything too dense?
- Is anything unnecessarily hidden?

## Keyboard

- Does search behave correctly?
- Does the keyboard cover important UI?
- Can users dismiss it naturally?

Test multiple phone sizes.

Make sure the smallest supported device still feels intentional.

Ask constantly:

> Would I genuinely enjoy using this app every day on my phone?

---

# PHASE 9 — TABLET EXPERIENCE: DESIGN FOR THE SPACE

Treat the tablet as a first-class platform.

Do NOT simply stretch mobile layouts.

Think about:

- Landscape hierarchy
- Multi-column layouts
- Persistent navigation
- Simultaneous information
- Larger media artwork
- Queue visibility
- Contextual panels

Test both large and smaller tablet windows.

Make sure extra space is used intelligently.

Avoid:

- Giant stretched cards
- Huge empty spaces
- Mobile UI floating in the centre
- Unnecessarily massive typography

The tablet should feel purpose-built and premium.

---

# PHASE 10 — CAR MODE: OVERTHINK EVERY DETAIL

Car Mode should receive an entirely separate design and usability audit.

Imagine the user is moving.

They cannot safely perform complicated interactions.

Therefore optimise for:

- Extremely large touch targets
- Instant recognition
- Minimal steps
- High readability
- High contrast
- Clear hierarchy
- Minimal cognitive load

Ask:

> Can this action be completed faster?

> Does this require unnecessary attention?

> Is this information useful while driving?

> Could this interaction be simplified?

The most important actions should always be extremely easy:

- Play
- Pause
- Next
- Previous
- Favourites
- Smart mixes
- Queue
- Returning to something familiar

Car Mode must feel genuinely exceptional.

Not just visually cool.

Actually useful.

---

# PHASE 11 — SMART PERSONALISATION AUDIT

Luma is supposed to feel incredibly intelligent.

Make sure the intelligence is genuinely useful.

Audit:

- Smart autoplay
- Skip behaviour
- Automatic reduction of disliked content
- Repeated listens
- Favourites
- Listening patterns
- Time/context behaviour where appropriate
- Smart mixes
- Recommendations
- Repetition prevention

Think carefully about confidence.

The app must not make extreme conclusions from one action.

For example:

One skip ≠ permanent dislike.

Repeated deliberate behaviour = stronger signal.

Users must always be able to:

- See what Luma learned
- Understand why
- Undo it
- Change it
- Reset it

The intelligence should feel:

SUBTLE.

HELPFUL.

SURPRISINGLY ACCURATE.

NOT annoying.

NOT creepy.

NOT overconfident.

---

# PHASE 12 — THEME SYSTEM: AUDIT THE ENTIRE APP IN EVERY THEME

Do not just switch themes on the Home screen and call it done.

Systematically inspect ALL major screens in ALL themes.

Check:

- Navigation
- Home
- Search
- Library
- Now Playing
- Queue
- Playlists
- Settings
- Car Mode
- Modals
- Sheets
- Inputs
- Loading
- Errors

Check:

- Contrast
- Readability
- Component consistency
- Icons
- Shadows
- Materials
- Backgrounds
- Motion

Theme switching should NEVER leave:

- Old colours
- Broken components
- Incorrect contrast
- Partially themed screens

Each theme should feel like a complete world.

Not merely a recolour.

---

# PHASE 13 — PERFORMANCE AUDIT

A premium application must feel fast.

Inspect for:

- Unnecessary re-renders
- Expensive calculations
- Duplicate requests
- Memory leaks
- Slow scrolling
- Laggy transitions
- Inefficient media loading
- Poor caching

Optimise carefully.

Do not introduce unnecessary complexity.

Prioritise improvements that genuinely improve the real user experience.

---

# PHASE 14 — LOADING, EMPTY & ERROR STATES

Every major feature needs intentional states.

## Loading

The user should understand:

- Something is happening
- What is loading
- That the app has not frozen

## Empty

Empty states should be useful.

Do not create dead empty screens.

Guide users naturally.

## Error

Errors should:

- Explain the problem simply
- Avoid technical jargon
- Offer recovery
- Preserve user context

Test failures deliberately.

---

# PHASE 15 — ANIMATIONS & MICROINTERACTIONS

Audit all movement.

Every animation should have a reason.

Improve:

- Navigation
- Player expansion
- Mini-player transitions
- Theme transitions
- Buttons
- Sheets
- Menus
- Queue interactions
- Loading

The application should feel alive.

But never slow.

Never overly animated.

Never distracting.

Respect reduced-motion settings where appropriate.

---

# PHASE 16 — ACCESSIBILITY & REAL-WORLD USABILITY

Think beyond ideal conditions.

Audit:

- Touch target sizes
- Text readability
- Contrast
- Screen scaling
- Long text
- Missing artwork
- Different device sizes

Make the app resilient.

Do not assume perfect content and perfect users.

---

# PHASE 17 — TRY TO DESTROY THE APP

Actively try to break it.

Test chaotic behaviour:

- Rapid tapping
- Rapid navigation
- Switching themes repeatedly
- Switching tracks repeatedly
- Opening multiple screens quickly
- Losing internet
- Restoring internet
- Closing/reopening the app
- Interrupting media
- Switching between apps

Find weaknesses before real users do.

---

# THE REQUIRED WORKING LOOP

Throughout this entire task, constantly repeat:

1. THINK HARD.
2. INSPECT.
3. TEST.
4. QUESTION THE IMPLEMENTATION.
5. FIND WEAKNESSES.
6. FIX THE ROOT CAUSE.
7. IMPROVE THE EXPERIENCE.
8. OPTIMISE.
9. TEST AGAIN.
10. THINK EVEN HARDER.

DO NOT rush through the checklist.

DO NOT check a box and move on without verifying.

DO NOT settle for “probably fine.”

---

# FINAL DEFINITION OF DONE

The app is NOT done because you reached the end of this prompt.

It is only done when you have genuinely gone through the entire application and can confidently say that:

- Every major feature was inspected
- Every navigation path was checked
- Every visible interaction was verified
- Playback was deeply tested
- Phone was deeply optimised
- Tablet was deeply optimised
- Car Mode was deeply optimised
- Maps/Waze coexistence was considered and improved
- Themes were systematically tested
- Smart features were reviewed critically
- Edge cases were considered
- Obvious performance issues were addressed
- Loading/empty/error states were polished
- The design was improved wherever weaknesses were discovered

And even then:

DO ONE FINAL PASS.

Look again.

Question everything again.

Try to find things you missed.

I specifically do not want a surface-level audit.

I want obsessive attention to detail.

Keep thinking.

Keep optimising.

Keep improving.

Keep testing.

Then test again.

The goal is not simply for Luma to function.

The goal is for a real user to use Luma and feel:

**“Everything about this feels intentional. Everything works. Nothing annoys me. This feels like a genuinely exceptional product.”**

That is the standard.
