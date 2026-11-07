# Lernen generell – Uni-Stoff lernen in Minecraft

Eine Minecraft-Mod (Minecraft 26.3, NeoForge, nur Client), mit der du beim Spielen deinen Uni-Stoff übst.
Du lädst am Anfang deine Übungsblätter, Altklausuren oder Skripte als **PDF** in die Welt hoch. Etwa **alle
5 Minuten** pausiert das Spiel, und eine KI (über **OpenRouter**) stellt dir eine Aufgabe daraus.

- **Falsch:** Ein zufälliger Gegenstand (Stapel) aus deinem Inventar wird gelöscht. Die Aufgabe bleibt offen.
- **Richtig:** Du bekommst einen **Booster** (z. B. 5 Extra-Herzen, 3 Minuten Feuerresistenz). Booster liegen in
  einem **eigenen Booster-Inventar** und wirken erst, wenn du sie einsetzt.
- **Die Aufgabe muss gelöst werden.** Solange die Antwort falsch ist, geht das Spiel nicht weiter: das
  Aufgabenfenster lässt sich nicht schließen und das Spiel bleibt pausiert.

Die Mod läuft parallel zu [Chinese-Minecraft](https://github.com/nphjfvp/Chinese-Minecraft) („Learn or Die“):
eigene Mod-ID (`unilernen`), eigene Tasten, eigene Dateien. Ist gerade ein Fenster der anderen Mod offen (z. B.
deren Quiz), wartet die Aufgabe, bis es zu ist. Während eine Uni-Aufgabe offen ist, ist das Spiel pausiert, also
fragt auch die chinesische Lehrerin nichts.

## So geht's

1. **NeoForge für Minecraft 26.3** installieren und `lernen-generell-…+mc26.3.jar` in den `mods`-Ordner legen.
2. **OpenRouter-Key holen:** auf [openrouter.ai](https://openrouter.ai) anmelden → *Keys* → neuen Key erstellen
   und etwas Guthaben aufladen (die Aufgaben kosten meist Bruchteile eines Cents).
3. Im Spiel **O** drücken (Lernmenü) → **Einstellungen** → Key ins Feld *OpenRouter-Key* einfügen (Strg+V) →
   **Verbindung testen**. Die Einstellungen gibt es auch im Hauptmenü unter *Mods* → *Lernen generell* → *Config*.
4. Welt betreten, **O** → **Unterlagen (PDFs)** und die PDFs **ins Minecraft-Fenster ziehen** (oder den Pfad
   einfügen und *Hinzufügen*; ein Ordnerpfad nimmt alle PDFs darin). Jede PDF wird einmal von der KI gelesen; sie
   zerlegt sie in einzelne Aufgaben (Teilaufgaben a, b, c … einzeln) und die wichtigsten Inhalte. Das dauert je nach
   Größe ein paar Sekunden bis Minuten. Der Status steht in der Liste (✔ fertig, ⏳ wird gelesen, ✘ Fehler).
5. Weiterspielen. Oben rechts läuft der Countdown bis zur nächsten Aufgabe.

## Das Aufgabenfenster

- Aufgabe oben (mit dem Mausrad scrollen), Antwort unten eintippen, **Enter** oder *Abschicken*.
- Formeln einfach tippen, wie es geht: `x^2`, `sqrt(2)`, `pi`, `1/3`, `[[1,2],[3,4]]`. Die KI bewertet
  inhaltlich: andere Schreibweisen, umgeformte Terme, Bruch statt Dezimalzahl usw. zählen als richtig.
- Nach einer falschen Antwort sagt die KI, **was** nicht stimmt, ohne die Lösung zu verraten.
- **Tipp:** gibt einen Hinweis; mehrmals drücken für weitere Tipps.
- **Lösung zeigen:** erst nach 3 falschen Versuchen (einstellbar). Danach musst du das Ergebnis trotzdem selbst
  eintippen, um weiterzuspielen, und es gibt **keinen Booster**.
- **Pausemenü:** öffnet das normale Pausemenü (zum Beispiel zum Speichern und Verlassen). Gehst du zurück ins
  Spiel, ist die Aufgabe sofort wieder da. Auch nach dem Neustart der Welt ist die offene Aufgabe gleich wieder da.
- **Überspringen** erscheint nur, wenn die KI 3-mal hintereinander nicht erreichbar war (kein Internet, kein
  Guthaben …), damit du nicht festsitzt. Fehler der KI werden nie bestraft.

## Sprechübungen (3 Stufen)

Unter *Einstellungen → Sprechen …* schaltest du Sprechübungen ein (*manchmal* = etwa jede 4. Aufgabe, *oft* = jede 2.,
*nur Sprechen*) und wählst die Sprache (Chinesisch, Englisch, Deutsch, Spanisch, Französisch, Italienisch,
Portugiesisch, Japanisch). Mit *Jetzt eine Sprechübung* im Lernmenü bekommst du sofort eine. Die KI baut einen Satz
passend zu deinen Unterlagen (bei einem Sprachkurs aus dessen Vokabeln und Grammatik) und zerlegt ihn in Wörter,
mit Pinyin für Chinesisch.

Oben steht immer der **ganze Satz**, groß, mit der Aussprache darunter. Nach jeder Aufnahme wird jedes Wort
**grün** (richtig) oder **rot** (falsch). **Leertaste** startet die Aufnahme. Sobald du nach dem Sprechen kurz still
bist, wird sie von selbst ausgewertet (oder Leertaste noch einmal drücken).

1. **Langsam, Wort für Wort:** Das gelbe Wort sprechen. Ist es richtig, wird es grün und das nächste kommt von
   selbst. Ist es rot, sprichst du es noch einmal. Nach 3 Fehlversuchen kannst du ein Wort überspringen, falls die
   Erkennung hängt.
2. **Etwas schneller:** den ganzen Satz in einem Zug, ruhig, aber ohne lange Pausen (Chinesisch etwa 1 Silbe pro Sekunde).
3. **Normales Tempo:** den ganzen Satz flüssig (Chinesisch etwa 2,2 Silben pro Sekunde, andere Sprachen etwa 1,8 Wörter
   pro Sekunde, plus 1,5 s Reaktionszeit). Das Ziel in Sekunden steht im Fenster.

In Stufe 2 und 3 müssen mindestens 80 % der Wörter erkannt werden (einstellbar). Wie bei normalen Aufgaben geht
das Spiel erst weiter, wenn alle drei Stufen geschafft sind; danach gibt es einen Booster. Ein verpatzter Satz kostet
standardmäßig **keinen** Gegenstand, weil auch die Erkennung mal danebenliegt (*Strafe bei Sprechfehlern* schaltet das ein).
Chinesisch wird über die Pinyin-Silben ohne Töne verglichen. Falsch geratene, gleich klingende Schriftzeichen zählen
deshalb nicht als Fehler.

**Spracherkennung**, unter *Sprechen … → Erkennung*:

- **helfer**: das Sprach-Hilfsprogramm von [Chinese-Minecraft](https://github.com/nphjfvp/Chinese-Minecraft). Es
  läuft lokal und offline, nimmt selbst auf (Minecraft braucht keinen Mikrofonzugriff) und kann zh, en, de, es, pt und ja.
  Wenn Chinese-Minecraft installiert ist und dort die Sprachantworten eingerichtet sind, läuft es schon. Wechselt die
  Sprache, lädt der Helfer ein anderes Modell, und die chinesische Lehrerin muss danach kurz neu laden.
- **openrouter**: Minecraft nimmt selbst über das Standard-Mikrofon auf, ein Audio-Modell über OpenRouter schreibt
  mit (Standard: dein normales Modell, z. B. `google/gemini-2.5-flash`). Unter macOS braucht der Launcher dafür die
  Mikrofon-Freigabe.
- **auto** (Standard): der Helfer, wenn er läuft und die Sprache kann, sonst OpenRouter.

## Anki-Export

Lernmenü → **Anki-Export**. Ankreuzen, was exportiert werden soll:

- die Aufgaben und Themen aus den PDFs (Vorderseite Aufgabe, Rückseite Lösung aus der PDF),
- die im Spiel gestellten Aufgaben mit der Musterlösung der KI, auf Wunsch nur die falsch beantworteten,
- die Sätze aus den Sprechübungen (Vorderseite Übersetzung, Rückseite Satz mit Pinyin).

Die Datei landet in `saves/<Welt>/unilernen/anki/` und ihr Pfad in der Zwischenablage. In Anki: **Datei →
Importieren** und die Datei wählen. Deck (mit Unterdecks pro Fach), Notiztyp *Basic* und Schlagwörter stehen schon in
der Datei, du musst nichts einstellen. Doppelte Karten erkennt Anki beim nächsten Export selbst.

## Welche Aufgaben kommen?

Unter *Einstellungen → Aufgaben*:

| Einstellung | Was passiert |
|---|---|
| **gemischt** (Standard) | Mal die Original-Aufgabe aus dem PDF, mal eine ähnliche mit neuen Zahlen, mal eine Kurzfrage |
| **aus dem PDF** | Genau die Aufgaben aus deinen Unterlagen (Klausurtraining) |
| **ähnliche (KI)** | Gleicher Aufgabentyp und gleiche Methode, aber andere Zahlen/Funktionen, damit du nicht auswendig lernst |
| **Kurzfragen** | Kurze Verständnisfragen zum Thema (Definitionen, Sätze, kleine Beispiele) |

Aufgaben, die du falsch hattest, kommen öfter dran; gerade gestellte seltener. Aus Skripten ohne Aufgaben macht
die KI Fragen zu den wichtigsten Inhalten.

## Booster

Für jede richtige Aufgabe gibt es einen zufälligen Booster. **Y** (auf deutscher Tastatur die **Z**-Taste) öffnet das
Booster-Inventar, ein Klick setzt einen ein. Das Booster-Inventar gehört zur Welt und ist getrennt vom normalen Inventar.

5 Extra-Herzen · Mehr Leben (+4 Herzen) · Feuerresistenz (3 min) · Sofortheilung · Regeneration · Tempo · Eile ·
Stärke · Resistenz · Nachtsicht · Wasseratmung · Sprungkraft · Sanfter Fall · Satt · Glück · Unsichtbarkeit ·
Delfingnade · Meereskraft · Held des Dorfes

## Einstellungen

| Einstellung | Standard | Bedeutung |
|---|---|---|
| OpenRouter-Key | – | Dein Key (`sk-or-…`). Wird nach dem Speichern nur gekürzt angezeigt. |
| Modell | `google/gemini-2.5-flash` | Jede Modell-ID von [openrouter.ai/models](https://openrouter.ai/models). Für Mathe lohnt sich ein starkes Modell. |
| Alle N min eine Aufgabe | 5 | Gemessen in Spielzeit, Pausen zählen nicht |
| Zufall | ±20 % | So stark schwankt der Abstand |
| Aufgaben | gemischt | siehe oben |
| Falsch | 1 Stapel weg | oder nur 1 Gegenstand, oder keine Strafe |
| Lösung nach N Fehlern | 3 | 0 = nie |
| PDF-Leser | auto | `pdf-text` = kostenlos, nur PDFs mit echtem Text · `mistral-ocr` = auch Scans, Handschrift, Formeln als Bild (kostet etwas) · `native` = das Modell liest die PDF selbst |
| Anzeige | oben rechts | Countdown und Booster-Zahl (oben links stört die Anzeige von Chinese-Minecraft) |
| Automatische Aufgaben | an | aus = nur über *Jetzt eine Aufgabe* im Lernmenü |
| Sprechen … | aus | Sprechübungen, Sprache, Erkennung, nötige Trefferquote, Strafe; siehe oben |
| Sprache | Deutsch | In dieser Sprache stellt und bewertet die KI |
| Wünsche an KI | – | z. B. „Klausurniveau“, „mehr Beweise“, „nur Rechenaufgaben“ |

Wird eine PDF nicht richtig gelesen (z. B. eingescannte Blätter): *PDF-Leser* auf `mistral-ocr` stellen und in der
PDF-Liste auf **↻** klicken.

## Tasten

| Taste | Funktion |
|---|---|
| **O** | Lernmenü: nächste Aufgabe, Statistik, Booster, Unterlagen, *Jetzt eine Aufgabe*, Einstellungen |
| **Y** (deutsch: **Z**) | Booster-Inventar |

Änderbar unter Optionen → Steuerung → Tastenbelegung → *Lernen generell*. Die Tasten von Chinese-Minecraft
(J, H, U, I, K, N, V, B) sind nicht belegt.

## Wo liegt was?

| Datei | Inhalt |
|---|---|
| `.minecraft/config/unilernen.json` | Einstellungen inkl. API-Key (nicht weitergeben!) |
| `.minecraft/saves/<Welt>/unilernen/welt.json` | Aufgaben, offene Aufgabe, Booster-Inventar, Statistik der Welt |
| `.minecraft/saves/<Welt>/unilernen/anki/` | Die Anki-Exporte |
| `.minecraft/saves/<Welt>/unilernen/pdfs/` | Die hochgeladenen PDFs. Du kannst PDFs auch direkt hier hineinlegen und im Spiel *Ordner einlesen* klicken. |

## Grenzen

- **Einzelspieler.** Gegenstände löschen, Booster und das Pausieren gehen nur in Einzelspielerwelten (wie bei
  Chinese-Minecraft). Auf Servern und in fürs LAN geöffneten Welten kommen die Aufgaben zwar, aber das Spiel läuft weiter.
- Die PDFs werden an OpenRouter und den dort gewählten Modellanbieter geschickt.
- Die KI kann sich irren. Bei einer offensichtlich falschen Bewertung: noch einmal abschicken, mit kurzer Begründung.

## Mod bauen

**Mit GitHub:** Nach jedem Push baut GitHub Actions die Mod. Im Repo unter *Actions* → letzter Lauf → *Artifacts* →
`lernen-generell` herunterladen und entpacken.

**Auf dem eigenen Rechner** (Java 25):

```bash
./gradlew build
```

Die fertige Datei liegt in `build/libs/lernen-generell-0.1.0+mc26.3.jar`. Mit `./gradlew runClient` startet ein
Test-Minecraft mit der Mod.

## Aufbau des Codes

| Klasse | Aufgabe |
|---|---|
| `core/StudyManager` | Ablauf: Countdown, nächste Aufgabe vorbereiten, Antwort prüfen, Strafe, Booster, Tipps, Lösung |
| `core/MaterialLibrary` | PDFs hochladen, in die Welt kopieren und nacheinander von der KI lesen lassen |
| `core/OpenRouter`, `core/OpenRouterAi` | API-Aufrufe (Chat mit PDF-Anhang) |
| `core/Prompts` | Die Anweisungen an die KI (Aufgaben herauslesen, erstellen, bewerten, Tipp, Lösung) |
| `core/AiJson`, `core/MathText` | KI-Antworten robust lesen; LaTeX-Reste in Unicode-Mathe umwandeln |
| `core/SpeakingSession`, `core/SpeechCompare` | Die drei Sprechstufen; vergleicht Gehörtes Wort für Wort (Chinesisch über Pinyin) |
| `core/HelperSpeech`, `core/MicSpeech` | Spracherkennung über den Helfer von Chinese-Minecraft oder Aufnahme im Spiel + OpenRouter |
| `core/AnkiExport` | Anki-Importdatei schreiben |
| `core/Boosters`, `core/WorldData`, `core/UniConfig` | Booster-Liste, Daten pro Welt, Einstellungen |
| `TaskScreen` | Das Aufgabenfenster (pausiert, nicht schließbar) |
| `SpeakScreen` | Das Sprechfenster mit den drei Stufen |
| `AnkiScreen`, `SpeechSettingsScreen` | Anki-Export, Sprech-Einstellungen |
| `MenuScreen`, `BoosterScreen`, `MaterialScreen`, `SettingsScreen` | Lernmenü, Booster-Inventar, Unterlagen, Einstellungen |
| `GameActions` | Gegenstand löschen und Booster-Effekte (auf dem eingebauten Server) |
| `Hud` | Countdown oben rechts |

Der Kern (`core/`) kennt Minecraft nicht; die Tests in `src/test/` prüfen ihn mit einer KI-Attrappe.

Aufbau, Build und Teile der Oberfläche sind an [Chinese-Minecraft](https://github.com/nphjfvp/Chinese-Minecraft)
angelehnt. Lizenz: MIT (`LICENSE`).
