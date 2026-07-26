# CLAUDE.md — Handrail

Voice-first GUI agent for Indian languages. Android, Kotlin, native.
~30-hour hackathon build. Ship order matters more than elegance.

## WHAT WE ARE BUILDING

Two modes over ONE shared perception layer:

1. **NARRATION mode** (build FIRST, must work standalone):
   User invokes assistant → capture current screen via
   AccessibilityService node tree → summarize via Sarvam 105B →
   speak summary via Bulbul TTS in user's language.
   This is the safety net and the fallback demo. It must never
   depend on any Takeover code.

2. **TAKEOVER mode** (build SECOND):
   User speaks a task (Saaras STT) → agent loop: perceive →
   Sarvam 105B picks next action (tool call) → execute →
   narrate the step aloud (Bulbul) → re-perceive → repeat.
   HARD STOP before any payment, send, submit, or irreversible
   action: narrate what's about to happen, hand the final tap
   back to the user. This is a deliberate feature, not a limitation.

## STACK — LOCKED. DO NOT SUBSTITUTE, DO NOT SUGGEST ALTERNATIVES.

- STT: Sarvam Saaras.
  TTS: Sarvam Bulbul. No OpenAI, no Gemini, no local models.
- Reasoning + tool calls: behind a single OpenAI-compatible
  chat-completions client. Two configs, selected by ONE flag
  (`ACTIVE_PROVIDER` in LlmConfig.kt):
    OPENROUTER (dev only): base https://openrouter.ai/api/v1/,
      model "openai/gpt-4o-mini", key OPENROUTER_API_KEY.
      (No Anthropic Console API key on hand — only a Max
      subscription, which doesn't grant direct API access.
      OpenRouter stands in for dev-only reasoning against the
      same chat-completions format. Never ships.)
    SARVAM (ship):     base https://api.sarvam.ai/v1/,
      model "sarvam-105b", key SARVAM_API_KEY.
  NO provider-specific code paths anywhere outside LlmConfig.
  No Anthropic SDK, no OpenAI SDK — plain OkHttp against the
  chat-completions format, which both providers accept.
  HARD DEADLINE: flip to SARVAM by Saturday 12:00 and never
  flip back. All agent-loop prompt tuning happens against
  Sarvam, because tool-call behavior differs between models
  and the demo runs on Sarvam.
  Assume the weaker model when writing prompts: strict tool
  schemas, explicit "respond with exactly one tool call, no
  prose," few-shot example in the system prompt. If it works
  on 105B it works on Claude; the reverse is not guaranteed.
- Sarvam is OpenAI-compatible:
  base_url = https://api.sarvam.ai/v1
  header    = Authorization: Bearer $SARVAM_API_KEY
  model     = "sarvam-105b"
  Use plain OkHttp + kotlinx.serialization for the client.
  Do NOT pull in a heavyweight LLM SDK.
- API key comes from BuildConfig field fed by local.properties
  (SARVAM_API_KEY). Never hardcode. Never commit.
- Perception: AccessibilityService + AccessibilityNodeInfo tree.
  NO screenshots, NO screen-capture APIs, NO vision. Sarvam has
  no screen-grounding VLM; do not write code that assumes one.
- Perception layer is adapted from MobileClaw (MIT). Retain
  license notices in adapted files; add credit section to README.
- Kotlin only. No Flutter, no React Native, no Compose Multiplatform.
  Jetpack Compose for the (minimal) UI is fine.
- minSdk 26, targetSdk 34.

## ARCHITECTURE

Single module app. Packages:

- `perception/` — AccessibilityService, node-tree capture,
  filtering, serialization. Shared by both modes.
- `actions/` — action executor: tap, setText, scroll, back, home.
- `agent/` — Takeover loop: state machine, prompt assembly,
  tool-call parsing, step budget, stop conditions.
- `speech/` — Saaras STT (record → upload), Bulbul TTS
  (text → audio → play). Queue TTS so narrations don't overlap.
- `sarvam/` — HTTP client, request/response models, retry.
- `ui/` — ASSIST entry activity, overlay bubble, settings
  (language picker, service-enabled check).

### Entry point
Activity with `android.intent.action.ASSIST` intent filter —
claims the digital-assistant slot (long-press home). NOT
VoiceInteractionService (too much boilerplate for the time).
Floating bubble via TYPE_APPLICATION_OVERLAY is a fallback
trigger only; build it after ASSIST works.

The ASSIST activity must be visually minimal and translucent —
the user needs to see the screen being narrated/operated under it.

## PERCEPTION RULES — NON-NEGOTIABLE

Raw node trees blow the context window. Before ANY Sarvam call:

1. Walk the tree; keep only nodes that are clickable, editable,
   scrollable, checkable, or have non-empty text/contentDescription.
2. Assign each kept node a short ref: e1, e2, e3…
3. Serialize compactly, one line per node:
   `e4 Button "Pay now" clickable bounds=[540,1820]`
   (bounds = center point, only for actionable nodes).
4. Keep a ref→AccessibilityNodeInfo map for the executor.
   Refresh it on every perception pass — stale nodes crash.
5. Cap serialized output at ~150 nodes; if over, prefer on-screen
   actionable nodes and truncate deepest-nested text.

## ACTION EXECUTION RULES

- Primary: `performAction` on the node resolved from the ref.
- `ACTION_SET_TEXT` FAILS SILENTLY on WebViews and custom edit
  fields. After every setText, re-read the node's text. If it
  didn't take: focus the node, then fall back to clipboard +
  ACTION_PASTE. If that fails, report failure to the agent loop
  honestly — never pretend a step succeeded.
- Unlabeled ImageButtons: fall back to a coordinate tap at
  bounds center via dispatchGesture. The model may target by
  bounds when no label exists — the serializer must include
  center coords for actionable nodes for exactly this reason.
- After every action, wait for TYPE_WINDOW_CONTENT_CHANGED or
  a 1.5s timeout before re-perceiving. Never re-perceive
  immediately — you'll read the pre-action screen.

## AGENT LOOP RULES

- Tool schema (OpenAI function-calling format): `tap(ref)`,
  `set_text(ref, text)`, `scroll(direction)`, `back()`, `home()`,
  `done(summary)`, `ask_user(question)`, `blocked(reason)`.
- Hard step budget: 12 steps, then force `blocked`.
- Irreversible-action guard is CODE, not prompt-only: before
  executing a tap, check the target node's text/description
  against a keyword list (pay, send, confirm, order, submit,
  transfer, buy — plus Hindi/Kannada equivalents). On match:
  do NOT tap; narrate what the button will do and stop. Belt
  and suspenders: the system prompt also instructs the model
  to stop, but the code check is the real guard.
- Every step: narrate the action in the user's language BEFORE
  executing it. Narration is the product, and it covers latency.
- If the model returns malformed tool calls twice in a row,
  bail to `blocked` and narrate that. No infinite retry.
- Login walls / OTP screens: detect (password fields, "OTP",
  "verify") → `ask_user`, hand control back. Never attempt to
  read or enter OTPs.

## KNOWN FAILURE MODES — ASSUME ALL OF THESE HAPPEN

- setText silent failure (handled above — verify + clipboard fallback).
- Context blowup (handled above — filter + refs + cap).
- Unlabeled buttons (handled above — coordinate fallback).
- Stale AccessibilityNodeInfo after screen change — always
  re-resolve refs from the latest perception pass, never cache
  nodes across passes. Call recycle() where required pre-API-33.
- OEM background restrictions (Xiaomi/Oppo): keep everything
  foreground-driven from the ASSIST activity + accessibility
  service; do not rely on background services surviving. Add a
  settings screen that deep-links to accessibility settings and
  verifies the service is actually connected.
- TTS/STT network latency: stream nothing fancy — but fire the
  Bulbul narration request for step N while the perception pass
  for N+1 runs. Simple coroutine overlap, not a pipeline framework.

## BUILD ORDER — DO NOT REORDER, DO NOT SKIP AHEAD

1. Project scaffold, manifest (ASSIST filter, accessibility
   service declaration + config XML, permissions), Sarvam client
   with a smoke-test function.
2. Perception: capture + filter + serialize + ref map. Log output
   to Logcat; verify by eyeball on Settings app before wiring AI.
3. NARRATION mode end-to-end: ASSIST invoke → perceive →
   105B summary (in target language) → Bulbul → speaker.
   **This is the checkpoint. It must demo on its own.**
4. Action executor with setText verification + coordinate fallback.
5. Saaras STT capture in ASSIST activity.
6. Agent loop wiring perception + executor + guard + narration.
7. Overlay bubble fallback trigger.
8. Polish: language picker, error narrations ("I couldn't do
   that"), demo hardening.

Do not start N+1 until N runs on a device. When asked to build
"the next thing," consult this list.

## HOW TO WORK

- Complete files, not fragments. If a file changes, output the
  whole file.
- No new dependencies without stating why in one line. Default
  deps: OkHttp, kotlinx-serialization, kotlinx-coroutines,
  Compose BOM. That's it.
- No feature expansion. If something isn't in this file, it's
  out of scope; say so and continue.
- No placeholder/mocked Sarvam responses in shipped code paths.
  Mocks allowed only in tests.
- Anything not demoable on a phone by Saturday evening is
  wasted work. Optimize for the 40-second filmed demo:
  narration of a real screen + one multi-step takeover task
  with a visible hard-stop handback.
- Error handling: fail loud in Logcat, fail graceful in voice
  ("Mujhe yeh nahi mila / ಸಿಗಲಿಲ್ಲ" — narrate failures in the
  user's language, never silence).

## CREDITS / LICENSE

Perception layer adapted from MobileClaw (MIT). Keep original
copyright notices in adapted files. README must credit MobileClaw
and state the MIT terms for those portions.