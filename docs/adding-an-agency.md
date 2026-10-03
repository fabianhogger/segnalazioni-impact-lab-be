# Adding or changing a body

## A body that already exists, reached a new way

Edit `src/main/resources/agencies.yml`. Nothing else. The service validates the table at
startup and refuses to boot if two bodies claim the same category.

```yaml
- id: amsa
  display-name: "Amsa"
  channel: PHONE          # ← becomes API when they publish one
  target: "800332299"     # ← becomes the endpoint URL
```

## A body that has opened an API

This is the case the repository was shaped around. Four steps.

**1. Write the adapter.** One class, in `adapter/out/agency`:

```java
@Component
public class AmsaApiAdapter implements AgencyAdapter {

    @Override
    public boolean supports(DispatchChannel channel) {
        return channel == DispatchChannel.API;   // or a more specific check
    }

    @Override
    public DispatchOutcome dispatch(Report report, Classification classification,
                                    Agency agency, List<Attachment> attachments) {
        // POST to agency.target(), carry the ticket id back as the reference
        return new DispatchOutcome(ReportStatus.SENT, agency.id(), DispatchChannel.API,
                ticketId, "Segnalazione registrata con numero " + ticketId,
                CitizenAction.NONE, null, null,
                attachments.stream().map(Attachment::id).toList(),
                Map.of());
    }
}
```

`ApiAgencyAdapter` is the placeholder that currently occupies `DispatchChannel.API`.
Delete it, or narrow its `supports` so the two do not both match.

**2. Flip the channel** in `agencies.yml`, and move the endpoint into `target`.
Credentials go in the environment, never in the YAML.

**3. Return a real reference.** The `reference` field is the citizen's proof that
something happened — a ticket number, not a UUID we invented. If the body does not
return one, say so in `citizenMessage` rather than fabricating one.

**4. Add a routing test.** `RoutingPipelineTest` stubs the classifier and asserts on
agency, channel and action. Copy one of the existing cases.

## A new category

1. Add it to `Category`.
2. Add it to exactly one body in `agencies.yml` — the startup check enforces this.
3. Describe it in `ClassifierPrompt.SYSTEM`. **This is the step that is easy to forget
   and that silently degrades routing:** a category the prompt never mentions is a
   category the model will not choose, and reports will scatter into the nearest
   plausible neighbour instead.
4. `every_category_except_emergency_resolves_to_a_reachable_agency` will fail until
   step 2 is done.

## Changing the prompt

`ClassifierPrompt.SYSTEM` is the highest-leverage file in the repository: routing
quality depends far more on it than on any Java. Treat an edit like a schema change.

There is no eval in the repository yet, which is the largest gap in it. Before the
prompt is tuned against intuition, build one: fifty real reports with the correct body
labelled by someone who knows the competences, then measure. Without that, every prompt
change is a guess that feels like an improvement.
