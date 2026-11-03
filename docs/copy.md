# Copy guide

How the app talks. Every string lives in `values/strings.xml` (English, default) and `values-pt-rBR/strings.xml`
(Portuguese, Brazil); `TranslationsTest` keeps the keys and format arguments in sync. Write both at the same time, in
the voice a native speaker would use.

## Voice

- **Direct and useful.** Give each line one job: name a status, give an instruction, or provide context the user needs
  to decide or act. Keep only details that help with that job.
- **Essential.** Use text only when it changes what the user understands or needs to do. Omit default subtitles,
  explanations of obvious controls, repeated confirmations, and internal process details. Keep instructions needed for
  the current task, actionable errors, and consequential confirmations; put technical how and why in docs.
- **Calm.** The user may be holding a kettle or portafilter. Use short sentences and one idea at a time.
- **Warm.** Onboarding can open with a welcoming, benefit-led headline. Keep routine statuses and instructions
  understated; use playfulness only when it sounds natural.
- **Honest about data.** Say where brews are stored or whether they are synced when that affects the user's choice or
  understanding (`Only on this phone`, `Server set. Nothing synced yet`). Distinguish saved, sent and synced data.
- **Plain.** Use familiar words, with coffee terms used at the bar and Android's own permission names where relevant.
  Keep technical details in the UI only when they help the user act.

## Mechanics

| Rule | EN | pt-BR |
| --- | --- | --- |
| Capitalization | Sentence case everywhere, including titles and buttons | Same |
| Address | Use "you"; use the imperative for instructions | Use "você"; use the imperative (`Ligue`, `Segure`) |
| Ellipsis | Use `…` (one character) for ongoing states: `Connecting…` | `Conectando…` |
| Final period | Full sentences end with a period; titles, labels and buttons do not | Same |
| Numbers and units | Formatting comes from code; units are `g` and `g/s` | Same; the locale supplies the decimal comma |
| Ratio | `1:%1$s` | Same |
| Contractions | Use natural contractions (`won't`, `Couldn't`) | n/a |
| App name | `Caparaó`, never translated | Same |
| Scale | Say "the scale" in instructions and "the Dot" when the model matters, such as pairing or onboarding | Say "a balança" and "a Dot" (feminine) |

## Terminology

Use these terms consistently. Add a term here when the app needs one.

| Concept | EN | pt-BR | Notes |
| --- | --- | --- | --- |
| One recorded extraction | brew | preparo | Use in lists; use "shot" only in espresso instructions |
| Brewing method: filter | Filter | Filtrado | |
| Brewing method: espresso | Espresso | Espresso | |
| Brewing method: French press | French press | Prensa francesa | |
| Mode selector, short labels | Manual · Filter · Espresso · Press | Manual · Filtrado · Espresso · Prensa | One line under the icon in Home, Recipes and the editor; full method names remain available to accessibility. |
| Home selector, manual scale accessible name | Manual scale | Balança manual | Manual stays the scale screen, not a brew mode. |
| French press steeping stage | Steep · Let it steep | Infusão · Deixe em infusão | No press instruction before the deadline: remove first, then press and serve. |
| Ground coffee weight | dose | dose | |
| Liquid out (espresso) | yield | bebida | |
| Water poured (filter) | water | água | |
| Weekly widget estimate | estimated beverage and caffeine | estimativas da bebida preparada e da cafeína | Approximate brewed amounts; never imply consumption or health advice |
| Coffee:water | ratio | razão | Shown as `1:16` |
| Weight per second | flow | fluxo | `g/s` |
| Zero the scale | tare | tara (noun), tarar (verb) | |
| Live espresso state | Extracting | Extraindo | |
| Live filter state | Brewing | Passando | |
| First liquid in the cup | first drop | primeira gota | |
| Bluetooth link | connect / pair | conectar / parear | Bluetooth lets the app communicate with the scale; the Dot pairs with one phone at a time |
| Remote copy | send / sync | enviar / sincronizar | Use "send" for an upload action and "sync" for a timestamp or sync status |
| Optional server | server | servidor | Call it optional when first introduced |
| Saved way to brew | recipe | receita | |
| Brew without a recipe | free brew | preparo livre | |
| Part of a filter recipe | step | etapa | |
| First small pour that wets the bed | bloom | bloom | |
| Pouring water to a target | pour | despejo | |
| Pause between pours | wait | espera | |
| Stirring the slurry by moving the dripper | swirl | agitar | |
| Grinder setting | grind | moagem | Free text; grinders have no common scale |
| The beans used | coffee | café | Free text; suggest the last one used |
| How it tasted, 1 to 5 | rating | nota | Stars |
| Free text about a brew | notes | anotações | Use "notas" for a recipe's notes |

## Patterns

### Buttons

- Use one or two words and name an action: `Tare`, `Save`, `Retry`, `Use weight`, `Test connection`.
- Name the outcome (`Turn on Bluetooth`) when the button opens a setting or performs that action.
- Name destructive actions directly (`Delete`, `Discard`, `Remove server`) and pair them with `Cancel`.
- pt-BR uses the infinitive: `Salvar`, `Descartar`, `Ligar Bluetooth`.

### Errors

State the problem briefly. Add an instruction when the user can resolve it, and make the action specific. Where an error
has a title and hint, give each its own string:

1. **Title:** name the problem, without blame or a period (`Scale not found` / `Balança não encontrada`).
2. **Hint:** give the action, ending with that action (`Turn the scale on and bring it closer to the phone.`).

Use a short status for background failures that need no action (`Couldn't reach the server.`). Mention background
retry behavior only where it helps; keep each failure message focused on the problem. Keep exception text, status codes
and addresses in the debug log.

### Empty states

Use a short, useful title. Make a subtitle add distinct context or a next step. Keep the tone natural, with playfulness
only when it fits. For example: `You haven't made coffee yet` / `Você ainda não fez café`.

### Progress and status

- Ongoing: use a present participle with `…` (`Looking for the scale…`, `Taring…`).
- Finished: use a short past participle (`Saved`, `Scale connected`, `Log copied`).
- Brew guidance focuses on the current step. Explain an automatic response when it helps the user understand that
  step, and wait to introduce later actions until they are relevant. For example: `Put the cup on the scale. The scale
  tares once the cup is steady.`

### Weekly widget

Prefix estimated beverage volume and caffeine values with `≈`. Use their labels (`Beverage`, `Caffeine`) without an
extra subtitle explaining that they are estimates. Dose is recorded, so show it without `≈`. Describe beverage as
brewed and caffeine as estimated caffeine in the brew, never as amounts consumed.

### Confirmations

Put the question in the title (`Delete this brew?`) and use `Delete` and `Cancel` for the actions. Add a line only when
it explains an effect that the title does not already make clear.

### Settings and permissions

Explain why the app needs access before asking. Say that Bluetooth lets the app communicate with the scale. Use the
permission name Android shows (`Nearby devices` / `Dispositivos por perto`); use the same approach for local network
access. Keep permission text specific about what the app does with that access.

## Checklist for a new string

- [ ] Add the key to both language files under the same comment.
- [ ] Use terminology from the table above.
- [ ] Use sentence case, periods only on full sentences and `…` for ongoing states.
- [ ] Keep titles, labels and buttons concise; let body and onboarding text wrap naturally without clipping. Check both
  languages on a small phone (pt-BR runs about 20% longer).
- [ ] Keep device addresses, device names and real server details out of public examples; use `coffee.example.com`.
