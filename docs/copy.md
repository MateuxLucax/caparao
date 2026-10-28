# Copy guide

How the app talks. Every string lives in `values/strings.xml` (English, default) and `values-pt-rBR/strings.xml`
(Portuguese, Brazil); `TranslationsTest` keeps the keys in sync. Write both at the same time, and write each one as a
native speaker would say it, not as a translation of the other.

## Voice

- **Direct.** Say what is happening or what to do. No greetings, no "Oops!", no exclamation marks.
- **Calm.** The user is holding a kettle or a portafilter. Short sentences, one idea each.
- **Honest about data.** Say where brews live ("Only on this phone", "Server set. Nothing sent yet.") and what the app
  will do next ("The app tries again later.").
- **Plain.** No marketing, no emoji, no jargon the user doesn't already use at the bar. Coffee terms are fine; tech
  terms only when Android uses them too (Bluetooth, Nearby devices, URL, token).

## Mechanics

| Rule | EN | pt-BR |
| --- | --- | --- |
| Capitalization | Sentence case everywhere, titles and buttons included | Same |
| Address | "you", imperative for instructions | "você", imperative (`Ligue`, `Segure`) |
| Ellipsis | `…` (one character) for ongoing states: `Connecting…` | `Conectando…` |
| Final period | Full sentences end with a period; titles, labels and buttons don't | Same |
| Numbers and units | Formatting comes from code, never hardcoded in a string; units are `g` and `g/s` | Same; the locale gives the decimal comma |
| Ratio | `1:%1$s` | Same |
| Contractions | Allowed when spoken (`won't`, `Couldn't`) | n/a |
| App name | `Caparaó`, never translated | Same |
| Scale | "the scale" in instructions, "the Dot" when the model matters (pairing, onboarding) | "a balança", "a Dot" (feminine) |

## Terminology

Use these and only these. Adding a term means adding it here.

| Concept | EN | pt-BR | Notes |
| --- | --- | --- | --- |
| One recorded extraction | brew | preparo | Not "shot" in lists; "shot" only in espresso instructions |
| Brewing method: filter | Filter | Filtrado | |
| Brewing method: espresso | Espresso | Espresso | Not "expresso" |
| Ground coffee weight | dose | dose | |
| Liquid out (espresso) | yield | bebida | |
| Water poured (filter) | water | água | |
| Coffee:water | ratio | razão | Shown as `1:16` |
| Weight per second | flow | fluxo | `g/s` |
| Zero the scale | tare | tara (noun), tarar (verb) | |
| Live espresso state | Extracting | Extraindo | |
| Live filter state | Brewing | Passando | |
| First liquid in the cup | first drop | primeira gota | |
| Bluetooth link | connect / pair | conectar / parear | Pair only for the Dot's one-phone pairing |
| Remote copy | send / sync | enviar / sincronizar | "send" for actions, "sync" for timestamps |
| Optional server | server | servidor | Always "Optional" when introduced |

Known gap: the brews tab is `Brews` in EN and `Dados` in pt-BR. Pick one meaning (list of brews) and align both
(`Preparos`) when the tab is next touched.

## Patterns

### Buttons

- One or two words, a verb: `Tare`, `Save`, `Retry`, `Use weight`, `Test connection`.
- Name the outcome, not the mechanism: `Turn on Bluetooth`, not `Open settings` when we open the Bluetooth toggle.
- Destructive actions say the thing: `Delete`, `Discard`, `Remove server`. Pair them with `Cancel`.
- pt-BR uses the infinitive: `Salvar`, `Descartar`, `Ligar Bluetooth`.

### Errors

Two parts, each its own string:

1. **Title**: what is wrong, no blame, no period. `Scale not found` / `Balança não encontrada`.
2. **Hint**: what to do, in order, ending with the action. `Turn the scale on and bring it closer to the phone.`

Background failures say what the app does next: `Couldn't reach the server. The app tries again later.` Never show
exception text, status codes or addresses; the debug log is where those go.

### Empty states

Title says what is missing, text says how it fills up:
`No brews yet` / `Brews you record show up here, split into filter and espresso.`

### Progress and status

- Ongoing: present participle with `…` (`Looking for the scale…`, `Taring…`).
- Finished: past participle, short (`Saved`, `Scale connected`, `Log copied`).
- Brew guidance tells the user the next physical step, then what the app does on its own:
  `Put the cup on the scale` / `It tares by itself once the cup is still.`

### Confirmations

A question in the title (`Delete this brew?`), buttons `Delete` and `Cancel`. Add a line only when something can't be
undone that the title doesn't already say.

### Settings and permissions

Say why before asking: `Bluetooth is only used to find the scale. Android calls it Nearby devices.` Use Android's
own names for its settings (Nearby devices / Dispositivos por perto).

## Checklist for a new string

- [ ] Key added to both files, grouped under the same comment.
- [ ] Terms from the table above.
- [ ] Sentence case, period only on full sentences, `…` for ongoing states.
- [ ] Fits on one line on a small phone in both languages (pt-BR runs ~20% longer).
- [ ] No scale address, device name or real server in examples (`coffee.example.com`).
