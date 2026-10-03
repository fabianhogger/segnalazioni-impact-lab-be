# Proposta operativa al Comune di Milano e agli enti erogatori

**Oggetto:** instradamento automatico delle segnalazioni dei cittadini e richiesta di
un canale di conferimento machine-to-machine.

**A chi è rivolto questo documento:** Direzione Transizione Digitale del Comune di
Milano; direzioni competenti per le sette categorie del centro segnalazioni; Amsa,
ATM, Unareti; Polizia Locale; ARPA Lombardia.

---

## 1. In una pagina

Esiste un servizio funzionante che riceve il testo libero di un cittadino, ne
riconosce la categoria, ne estrae luogo e descrizione, e individua l'ente competente.
È già in grado di consegnare una segnalazione via e-mail e di preparare, per tutti gli
altri canali, un pacchetto pronto all'invio che il cittadino trasmette con un tocco.

Quello che il servizio **non** fa, e non farà, è agire al posto del cittadino sui
canali esistenti: non automatizza sessioni SPID, non usa endpoint non documentati di
app di terzi, non compila moduli web per conto di altri, non telefona ai numeri
presidiati da operatori. Sono scelte deliberate, non limiti tecnici.

Ne consegue un unico punto di attenzione per l'amministrazione: **oggi l'ultimo metro
della consegna è a carico del cittadino**, perché non esiste un'interfaccia applicativa
a cui conferire una segnalazione. Questo documento chiede di crearla, e nel frattempo
propone tre passi intermedi che non richiedono alcuno sviluppo.

---

## 2. Che cosa chiediamo, in ordine di costo crescente

### Passo 0 — Verifica dei recapiti (costo: nullo, tempo: giorni)

Il servizio instrada oggi su una tabella di competenze costruita da fonti pubbliche.
Alcune righe non sono confermate. Chiediamo una validazione puntuale:

| Ente | Recapito usato | Stato | Cosa chiediamo |
|---|---|---|---|
| Comune — centro segnalazioni | modulo online, SPID/CIE | confermato | conferma delle sette categorie e dei casi in cui la foto è obbligatoria |
| Comune — 020202 | 020202, telefono e WhatsApp | parziale | **se la linea apra o no una pratica formale**, o sia solo informativa |
| Comune — SOS Affitti | SOSaffitti.segnalazioni@comune.milano.it | 2023 | se la casella sia ancora presidiata e chi la legge |
| Comune — unità di strada | 02 8844 7646 | non confermato | validità, orari, periodo di attivazione |
| Polizia Locale | 020208 | non confermato | validità per le non urgenze |
| Amsa | 800 33 22 99, app PULIamo | confermato | se preferite ricevere via telefono, app o altro |
| Unareti | 803500 | non confermato | validità |
| ATM | modulo online, 02 48 607 607 | confermato | conferma dei 10 giorni dichiarati |
| ARPA Lombardia | tramite Comune per il rumore | parziale | **recapito della sala operativa** e criterio di attivazione |

Le due righe in grassetto sono quelle che oggi incidono di più sulla qualità
dell'instradamento.

### Passo 1 — Una casella di posta dedicata (costo: molto basso, tempo: settimane)

Prima di qualunque API, il modo più rapido per chiudere l'ultimo metro è una casella
— ordinaria o PEC — che accetti segnalazioni generate da sistemi terzi, con un
oggetto strutturato e un identificativo nostro.

Serve, per ciascun ente:

- un indirizzo dedicato, distinto dalle caselle al pubblico;
- l'indicazione di chi lo presidia e con quale frequenza;
- la conferma che un allegato fotografico sia accettato;
- l'autorizzazione esplicita a ricevere messaggi generati automaticamente.

Il servizio è già pronto per questo canale ed è l'unico che oggi sa trasmettere da
solo. L'invio reale è disattivato per impostazione predefinita e ogni destinatario
deve essere inserito in una lista esplicita: nessuna casella pubblica può essere
raggiunta per errore o per sbaglio di configurazione.

### Passo 2 — Un endpoint di conferimento (costo: medio, tempo: mesi)

È la richiesta centrale. Un singolo endpoint HTTP che accetti una segnalazione già
strutturata e restituisca un numero di pratica.

```
POST /segnalazioni
Authorization: Bearer <credenziale di servizio>
Content-Type: application/json

{
  "idEsterno": "317a25b0-0905-4468-be76-34a2b370f8a3",
  "categoria": "RIFIUTI",
  "titolo": "Discarica abusiva di ingombranti",
  "descrizione": "Testo neutro e verificabile, in italiano.",
  "testoOriginale": "Le parole esatte del cittadino.",
  "luogo": {
    "via": "Via Paolo Sarpi",
    "civico": "12",
    "municipio": 1,
    "lat": 45.4788,
    "lon": 9.1786
  },
  "segnalante": { "contatto": "cittadino@example.org", "anonimo": false },
  "allegati": [ { "nome": "foto.jpg", "tipo": "image/jpeg", "url": "https://…" } ],
  "inviatoIl": "2026-10-03T14:33:00+02:00"
}

→ 201 Created
{ "numeroPratica": "SEG-2026-114502", "statoUrl": "https://…/pratiche/SEG-2026-114502" }
```

Tre requisiti qualificanti, in ordine di importanza:

1. **Un numero di pratica restituito in modo sincrono.** È ciò che trasforma una
   segnalazione in qualcosa di cui il cittadino può chiedere conto. Senza, il servizio
   può solo dire "inviato", che è esattamente il livello di servizio odierno.
2. **Un modo per leggere lo stato della pratica,** anche solo aperta / in lavorazione /
   chiusa. Il valore per il cittadino è quasi tutto qui.
3. **Un rifiuto esplicito per incompetenza,** con l'indicazione dell'ente corretto.
   Serve a noi per correggere l'instradamento e a voi per non ricevere due volte le
   stesse segnalazioni sbagliate.

Note tecniche che semplificano la vita a entrambi: `idEsterno` rende la chiamata
idempotente, così un nostro tentativo ripetuto non genera doppioni; una credenziale di
servizio (non SPID) perché l'identità in gioco è quella del sistema, mentre quella del
cittadino viaggia nel campo `segnalante`.

### Passo 3 — Identità del cittadino, se e quando serve (costo: alto)

Se l'amministrazione ritiene che una segnalazione debba essere imputabile a una persona
identificata, la strada corretta **non** è delegare SPID a un terzo, ma un flusso di
autorizzazione in cui è il cittadino a concedere al servizio un permesso limitato e
revocabile. È un intervento significativo e lo indichiamo come ultimo per questo: i
primi tre passi producono quasi tutto il beneficio senza toccare l'identità digitale.

---

## 3. Che cosa offriamo in cambio

Non chiediamo soltanto. Il servizio produce dati che l'amministrazione oggi non ha:

- **Segnalazioni mal indirizzate, misurate.** Ogni ente può rispondere indicando la
  competenza corretta; quelle correzioni dicono con precisione dove la tabella delle
  competenze pubblica è ambigua per i cittadini.
- **Che cosa i cittadini provano a segnalare e non trovano dove.** Le categorie di
  ripiego sono una mappa dei buchi nel catalogo dei servizi.
- **Dove la segnalazione si interrompe.** Il servizio registra quante segnalazioni
  restano in attesa di un'azione del cittadino e non vengono mai completate: è la
  misura diretta del costo dell'attrito dei canali attuali.
- **Segnalazioni già normalizzate:** categoria, luogo strutturato, descrizione neutra.
  Meno lavoro di triage a valle.

La tabella delle competenze è un file di configurazione leggibile e correggibile da
chi non scrive codice. Se ci mandate una correzione, entra in servizio senza sviluppo.

---

## 4. Questioni che spettano all'amministrazione, non a noi

Le segnaliamo perché vanno decise prima di un esercizio reale, non dopo.

1. **Titolarità del trattamento.** Nel momento in cui il servizio inoltra dati
   personali a un ente pubblico, chi è titolare e chi responsabile? Serve l'inquadramento
   corretto prima del primo invio reale.
2. **Foto dello spazio pubblico.** Contengono regolarmente volti e targhe. Vanno
   oscurati? Da chi? Per quanto tempo si conservano? Il servizio può applicare una
   minimizzazione, ma la regola la stabilite voi.
3. **Dati di terzi nel testo libero.** I cittadini scrivono nomi, numeri di interno,
   a volte dati sanitari. Il servizio segnala quando questo accade; cosa farne —
   bloccare, oscurare, inoltrare — è una decisione vostra.
4. **Segnalazioni anonime.** Accettate o no, e per quali categorie?
5. **Che cosa è una risposta.** Se non esiste un numero di pratica, il cittadino non
   può distinguere una segnalazione lavorata da una ignorata. Questo è il punto che
   determina se il servizio crea fiducia o la consuma.
6. **Il confine dell'emergenza.** Oggi il servizio, davanti a qualunque indizio di
   pericolo immediato, si ferma e indirizza al 112 senza inoltrare nulla. Chiediamo
   conferma che sia il comportamento che desiderate.

---

## 5. Proposta di sperimentazione

Una sola categoria, un solo ente, tre mesi, nessuno sviluppo richiesto all'ente.

- **Categoria:** rifiuti abbandonati e ingombranti. È la più frequente, la più
  verificabile sul campo e la meno ambigua da classificare.
- **Canale:** casella dedicata (Passo 1).
- **Volume:** limitato e concordato.
- **Misure:** quota di segnalazioni instradate all'ente corretto; quota rifiutata per
  incompetenza; quota chiusa come non lavorabile per dati insufficienti; tempo fra
  invio e prima lavorazione.

L'esito di questi tre mesi è l'unico argomento serio a favore o contro il Passo 2. Se
la quota di instradamenti corretti non è alta, il problema è nostro e lo diciamo.

---

## 6. Prossimi passi concreti

| # | Azione | A chi | Esito atteso |
|---|---|---|---|
| 1 | Invio di questo documento e richiesta di un incontro tecnico | Direzione Transizione Digitale | un referente tecnico individuato |
| 2 | Validazione della tabella dei recapiti (§2, Passo 0) | direzioni competenti | tabella confermata |
| 3 | Richiesta di casella dedicata per la categoria rifiuti | Amsa + Comune | un indirizzo e un presidio |
| 4 | Inquadramento privacy (§4.1–4.3) | DPO del Comune | ruoli definiti, regola sulle foto |
| 5 | Avvio della sperimentazione a volume limitato | Amsa | tre mesi di dati |
| 6 | Presentazione dei risultati e decisione sul Passo 2 | tutti | sì/no motivato sull'API |
| 7 | Contatti paralleli con ATM, Unareti, ARPA | — | stesso percorso, partendo dal Passo 0 |

I passi 1 e 2 non costano nulla all'amministrazione e sbloccano la maggior parte del
valore immediato. Il passo 3 è quello che rende il servizio utile davvero.

---

*Documento tecnico di accompagnamento al servizio. Il codice, la tabella delle
competenze e i limiti dichiarati sono ispezionabili: `docs/architecture.md` descrive
in particolare che cosa il servizio si rifiuta di fare e perché.*
