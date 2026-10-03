# segnalazioni-ai

Routes a citizen's free-text report about Milan to the body actually responsible for it.

Receive text (and photos) → classify and extract with Claude → pick the competent body →
either transmit it, or hand the citizen a ready-to-send packet.

> ### 📄 [docs/municipality-handoff.md](docs/municipality-handoff.md) — the proposal to the Comune
>
> This service can route a report correctly but, for almost every body, cannot deliver
> it: no public body in Milan publishes a reporting API, and this project will not
> automate SPID sessions, private app endpoints or web forms to fake one. That gap is
> not closed by writing more code.
>
> The handoff document is what closes it. Written in Italian, addressed to the Comune's
> digital transformation office and to each agency, it asks for four things in order of
> cost: **verify the recipient table** (free), **open a dedicated mailbox** (very cheap),
> **publish a `POST /segnalazioni` endpoint that returns a real case number** (the actual
> goal), and **citizen identity last**. It carries a concrete JSON contract, the
> governance questions that are the administration's to answer, and a three-month
> single-category pilot. Hand that file over as-is.

## Who it can reach

Nine bodies, covering 17 categories. The competence table lives in
[`agencies.yml`](src/main/resources/agencies.yml) — configuration, not code.

**Delivered by the service today.** One body, one channel:

| Body | Covers | Channel |
|---|---|---|
| Comune — SOS Affitti | irregular rentals | **e-mail** — verified end to end over SMTP |

**Routed today, delivered by the citizen.** The service picks the body, writes the
report in the form that body expects, and returns a one-tap action (`tel:` link or form
URL) plus pre-filled text. The citizen presses send:

| Body | Covers | Channel |
|---|---|---|
| Comune — online reporting centre | roads, street cleaning, urban furniture, public green, cemeteries, abandoned vehicles, street lighting | web form (SPID/CIE) |
| Amsa | waste, overflowing bins, illegal dumps, bulky items, syringes | phone 800 33 22 99 / PULIamo app |
| ATM | metro, tram, bus, BikeMi | web form + infoline |
| Unareti | electrical distribution faults, blackouts | phone 803500 |
| Polizia Locale | non-urgent policing, illegal parking, degradation | phone 020208 |
| ARPA Lombardia (via the Comune) | noise, pollution, spills, odours | phone, via the Comune |
| Comune — outreach unit | people sleeping rough | phone 02 8844 7646 |
| Comune — 020202 | anything uncategorised, and every low-confidence report | phone / WhatsApp |

**Eventually, all of them directly.** Every body above is a candidate for machine
delivery the moment it offers a mailbox or an endpoint — the adapter seam is already
there ([`AgencyAdapter`](src/main/java/it/milano/segnalazioni/application/port/AgencyAdapter.java),
one class per new channel). Several of the phone numbers above came from secondary
sources and are marked `verified: "no"` in `agencies.yml`; confirming them is step one
of the handoff document.

Emergencies are never routed anywhere: any sign of immediate danger short-circuits to
`tel:112` before a body is chosen.

## Build status

| | |
|---|---|
| Classification and extraction | working, Claude structured outputs |
| Routing over 17 categories and 9 bodies | working, driven by `agencies.yml` |
| E-mail / PEC delivery | working, verified end to end over SMTP |
| Assisted handoff for every other channel | working |
| API delivery | seam in place, nothing to call yet |
| Evals | **missing** — see Known gaps |

## Run it

```bash
docker compose up -d                 # Mailpit, catches all outbound mail
mvn spring-boot:run
```

Boots with no API key: a keyword stand-in classifier takes over so the pipeline is
demonstrable offline. It is not a product — it says so in the startup log.

With Claude:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

To exercise a real SMTP send without touching a real public mailbox:

```bash
SOS_AFFITTI_EMAIL=test@localhost mvn spring-boot:run -Dspring-boot.run.profiles=demo
# then read it at http://localhost:8025
```

## Try it

```bash
curl -XPOST localhost:8080/api/v1/reports -H 'Content-Type: application/json' -d '{
  "text": "Discarica abusiva di rifiuti ingombranti in Via Paolo Sarpi 12"}'
```

```json
{
  "status": "AWAITING_CITIZEN_ACTION",
  "analysis": { "category": "RIFIUTI", "location": "Via Paolo Sarpi 12" },
  "nextStep": {
    "agencyId": "amsa", "action": "CALL", "deeplink": "tel:800332299",
    "prefilledText": "Discarica abusiva di rifiuti ingombranti.\nLuogo: Via Paolo Sarpi 12.\n…"
  }
}
```

| Endpoint | |
|---|---|
| `POST /api/v1/reports` | submit a report |
| `POST /api/v1/attachments` | upload a photo, returns an id to reference |
| `GET /api/v1/reports/{id}` | one case |
| `GET /api/v1/agencies` | the competence table as loaded |

## Sending real mail

Two rails, both on by default:

- `segnalazioni.dispatch.email.dry-run: true` — nothing leaves the process.
- `allowed-recipients` — with dry-run off, anything else is refused, not sent.

Turning both off means mailing a real public body. Confirm with that body first; the
reasoning is in `docs/municipality-handoff.md` §2.

## Where things are

```
domain/         Report, Classification, Agency, DispatchOutcome
application/    the pipeline + the ports it depends on
adapter/in/     HTTP
adapter/out/    llm (Claude) · agency (email, assisted, API seam) · persistence · storage
config/         agencies.yml and its startup validation
```

`src/main/resources/agencies.yml` is the competence table. It is configuration, not
code, and it is the file to correct when the Comune tells us we got a competence wrong.

## Docs

- `docs/architecture.md` — the pipeline, the safety rails, and what the service refuses to do
- `docs/adding-an-agency.md` — adding a body, a channel, or a category
- `docs/municipality-handoff.md` — the proposal for the Comune and the agencies (Italian)

## Known gaps

- **No eval.** The largest gap. Prompt changes are currently judged by intuition.
  `docs/adding-an-agency.md` describes what to build.
- Dispatch is inline, so a slow SMTP server is a slow HTTP response.
- H2 on disk and photos on the local filesystem — single instance only.
- No authentication on the API, and no rate limiting. Required before public exposure.
