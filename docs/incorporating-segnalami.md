# What SegnalaMi can do that this service cannot, and how to close the gap

Two repos make up one product. **SegnalaMi** (`FlXZ22/impact-lab`, Node + TypeScript) is
the citizen-facing half: chat intake, photos, voice, an officer triage portal and an MCP
server. **This service** (`fabianhogger/segnalazioni-impact-lab-be`, Java + Spring Boot)
is the dispatch half: it classifies a report and names the responsible body.

Today they meet at exactly one call — `POST /api/v1/reports` with `{text, latitude,
longitude}` — and that call carries less than either side already knows. This document
lists what SegnalaMi does that this service cannot execute, and proposes an order for
absorbing it.

---

## The gaps

| # | SegnalaMi can | This service today | Verdict |
|---|---|---|---|
| A | Attach photos to a report, EXIF-stripped and re-encoded, and reason over them with vision | `POST /api/v1/attachments` + `attachmentIds` **exists**; the classifier is text-only and the frontend never sends them | Wiring + vision |
| B | Advance a report through `open → received → in_progress → resolved`, recording every change in `report_status_events`, and serve it back | Write-once. 7 `ReportStatus` values, no `PATCH`/`PUT`/`DELETE`, no events table, no callback | **Missing** |
| C | Triage: Claude assigns `priority` 1–5, `department`, `affected_people`, `missing_info`, `reason`; officers override priority and department | `Classification` has category/severity/confidence/title/description/location/`missingInformation`. No priority, no affected-people, no human override of anything | **Missing** |
| D | Refuse a submission *before storing it* — `emergency`, `natural_event`, `off_topic`, `abusive`, `harmful` | Emergency only, and *after* classification, as a routing short-circuit | Partial |
| E | Draft → update → confirm → send, with `idempotency_key`, revision conflicts (`CONFLITTO_REVISIONE`) and a `conferma_token` (the MCP contract) | One shot `POST /reports`. No drafts, no confirmation step, no idempotency | **Missing** |
| F | List a citizen's own reports with cursor paging; delete a report with its photo and timeline atomically | `GET /reports` returns *everyone's*, unauthenticated; no delete | **Missing** |
| G | Speak Italian and English | `nextStep.message` is Italian only | **Missing** |
| H | Transcribe voice (Groq Whisper) | — | **Not a gap.** Text arrives already transcribed; this belongs in the client |
| I | Rate-limit, enforce same-origin, set CSP | None | **Missing**, and required before exposure |
| J | Let a citizen answer a follow-up question and continue | `NEEDS_INFO` is a dead end: no resume endpoint, so the citizen must submit a brand-new report with a new id | **Missing** |
| K | Serve a browser app directly | No CORS configuration at all | **Missing** |

### The taxonomy problem, which blocks C

There are **three** category vocabularies across the two repos, two priority scales and
three recipient vocabularies. Nothing maps any of them:

| | Categories | Recipients | Priority |
|---|---|---|---|
| **This service** | 17: `STRADE`, `PULIZIA_STRADE`, `RIFIUTI`, `ARREDO_URBANO`, `VERDE_PUBBLICO`, `CIMITERI`, `VEICOLI_ABBANDONATI`, `ILLUMINAZIONE_PUBBLICA`, `GUASTO_ELETTRICO`, `TRASPORTO_PUBBLICO`, `RUMORE`, `AMBIENTE`, `SICUREZZA_NON_URGENTE`, `PERSONE_SENZA_DIMORA`, `AFFITTI_IRREGOLARI`, `EMERGENZA`, `ALTRO` | 9 agency ids in `agencies.yml` | `Severity`: `ROUTINE`/`URGENT`/`EMERGENZA` |
| **SegnalaMi triage** | 6: `mobilita`, `strade_marciapiedi`, `verde_pubblico`, `servizi_pubblici`, `sicurezza`, `altro` | 6: `comune_di_milano`, `atm`, `trenord_rfi`, `green_space_operator`, `local_police`, `unknown` | integer 1–5 |
| **MCP contract** | 7: `illuminazione_pubblica`, `rifiuti`, `strade_e_marciapiedi`, `arredo_urbano`, `verde_pubblico`, `segnaletica`, `altre` | 3 codes: `A2A_IP`, `AMSA`, `COMUNE_MI` | `bassa`/`media`/`alta` |

The MCP set is the odd one out: it is *not* a generalisation of this service's enum
(`segnaletica` sits inside `STRADE` here, `altre` ≠ `ALTRO`), and `A2A_IP` has no
counterpart in `agencies.yml` at all — nor does SegnalaMi's `trenord_rfi`.

**Declare this service's `Category` enum and `agencies.yml` canonical**, and ship two
translation tables beside it — one for the triage vocabulary, one for the MCP contract —
rather than trying to merge three enums. Severity and the 1–5 priority are genuinely
different axes (danger vs. queue order) and should both exist, not be collapsed.

This also removes today's duplication: every report is sent to Claude **twice** — once by
SegnalaMi's `assessor.ts` and once by this service's `ClaudeClassifier` — asking
overlapping questions with different prompts in different processes.

## Not to be ported, deliberately

`docs/architecture.md` already rules these out, and absorbing SegnalaMi must not quietly
re-open them: driving the Comune portal with a citizen's SPID, calling PULIamo's private
endpoints, submitting ATM's form headlessly, auto-dialling staffed phone lines. The
`AssistedHandoffAdapter` exists and `ApiAgencyAdapter` throws for this reason. Likewise,
`dry-run` email and the recipient allowlist stay on, and nothing may imply to a citizen
that an authority has been contacted.

---

## Proposed order

### Phase 1 — Make the existing seam carry what it already knows (small)

0. **Blocker first: attachment metadata does not survive a restart.**
   `FilesystemAttachmentStore` writes bytes to `./data/attachments/<uuid>` but keeps the
   metadata in a `ConcurrentHashMap` (line 29). After a restart `metadata(id)` returns
   empty, `IntakeService.resolve()` drops it with `flatMap(Optional::stream)`, and the
   photo vanishes from the pipeline **silently** — the file is still on disk. Persist the
   metadata (an `attachments` table) before sending any photo through this seam, or
   Phase 1 will appear to work and then lose photos in production.

1. **Send the photo.** `impact-lab/src/routing.ts` posts only text and coordinates. Add:
   upload the sanitized JPEG to `POST /api/v1/attachments`, pass the returned id in
   `attachmentIds`. No Java change — the endpoint and `EmailAgencyAdapter`'s attachment
   handling already exist and are untested end to end.
2. **Give the classifier eyes.** `ClassifierPrompt.userMessage()` sends only the text and
   `"Foto allegate: si|no"` — a boolean. The image bytes never reach the model; a photo frequently
   settles a category that text leaves ambiguous. Add an image content block when the
   report has attachments, mirroring SegnalaMi's `assessor.ts`.
3. **English.** Add a `language` field to `ReportRequest` (default `it`), thread it into
   `ClassifierPrompt` and the `nextStep.message` templates. This closes the mixed-language
   card the chat currently has to apologise for.

### Phase 2 — A lifecycle, so a report is not write-once

4. New table `report_status_events(report_id, status, at)` plus `PATCH
   /api/v1/reports/{id}` to advance status and `GET /api/v1/reports/{id}/progress`
   returning `{status, timeline}` and **never the report content** — mirroring SegnalaMi's
   progress endpoint, which deliberately withholds it.
5. While here, close the `NEEDS_INFO` dead end: an endpoint that accepts answers against
   an existing report id and re-runs classification, instead of forcing a new submission
   with a new id and a lost history.
6. Keep the two status axes separate and documented: this service's `ReportStatus`
   describes the *handoff*; SegnalaMi's `open/received/in_progress/resolved` describes the
   *work*. Do not collapse them.

This phase is what unblocks the MCP server's `stato_pratica` and `elenco_pratiche`.

### Phase 3 — Absorb triage, so the officer portal can be backed by this service

6. Extend `Classification` (or add an `Assessment` value object) with `priority` 1–5,
   `affectedPeople`, `reason`, and a `department` derived through the mapping table from
   Phase 0 of the taxonomy work.
7. Persist the structured location. Today only the flattened `locationText` is stored;
   `LocationHint`'s street / civic / landmark / municipio are thrown away, so no triage
   view can filter or group by Municipio.
8. Add override persistence and endpoints for the two fields officers actually change:
   priority and recipient body. Record who changed what and when.
9. Then point `impact-lab`'s officer portal at this service and **delete its second Claude
   call**. One classification per report.

### Phase 4 — Everything the MCP contract assumes

The MCP server is today a **facade over SegnalaMi, not over a City backend**: drafts live
in process memory, sent practices in `~/.config/segnalami-mcp/pratiche.json`, and
`mcp/docs/architecture.md` specifies auth/SPID, `POST /api/allegati`, idempotency keys and
a richer error taxonomy that are **not implemented anywhere**. That unimplemented spec is
the clearest statement of what this service should become, and it is already written down
— including an Italian error taxonomy (`CONFLITTO_REVISIONE`, `BOZZA_SCADUTA`,
`CHIAVE_IDEMPOTENZA_IN_CONFLITTO`, `ALLEGATO_TROPPO_GRANDE`, `NON_AUTENTICATO`, …) worth
adopting verbatim rather than inventing a second one.


9. A draft aggregate: create, update with an expected revision (409 on mismatch), confirm
   (freeze + token), send (consumes the token). Idempotency keys on create and send.
10. Authentication and per-client rate limiting. `GET /api/v1/reports` currently returns
    every report, with raw citizen text and contact, to anyone who can reach the port.
    This is not a Java-side failing alone: SegnalaMi's officer portal and every
    `/api/operations` route are equally unauthenticated. The MCP contract already speaks
    of a *cittadino autenticato*, which exists on neither side.
11. Per-citizen listing and deletion, with the photo and timeline removed in the same
    transaction, as SegnalaMi already does.

Phase 4 is also the point at which `municipality-handoff.md` Step 2 — a real *endpoint di
conferimento* — becomes implementable, because a confirmed, idempotent, authenticated
submission is exactly what a public body would require.

---

### Two small mismatches to settle while doing the above

- **Text length**: SegnalaMi caps a report at 2000 characters, this service at 5000.
  `routing.ts` already slices to 5000, so nothing breaks today, but the limits should
  agree once the two share a contract.
- **Fail-open vs fail-closed**: SegnalaMi's moderation gate fails *closed* — an API error
  returns 503 and stores nothing. Its assistant, routing and triage all fail *open*. If
  the moderation gate moves here (gap D), keep it failing closed; that asymmetry is
  deliberate.

## One caveat that applies to every phase

There is **no web-layer test at all** — no `@WebMvcTest`, no MockMvc, so no controller,
validation, serialization or multipart behaviour is covered. The 16 existing tests
exercise routing, the heuristic classifier and the two email safety rails, which are the
right things to protect, but every endpoint added below would land untested by default.
Add the first `@WebMvcTest` alongside Phase 1.

Two existing behaviours to keep in mind while extending the pipeline: dry-run email
returns `ReportStatus.SENT`, i.e. it reports success without sending; and
`ApiAgencyAdapter` throws `UnsupportedOperationException`, which `ApiExceptionHandler`
does not map, so it would surface as a 500 rather than a clear error.

## Suggested first move

Phase 1 is roughly a day and is the only phase that improves the demo. Phases 2–4 are
sequenced by dependency: the lifecycle (2) is required by the MCP status tools, triage (3)
is required before the portal can drop its own database, and drafts plus auth (4) are
required before anything is transmitted to a real municipal system.
