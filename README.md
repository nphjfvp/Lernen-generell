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
| `core/Boosters`, `core/WorldData`, `core/UniConfig` | Booster-Liste, Daten pro Welt, Einstellungen |
| `TaskScreen` | Das Aufgabenfenster (pausiert, nicht schließbar) |
| `MenuScreen`, `BoosterScreen`, `MaterialScreen`, `SettingsScreen` | Lernmenü, Booster-Inventar, Unterlagen, Einstellungen |
| `GameActions` | Gegenstand löschen und Booster-Effekte (auf dem eingebauten Server) |
| `Hud` | Countdown oben rechts |

Der Kern (`core/`) kennt Minecraft nicht; die Tests in `src/test/` prüfen ihn mit einer KI-Attrappe.

Aufbau, Build und Teile der Oberfläche sind an [Chinese-Minecraft](https://github.com/nphjfvp/Chinese-Minecraft)
angelehnt. Lizenz: MIT (`LICENSE`).
