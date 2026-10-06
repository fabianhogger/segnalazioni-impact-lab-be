# Consolidating onto one backend and one app

## Why

The product is currently three processes, three UIs, two databases and two repos, and the
split between them does not follow a meaningful boundary. **Both** backends call Claude to
classify a report, **both** persist reports, and **three** different category vocabularies
are in use. That duplication is the incoherence — not the number of files.

The decision taken: **Spring Boot becomes the only backend.** The Node server is retired;
`public/` becomes static resources served by Java. The citizen UI and the officer portal
stay as two routes of that one app. The MCP server stays in the tree as a theoretical
design artifact, wired to nothing.

This is the most expensive of the options considered — realistically weeks, not days,
and the Java side has **zero web-layer tests** today. The sequencing below exists to keep
a working demo at every step rather than to make it fast.

## Target

```
segnalazioni-impact-lab-be        ONE deployable, ONE database
  src/main/resources/static/      citizen UI  (/)  + officer UI (/officer)
  adapter/in/web/                 the whole HTTP surface
  application/                    intake · moderation · triage queue · dispatch
  adapter/out/                    claude · storage · email · images
  mcp/                            kept, not wired, marked theoretical
```

`impact-lab` keeps the Impact Lab submission README, the screencasts and the history. It
stops being deployable. (If you would rather keep the UI in its own repo and copy it in at
build time, that changes Phase 5 only.)

---

## What has to move

| Node | Java landing place | Notes |
|---|---|---|
| `src/domain/validation.ts`, `privacy.ts` | extend `ReportRequest` + a `PersonalDataGuard` | Smallest piece; do it first as a warm-up |
| `src/images.ts` (sharp) | new `ImageSanitizer` | **Hardest dependency.** See risks |
| `src/moderation.ts` | `Moderator` port + `ClaudeModerator` | Must keep failing **closed** |
| `src/assistant.ts` | `Assistant` port + `ClaudeAssistant` | Free text, 4 canned fallbacks |
| `src/operations/assessor.ts` | fold into `ClaudeClassifier` | **Do not port as a second Claude call** |
| `src/operations/worker.ts` | `@Scheduled` + DB claim | Postgres `FOR UPDATE SKIP LOCKED` already in the Node version |
| `src/transcription/groq.ts` | `Transcriber` port + `GroqTranscriber` | Multipart POST, 30 s timeout |
| `src/repository/*` + `db/*.sql` | JPA entities + **Flyway** | Java has `ddl-auto: update` and no migrations today |
| `src/storage/*` | `ImageStore` port (local / Supabase) | `AttachmentStore` port already exists |
| rate limit, CSP, origin check | a `OncePerRequestFilter` | Java has none of these |
| `public/`, `public/officer/` | `src/main/resources/static/` | Vendored Leaflet, see risks |

### Two things to decide once, up front

**1. One classification, not two.** Today `ClaudeClassifier` answers "which body?" and
`assessor.ts` answers "how urgent, who is affected?". Merge them into a single structured
output on the existing `Classification` record:

```
category  severity  confidence  title  description  location
missingInformation  containsThirdPartyPersonalData          // existing
priority (1-5)  affectedPeople[]  reason                    // from the assessor
```

Drop the 6-value `DEPARTMENTS` enum entirely — `agencyId` from `agencies.yml` already names
the responsible body, and it is the more precise of the two. Keep the 6 coarse categories
only if the officer portal wants them as a filter grouping, derived from the 17.

**2. Two status axes, both first-class.** Keep `ReportStatus` (7 values) for the *handoff*
and add a `WorkStatus` (`open`/`received`/`in_progress`/`resolved`) for the *work*, with a
`report_status_events` table. They are genuinely different things and collapsing them will
lose information the officer portal depends on.

---

## Sequence

Each phase ends with a system that runs and demos. Nothing is deleted until its
replacement passes the same tests.

### Phase 0 — A safety net, before moving any logic

The Java service has no `@WebMvcTest`, no MockMvc, no controller or validation coverage.
Porting business logic onto an untested web layer is how this goes wrong.

1. Add Flyway; convert the current implicit schema into `V1__baseline.sql`; turn
   `ddl-auto` off.
2. Port `test/api.test.ts` (and later `operations.test.ts`) to MockMvc **as specification**
   — the assertions describe behaviour that must survive the move, and most will fail
   until the matching phase lands. Keep them `@Disabled` and enable per phase.

### Phase 1 — Persistence parity

3. `report_status_events` + `WorkStatus`; persist the structured `LocationHint` fields
   (street, civic, landmark, municipio) instead of only the flattened `locationText`.
4. Operations columns: `assessment`, `ai_state`, `ai_error`, `assessment_model`,
   `assessed_at`, `priority_override`, `operations_updated_at`.
5. `PATCH /api/v1/reports/{id}` (work status) and `GET /api/v1/reports/{id}/progress`
   returning `{status, timeline}` and **never the content**.
6. Fix `FilesystemAttachmentStore`: its metadata lives in a `ConcurrentHashMap`, so after a
   restart every attachment id resolves empty and the photo is dropped silently. Move it
   into a table. This blocks anything photo-related.

### Phase 2 — Intake parity, behind the existing seam

7. `ImageSanitizer`, personal-data guard, request validation.
8. `ClaudeModerator` as a gate **before** persistence, failing closed (503, nothing
   stored) — matching the Node behaviour exactly.
9. `ClaudeAssistant` for the citizen reply, with the four canned fallbacks.
10. Widen `POST /api/v1/reports` to accept `image` (base64) and `language`, and to return
    `{report, reply, nextStep}` so it is response-compatible with the Node endpoint.

At this point Java can serve the citizen flow end to end. Flip `ROUTING_URL` thinking
around: point the Node app's own `POST /api/reports` at Java as a pass-through, so the two
run side by side and can be compared on real traffic before anything is cut over.

### Phase 3 — Triage

11. Merge the assessor prompt into `ClassifierPrompt`; one Claude call per report.
12. `@Scheduled` assessment worker with a DB claim and explicit retry, mirroring
    `worker.ts`'s durable semantics.
13. `/api/v1/operations/*`: config, list, `PATCH` overrides, `DELETE` with `confirm`,
    `POST retry`. Note Java's `GET /reports` currently returns every report with raw text
    and contact to anyone — scope these properly as they are written.

### Phase 4 — Transcription and the remaining edges

14. `GroqTranscriber` + `POST /api/v1/transcriptions` (raw audio body, 10 MB, 9 MIME types,
    the silence-hallucination filter).
15. Rate limiting (12/min reports, 30/min transcriptions), CSP, origin rejection,
    `Cache-Control: no-store` on `/api/**` — as one filter.

### Phase 5 — Move the UI, retire Node

16. Copy `public/` into `src/main/resources/static/`; serve `/officer` and `/comune` from
    the same app. The frontends call relative `/api/...`, so the only edits are the API
    path prefix (`/api` → `/api/v1`, or map both) and the vendored Leaflet path.
17. Delete `server.ts`, `src/`, `test/` from `impact-lab`; leave the README, docs and
    screencasts. Update both READMEs to describe one service.

### Phase 6 — MCP: keep, disable, label

18. Remove the `segnalami` entry from `.mcp.json` so nothing launches it.
19. Put a banner at the top of `mcp/README.md` and `mcp/docs/architecture.md`: this is a
    design study for a future Comune interface, it is not wired into the product, and its
    `comuneClient` was never implemented — the code talks to SegnalaMi's own API.
20. Exclude `mcp/` from CI and from the build.
21. Delete `mcp/frontend/` (a 180-line mockup duplicating citizen intake, whose SPID and
    CIE endpoints already return 501) and `mcp/dist/` (committed build output that will
    drift). The `mcp/contracts/*.json` files are the valuable part — they are the clearest
    written specification of drafts, idempotency, a status timeline and an error taxonomy,
    and they should guide the eventual API, so keep them.

> Rather than literally commenting out seven source files, this disables the server and
> labels it. Commented-out code rots invisibly; a wired-off module with a banner does not,
> and `git` still has the history either way. Say if you want the literal version.

---

## Risks worth pricing before starting

- **sharp has no equivalent in Java.** The current pipeline is rotate-by-EXIF → resize 1600
  inside → flatten on white → JPEG q82 mozjpeg, and the re-encode is what strips EXIF/GPS.
  Thumbnailator or Scrimage get close; mozjpeg's exact output does not exist. Also
  `ReportController` currently accepts `image/heic`, which `ImageIO` cannot decode without
  a native plugin — either add one or drop HEIC from the allowlist honestly.
- **No web tests today.** Phase 0 is not optional padding; it is the only thing that makes
  the rest verifiable.
- **Dev ergonomics.** `node:sqlite` with WAL and a file DB is a zero-setup local story;
  H2-file plus Flyway is fine but slower to iterate, and the Node side currently runs
  TypeScript with no build step at all.
- **Supabase image storage** would need a REST client in Java, or dropping that driver.
- **Scope honesty.** `docs/architecture.md` rules out SPID automation, PULIamo's private
  endpoints, headless ATM submission and auto-dialling staffed lines. Absorbing the citizen
  app must not quietly re-open any of them, and the email dry-run plus recipient allowlist
  stay on.

## If this turns out too long

The cheapest point to stop and still be coherent is **after Phase 3**: one database, one
report lifecycle, one Claude classification, one taxonomy, with Node reduced to serving
static files and proxying. That removes every real duplication. Phases 4–5 are
consolidation of plumbing, not of meaning.
