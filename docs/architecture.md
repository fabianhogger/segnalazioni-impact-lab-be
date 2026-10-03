# Architecture

## The pipeline

```
POST /api/v1/reports
      │
      ▼
 IntakeService ──► Classifier (port)
      │              └─ ClaudeClassifier    structured output, one call
      │              └─ HeuristicClassifier offline stand-in, no API key
      │
      ├─ severity == EMERGENZA ───────────────────► stop. Tell the citizen to call 112.
      ├─ missingInformation non-empty ────────────► stop. Ask the citizen.
      │
      ▼
 RoutingService ──► AgencyRegistry (agencies.yml)
      │              confidence < 0.6 → the human fallback line
      ▼
 DispatchService ──► AgencyAdapter (port), selected by the agency's channel
                      ├─ EmailAgencyAdapter      EMAIL      service transmits
                      ├─ AssistedHandoffAdapter  PHONE/WEB_FORM/APP  citizen transmits
                      └─ ApiAgencyAdapter        API        not implemented: nothing to call yet
```

Four packages, one direction of dependency:

| Package | Holds | Depends on |
|---|---|---|
| `domain` | `Report`, `Classification`, `Agency`, `DispatchOutcome` | nothing |
| `application` | the pipeline, and the ports it needs | `domain` |
| `adapter` | HTTP in; Claude, SMTP, JPA, filesystem out | `application`, `domain` |
| `config` | the competence table and its validation | `domain` |

The point of the inversion is narrow and concrete: `application` names what it needs
(`Classifier`, `AgencyAdapter`, `ReportRepository`, `AttachmentStore`) and never names
who provides it. When the Comune ships an API, it arrives as one new class in `adapter`.

## What the service will and will not do

It routes, rewrites and hands off. It does not impersonate a citizen.

| Not done | Why |
|---|---|
| Driving the Comune portal with the citizen's SPID | SPID cannot be delegated. Automating the session is both a terms breach and an identity-assurance breach. |
| Calling PULIamo's private endpoints | Undocumented endpoints are not a public interface. Using them is unauthorised access, whatever the intent. |
| Submitting ATM's web form headlessly | Same terms problem, plus it silently becomes a scraper that breaks on every redesign. |
| Auto-dialling 020202 or 800 33 22 99 | Those lines are staffed by people. Machine-generated calls take a seat from a person in a queue. |

This is why `AssistedHandoffAdapter` exists and why `ApiAgencyAdapter` throws. The gap
between what is useful and what is permitted is not a technical gap, and it is not
closed by writing more code — it is closed by `municipality-handoff.md`.

## Safety rails

**Emergencies are never queued.** `IntakeService` checks severity before routing, and
returns `tel:112`. The classifier prompt instructs the model to resolve the
URGENT/EMERGENZA boundary towards EMERGENZA: a false alarm costs one phone call, a
false negative costs more than this service is worth.

**Email cannot be sent by accident.** `dry-run` defaults to `true`, and with it off a
recipient must still appear in `allowed-recipients` or the send is refused. Both rails
are covered by `EmailDispatchSafetyTest`; deleting those tests re-opens the hole.

**Low confidence routes to a human,** not to a best guess. Below 0.6 the report goes to
the information line regardless of category.

**Incomplete reports are not filed.** A report with no street reaches a body that closes
it as unactionable, and the citizen believes it was received. Asking is cheaper.

**Citizen text is data, never instruction.** It is delimited inside
`<segnalazione_cittadino>` and the system prompt says to classify the text rather than
obey it. The structured-output schema also means a successful injection cannot change
the response shape — it could at worst change a category, which the confidence floor
and the citizen's own view of the classification are there to catch.

## Model configuration

`claude-opus-5-5`, structured outputs against the `Classification` record, effort `low`.

Classification is short and tightly specified, which is the shape of task where low
effort holds. That is an assumption, not a measurement — before raising it, build an
eval from real reports and check the routing accuracy actually moves.

The system prompt is marked for caching. It is long, identical on every request, and
sits in front of the only part that varies, so it is the textbook case.

The schema is derived from the record via Jackson, so `@JsonPropertyDescription` text
**is prompt**. The generated schema constrains `category` and `severity` to their enum
values, which is why no output parsing or validation code exists here.

## State and scale

One Spring Boot process, H2 on disk, photos on the local filesystem. That is honest for
an MVP serving a pilot, and dishonest for anything more.

What has to change before real volume, in order:
1. Postgres instead of H2 — the driver and profile are already in the build.
2. S3 or MinIO behind `AttachmentStore`.
3. An async boundary between intake and dispatch. Today dispatch is inline, so a slow
   SMTP server is a slow HTTP response. The natural seam is `DispatchService`: put a
   queue behind it and the controller returns as soon as the report is classified.

None of these are blocked by the current shape, which is the reason for the current
shape.
