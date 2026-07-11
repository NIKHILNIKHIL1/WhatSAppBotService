# Plan: WhatsApp Voice-Note Ordering (Option A)

> Status: **Planned — not yet implemented** (drafted 2026-07-11)
>
> Goal: let a customer place an order by sending a WhatsApp **voice message** in their
> selected language ("2 litre doodh, 1 paneer bhejo"). No telephony involved — the voice
> note is just an alternative input method for filling the cart. Everything from
> "cart exists" onward reuses the existing checkout flow untouched.

## Flow overview

```
Customer sends voice note ("2 litre doodh, 1 paneer")
  → Meta webhook delivers audio message (media id)
  → download audio bytes from Meta CDN
  → speech-to-text (external STT API)
  → Claude parses transcript against the tenant's product catalog → structured cart lines
  → read-back: existing cart summary + CONFIRM/CANCEL buttons (CHECKOUT_CONFIRM step)
  → existing handleCheckoutConfirm does revalidation, stock, order creation, receipt
```

Key design decision: **voice only fills the cart.** Price revalidation, idempotency
(`waMessageId`), stock locking, receipts, and the visual confirm step all come free from
the current checkout path — the customer always confirms on screen before an order is
created.

## New code (new package `whatsapp/voice` or top-level `speech` module)

### 1. `SpeechTranscriptionService`
- Interface + one implementation.
- Input: audio bytes + MIME type + language hint; output: transcript string.
- Claude does not accept audio, so STT is a separate provider — OpenAI
  `gpt-4o-transcribe`/Whisper or Google Speech-to-Text both handle Hindi/English/French
  well, including code-switched "Hinglish". Call via Spring `RestClient` (same pattern
  as `WhatsAppClient`).
- Language hint from `session.languageCode()` / `customer.getPreferredLanguageCode()`;
  provider auto-detect as fallback.

### 2. `VoiceOrderParser`
- One Claude Messages API call using the official Java SDK
  (`com.anthropic:anthropic-java`), model `claude-opus-4-8`.
- Prompt contains the tenant's active product list (id, name, price, unit) and the
  transcript. Uses **structured outputs** (`outputConfig(ParsedOrder.class)`) so the
  response is a typed record — no JSON wrangling:

  ```java
  record ParsedLine(Long productId, BigDecimal quantity, String spokenText, boolean uncertain) {}
  record ParsedOrder(List<ParsedLine> lines, List<String> unmatchedItems) {}
  ```

- Instructions: map spoken names (any language, colloquial) onto catalog products,
  extract quantities/units, and put anything unmatchable into `unmatchedItems` rather
  than guessing.
- Catalogs are small (dairy shop) so the whole product list fits in the prompt. Put the
  static instruction text first and the per-call catalog/transcript after, so the
  instruction prefix is cacheable.

### 3. `VoiceOrderService`
- Orchestrates download → transcribe → parse → session update, and owns all failure
  messaging. Keeps `WhatsAppConversationService` from growing another 200 lines.

## Touch points in existing files

| File | Change |
|---|---|
| `WhatsAppWebhookPayload.java` | Add `AudioContent audio` record (`id`, `mime_type`, `voice` flag) beside `ImageContent` (~line 53), plus `audioMediaId()` accessor. |
| `WhatsAppWebhookService.processMessage` (line 131) | Currently only images become `InboundMedia`; audio messages fall through silently. Add an audio branch. **Important:** do NOT funnel audio through `InboundMedia` — `handleMessage` treats *any* media as a photo concern and forwards it to the vendor (`WhatsAppConversationService:132`). Either add a `type` discriminator to `InboundMedia` or pass audio as a separate parameter/method. |
| `WhatsAppConversationService` | New `handleVoiceOrder(...)`: send an immediate "🎤 Got it, processing…" ack (STT + parse takes 2–5s, no typing indicator on WhatsApp), call `VoiceOrderService`, merge parsed lines into `session.cart()`, then reuse the existing `sendCheckoutConfirmation` / `sendCartSummary` flow. Read back unmatched items ("I couldn't find *organic ghee* — it's not in the menu") in the customer's language. |
| `WhatsAppClient.java` | Add `downloadMedia(mediaId, accessToken)` — currently only upload/send-by-id exist. Meta download is two steps: `GET /{media-id}` returns a short-lived CDN URL, then `GET` that URL with the same bearer token returns the bytes. Meta voice notes are OGG/Opus (~16 kB per 10 s), accepted by both STT providers. |
| `application.yml` | New `app.speech.*` block: provider, API keys via env vars (`SPEECH_API_KEY`, `ANTHROPIC_API_KEY`), a max-duration guard (reject notes > ~60 s), and an `enabled` flag so this can ship dark. |
| `pom.xml` | Add `com.anthropic:anthropic-java`. STT goes through `RestClient` — no extra dependency. |
| i18n bundles (all 5: `messages`, `_en`, `_hi`, `_fr`, `_wo`) | New keys: `bot.voice.ack`, `bot.voice.readback_header`, `bot.voice.unmatched_item`, `bot.voice.could_not_understand`, `bot.voice.too_long`, `bot.voice.unsupported_language`. |

**No `OrderChannel` change needed** — the order still exits through the WhatsApp confirm
flow, so `OrderChannel.WHATSAPP` stays correct (and the `AuditChannel.valueOf` mapping in
`OrderService:157` is untouched).

## Failure handling (mirrors existing patterns)

- STT/parse/download errors: log, send `bot.voice.could_not_understand` in the
  customer's language, **leave the session and cart exactly as they were** — same
  philosophy as the checkout failure path at `WhatsAppConversationService:757`.
- Empty/zero-confidence parse: same message; suggest tapping through the menu instead.
- Wolof (`wo`) tenants: no mainstream STT supports Wolof — detect via the language hint
  and reply with `bot.voice.unsupported_language`, pointing to the button flow. Ship this
  check from day one.
- Idempotency: the inbound audio message is already covered by the
  `existsByWaMessageId` check (`WhatsAppWebhookService:100`), so Meta webhook retries
  cannot double-process a voice note. Store the transcript in the
  `WhatsAppMessage.payload` JSON for auditability.

## Explicitly deferred (phase 2)

- **Twilio tenants**: inbound audio arrives as a public media URL, not a media id — the
  download step differs (basic-auth GET). Gate voice handling on
  `MessagingProvider.META` initially and log-and-skip for Twilio.

Vendor notification is confirmed working (verified 2026-07-11) — voice orders exit
through the same `handleCheckoutConfirm` path as typed WhatsApp orders, so the vendor
alert needs no changes for this feature.

## Testing

- Unit: `VoiceOrderParser` with a stubbed Claude client (canned structured responses) —
  quantity extraction, unmatched items, mixed-language names. `VoiceOrderService` with a
  stub transcriber.
- Integration: webhook payload with an audio message → assert session cart populated and
  confirmation buttons sent, with mocked STT/LLM beans (existing Testcontainers setup
  covers the DB side).
- Manual smoke test: real voice notes in Hindi and English against a sandbox tenant
  before enabling per-tenant.

## Build order

1. `WhatsAppClient.downloadMedia` + webhook audio plumbing (verifiable alone by logging
   audio arrival).
2. `SpeechTranscriptionService` + config.
3. `VoiceOrderParser` (Claude structured outputs) + unit tests.
4. `handleVoiceOrder` wiring + i18n keys + read-back flow.
5. Feature flag on, sandbox tenant smoke test, then per-tenant rollout.

Estimated effort: **3–5 working days** including testing.

Running costs: ~$0.005 per voice note for STT; under a cent per parse call at typical
transcript sizes (parser model can be swapped for a cheaper one later if cost matters —
a deliberate trade-off, not the default).
