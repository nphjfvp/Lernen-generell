package dev.lernen.uni.core;

import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Der Ablauf: etwa alle N Minuten Spielzeit eine Aufgabe stellen, Antworten prüfen lassen, bei falscher
 * Antwort strafen, bei richtiger einen Booster geben. Die nächste Aufgabe wird im Hintergrund schon
 * vorbereitet, damit sie pünktlich da ist. Kennt Minecraft nicht; das Spiel hängt sich über {@link Game} an.
 */
public final class StudyManager {
   /** Wie lange nach einem KI-Fehler gewartet wird, bevor es wieder versucht wird. */
   static final long RETRY_MS = 60_000L;
   /** Nach so vielen Fehlern der KI hintereinander beim Prüfen darf man die Aufgabe überspringen. */
   public static final int SKIP_AFTER_ERRORS = 3;

   public interface Game {
      /** Zeigt die offene Aufgabe (pausiert das Spiel). */
      void openChallenge();

      /** Ob das Aufgabenfenster gerade offen ist. */
      boolean challengeOpen();

      /** Löscht zufällige Gegenstände: "stapel" oder "einzeln". */
      void penalize(String mode);

      void message(String text);
   }

   private final Supplier<UniConfig> config;
   private final Ai ai;
   private final Executor main;
   private final Random random;
   private final LongSupplier clock;
   private final MaterialLibrary materials;

   private WorldData data;
   private Game game;
   /** Zählt hoch bei jedem Weltwechsel; Antworten der KI für eine alte Welt werden verworfen. */
   private int session;
   private long nextDueAt;
   private WorldData.Challenge prepared;
   private boolean preparing;
   private long retryAt;
   private String prepareError = "";

   private boolean busy;
   private String status = "";
   private boolean statusError;
   private int checkErrors;
   /** Die zuletzt richtig gelöste Aufgabe, damit das Fenster "Richtig!" zeigen kann. */
   private WorldData.Challenge solved;
   private String solvedBooster = "";
   /** Ob gerade eine Spracherkennung bereitsteht; ohne sie gibt es keine Sprechübungen. */
   private BooleanSupplier speechAvailable = () -> false;
   /** Die nächste vorbereitete Aufgabe soll eine Sprechübung sein (über das Menü angefordert). */
   private boolean speakNowPending;

   public StudyManager(Supplier<UniConfig> config, Ai ai, Executor main, Random random, LongSupplier clock) {
      this.config = config;
      this.ai = ai;
      this.main = main;
      this.random = random;
      this.clock = clock;
      this.materials = new MaterialLibrary(config, ai, main, clock);
   }

   public void attach(WorldData data, Game game) {
      this.session++;
      this.data = data;
      this.game = game;
      this.prepared = null;
      this.preparing = false;
      this.retryAt = 0L;
      this.prepareError = "";
      this.busy = false;
      this.status = "";
      this.checkErrors = 0;
      this.solved = null;
      this.nextDueAt = data.playMs + this.nextInterval();
      this.materials.attach(data, this.session);
   }

   public void detach() {
      if (this.data != null) {
         this.data.save();
      }
      this.session++;
      this.data = null;
      this.game = null;
      this.prepared = null;
      this.preparing = false;
      this.busy = false;
      this.materials.detach();
   }

   public void setSpeechAvailable(BooleanSupplier available) {
      this.speechAvailable = available;
   }

   private boolean speakingPossible() {
      UniConfig c = this.config.get();
      return (c.speechShare() > 0.0 || this.speakNowPending) && this.speechAvailable.getAsBoolean();
   }

   /** Ob überhaupt etwas gestellt werden kann: Aufgaben aus PDFs oder Sprechübungen. */
   private boolean hasMaterial() {
      return !this.data.tasks.isEmpty() || this.speakingPossible();
   }

   public WorldData data() {
      return this.data;
   }

   public MaterialLibrary materials() {
      return this.materials;
   }

   long nextInterval() {
      UniConfig c = this.config.get();
      long base = c.intervalMs();
      double jitter = c.jitterPercent / 100.0;
      double factor = 1.0 + (this.random.nextDouble() * 2.0 - 1.0) * jitter;
      return Math.max(15_000L, Math.round(base * factor));
   }

   /**
    * Jeden Spieltick aufrufen.
    *
    * @param deltaMs vergangene Zeit
    * @param playing ob gerade gespielt wird (Welt geladen, nicht pausiert); nur dann läuft die Uhr
    * @param screenFree ob kein anderes Fenster offen ist, sodass eine Aufgabe aufgehen darf
    */
   public void tick(long deltaMs, boolean playing, boolean screenFree) {
      if (this.data == null || this.game == null) {
         return;
      }
      if (playing && this.data.active == null) {
         this.data.playMs += Math.max(0L, Math.min(deltaMs, 1000L));
      }
      this.materials.tick();

      if (this.data.active != null) {
         if (screenFree && !this.game.challengeOpen()) {
            this.game.openChallenge();
         }
         return;
      }

      UniConfig c = this.config.get();
      if (!c.hasKey() || !this.hasMaterial()) {
         return;
      }
      if (!c.enabled && !this.askNowPending) {
         return;
      }
      if (this.prepared == null && !this.preparing && this.clock.getAsLong() >= this.retryAt) {
         this.prepare();
      }
      if (this.prepared != null && this.data.playMs >= this.nextDueAt && screenFree) {
         this.activate();
      }
   }

   /** Über das Menü angefordert, auch wenn automatische Aufgaben aus sind. */
   private boolean askNowPending;

   /** Stellt sofort eine Aufgabe (sobald sie vorbereitet ist). */
   public void askNow() {
      if (this.data == null) {
         return;
      }
      this.nextDueAt = this.data.playMs;
      this.askNowPending = true;
      this.retryAt = 0L;
   }

   /** Stellt sofort eine Sprechübung (über das Menü). */
   public void speakNow() {
      if (this.data == null) {
         return;
      }
      if (this.prepared != null && !this.prepared.isSpeaking()) {
         this.prepared = null;
      }
      this.speakNowPending = true;
      this.askNow();
   }

   private void prepare() {
      WorldData.Task task = TaskPicker.pick(this.data.tasks, this.data.playMs, this.random);
      UniConfig cfg = this.config.get();
      boolean speak = this.speakingPossible() && (this.speakNowPending || task == null || this.random.nextDouble() < cfg.speechShare());
      if (speak) {
         this.prepareSpeaking(task, cfg.speechLanguage);
         return;
      }
      if (task == null) {
         return;
      }
      String style = TaskPicker.style(this.config.get(), task, this.random);
      if ("original".equals(style)) {
         WorldData.Challenge c = new WorldData.Challenge();
         c.taskId = task.id;
         c.style = style;
         c.title = task.title;
         c.question = task.text;
         c.solution = task.solution;
         this.prepared = c;
         return;
      }

      this.preparing = true;
      int mySession = this.session;
      String subjects = this.data.subjects();
      this.ai.generate(task, style, subjects).whenComplete((c, error) -> this.main.execute(() -> {
         if (mySession != this.session) {
            return;
         }
         this.preparing = false;
         if (error != null || c == null) {
            this.prepareError = error == null ? "keine Aufgabe" : OpenRouter.describe(error);
            this.retryAt = this.clock.getAsLong() + RETRY_MS;
            Log.warn("Aufgabe konnte nicht erstellt werden: " + this.prepareError);
         } else {
            this.prepareError = "";
            this.prepared = c;
         }
      }));
   }

   private void prepareSpeaking(WorldData.Task task, String language) {
      this.preparing = true;
      int mySession = this.session;
      this.ai.generateSpeaking(task, language, this.data.subjects()).whenComplete((c, error) -> this.main.execute(() -> {
         if (mySession != this.session) {
            return;
         }
         this.preparing = false;
         if (error != null || c == null) {
            this.prepareError = error == null ? "kein Satz" : OpenRouter.describe(error);
            this.retryAt = this.clock.getAsLong() + RETRY_MS;
            Log.warn("Sprechübung konnte nicht erstellt werden: " + this.prepareError);
         } else {
            this.prepareError = "";
            this.prepared = c;
         }
      }));
   }

   private void activate() {
      WorldData.Challenge c = this.prepared;
      this.prepared = null;
      this.askNowPending = false;
      if (c.isSpeaking()) {
         this.speakNowPending = false;
      }
      WorldData.Task task = this.data.task(c.taskId);
      if (task != null) {
         task.asked++;
         task.lastAskedAt = this.data.playMs;
      }
      c.createdAt = this.data.playMs;
      this.data.active = c;
      this.busy = false;
      this.status = "";
      this.statusError = false;
      this.checkErrors = 0;
      this.solved = null;
      this.data.save();
      this.game.openChallenge();
   }

   public void submit(String answer) {
      WorldData.Challenge c = this.active();
      if (c == null || this.busy) {
         return;
      }
      String a = answer == null ? "" : answer.strip();
      if (a.isEmpty()) {
         this.setStatus("Bitte zuerst eine Antwort eintippen.", true);
         return;
      }
      this.busy = true;
      this.setStatus("Die KI prüft deine Antwort …", false);
      int mySession = this.session;
      this.ai.check(c, a).whenComplete((verdict, error) -> this.main.execute(() -> {
         if (mySession != this.session || this.data == null || this.data.active != c) {
            return;
         }
         this.busy = false;
         if (error != null || verdict == null) {
            this.checkErrors++;
            this.setStatus("Konnte nicht prüfen: " + (error == null ? "keine Antwort" : OpenRouter.describe(error)) + " Nochmal abschicken.", true);
            return;
         }
         this.checkErrors = 0;
         c.attempts.add(new WorldData.Attempt(a, verdict.correct(), verdict.feedback()));
         if (verdict.correct()) {
            this.finishCorrect(c);
         } else {
            this.handleWrong(c, verdict.feedback());
         }
      }));
   }

   private void handleWrong(WorldData.Challenge c, String feedback) {
      WorldData.Task task = this.data.task(c.taskId);
      if (task != null) {
         task.wrong++;
      }
      this.data.stats.wrongAnswers++;
      this.data.stats.streak = 0;
      this.setStatus("Falsch. " + feedback, true);
      String penalty = this.config.get().penalty;
      if (!"keine".equals(penalty)) {
         this.game.penalize(penalty);
      }
      this.data.save();
   }

   private void finishCorrect(WorldData.Challenge c) {
      WorldData.Task task = this.data.task(c.taskId);
      if (task != null) {
         task.correct++;
      }
      WorldData.Stats s = this.data.stats;
      s.solved++;
      if (c.attempts.size() == 1 && !c.solutionShown) {
         s.solvedFirstTry++;
      }
      s.streak++;
      s.bestStreak = Math.max(s.bestStreak, s.streak);
      this.solvedBooster = "";
      if (!c.solutionShown) {
         Boosters.Booster b = Boosters.random(this.random);
         this.data.addBooster(b.id());
         s.boostersEarned++;
         this.solvedBooster = b.id();
         this.game.message("§a✔ Richtig! §7Neuer Booster: §e" + b.name() + "§7 (Lernmenü → Booster)");
      } else {
         this.game.message("§a✔ Geschafft. §7Mit gezeigter Lösung gibt es keinen Booster.");
      }
      this.remember(c, true);
      this.data.active = null;
      this.solved = c;
      this.setStatus("Richtig!", false);
      this.nextDueAt = this.data.playMs + this.nextInterval();
      this.data.save();
   }

   public void requestHint() {
      WorldData.Challenge c = this.active();
      if (c == null || this.busy) {
         return;
      }
      if (c.hintsShown < c.hints.size()) {
         c.hintsShown++;
         this.data.save();
         return;
      }
      this.busy = true;
      this.setStatus("Die KI überlegt sich einen Tipp …", false);
      int mySession = this.session;
      this.ai.hint(c).whenComplete((hint, error) -> this.main.execute(() -> {
         if (mySession != this.session || this.data == null || this.data.active != c) {
            return;
         }
         this.busy = false;
         if (error != null || hint == null || hint.isBlank()) {
            this.setStatus("Kein Tipp: " + (error == null ? "leere Antwort" : OpenRouter.describe(error)), true);
            return;
         }
         c.hints.add(hint);
         c.hintsShown = c.hints.size();
         this.setStatus("", false);
         this.data.save();
      }));
   }

   public boolean canShowSolution() {
      WorldData.Challenge c = this.active();
      int after = this.config.get().solutionAfterWrong;
      return c != null && !c.solutionShown && after > 0 && c.wrongAttempts() >= after;
   }

   public void requestSolution() {
      WorldData.Challenge c = this.active();
      if (c == null || this.busy || !this.canShowSolution()) {
         return;
      }
      if (!c.solution.isBlank()) {
         c.solutionShown = true;
         this.setStatus("Lösung aufgedeckt: Tipp das Ergebnis selbst ein, um weiterzuspielen (ohne Booster).", false);
         this.data.save();
         return;
      }
      this.busy = true;
      this.setStatus("Die KI schreibt die Lösung auf …", false);
      int mySession = this.session;
      this.ai.solve(c).whenComplete((solution, error) -> this.main.execute(() -> {
         if (mySession != this.session || this.data == null || this.data.active != c) {
            return;
         }
         this.busy = false;
         if (error != null || solution == null || solution.isBlank()) {
            this.setStatus("Keine Lösung: " + (error == null ? "leere Antwort" : OpenRouter.describe(error)), true);
            return;
         }
         c.solution = solution;
         c.solutionShown = true;
         this.setStatus("Lösung aufgedeckt: Tipp das Ergebnis selbst ein, um weiterzuspielen (ohne Booster).", false);
         this.data.save();
      }));
   }

   /** Nur wenn die KI mehrmals hintereinander nicht prüfen konnte: sonst säße man fest. */
   public boolean canSkip() {
      return this.active() != null && this.checkErrors >= SKIP_AFTER_ERRORS && !this.busy;
   }

   public void skip() {
      if (!this.canSkip()) {
         return;
      }
      this.remember(this.data.active, false);
      this.data.active = null;
      this.solved = null;
      this.nextDueAt = this.data.playMs + this.nextInterval();
      this.game.message("§7Aufgabe wegen Fehlern übersprungen.");
      this.data.save();
   }

   /** Höchstens so viele Einträge im Verlauf (für den Anki-Export). */
   static final int HISTORY_MAX = 2000;

   private void remember(WorldData.Challenge c, boolean solved) {
      WorldData.HistoryEntry h = new WorldData.HistoryEntry();
      WorldData.Task task = this.data.task(c.taskId);
      h.kind = c.isSpeaking() ? "sprechen" : "aufgabe";
      h.title = c.title;
      h.question = c.question;
      h.solution = c.solution;
      h.source = task == null ? "" : task.source;
      h.topic = task == null ? "" : task.topic;
      h.wrongAttempts = c.wrongAttempts();
      h.solved = solved;
      h.at = System.currentTimeMillis();
      if (c.isSpeaking()) {
         h.language = c.speech.language;
         h.reading = c.speech.reading();
         h.translation = c.speech.translation;
      }
      this.data.history.add(h);
      while (this.data.history.size() > HISTORY_MAX) {
         this.data.history.removeFirst();
      }
   }

   /** Die Sprechübung ist durch alle drei Stufen: gilt als richtig gelöst. */
   public void finishSpeaking() {
      WorldData.Challenge c = this.active();
      if (c == null || !c.isSpeaking()) {
         return;
      }
      c.attempts.add(new WorldData.Attempt(c.speech.sentence, true, "Alle drei Stufen geschafft."));
      this.checkErrors = 0;
      this.finishCorrect(c);
   }

   /** Ein ganzer Satz (Stufe 2/3) ist nicht geschafft. Kostet nur dann etwas, wenn es eingestellt ist. */
   public void speechFailed(String feedback) {
      WorldData.Challenge c = this.active();
      if (c == null || !c.isSpeaking()) {
         return;
      }
      c.attempts.add(new WorldData.Attempt("(gesprochen)", false, feedback));
      this.checkErrors = 0;
      UniConfig cfg = this.config.get();
      if (cfg.speechPenalty) {
         this.data.stats.wrongAnswers++;
         this.data.stats.streak = 0;
         if (!"keine".equals(cfg.penalty)) {
            this.game.penalize(cfg.penalty);
         }
      }
      this.data.save();
   }

   /** Die Spracherkennung ist ausgefallen (kein Mikrofon, Helfer weg …); nach mehreren Fehlern darf man überspringen. */
   public void speechError(String message) {
      this.checkErrors++;
      this.setStatus(message, true);
   }

   public void speechOk() {
      this.checkErrors = 0;
   }

   /** Speichert den Fortschritt der Sprechübung (Stufe, Wort). */
   public void saveProgress() {
      if (this.data != null) {
         this.data.save();
      }
   }

   /** Vom Spiel gemeldet, nachdem die Strafe ausgeführt wurde. */
   public void onItemsLost(int stacks) {
      if (this.data != null) {
         this.data.stats.itemsLost += stacks;
         this.data.save();
      }
   }

   private void setStatus(String text, boolean error) {
      this.status = text;
      this.statusError = error;
   }

   public WorldData.Challenge active() {
      return this.data == null ? null : this.data.active;
   }

   public WorldData.Challenge solved() {
      return this.solved;
   }

   public String solvedBooster() {
      return this.solvedBooster;
   }

   public void clearSolved() {
      this.solved = null;
   }

   public boolean busy() {
      return this.busy;
   }

   public String status() {
      return this.status;
   }

   public boolean statusError() {
      return this.statusError;
   }

   public boolean preparing() {
      return this.preparing;
   }

   public boolean hasPrepared() {
      return this.prepared != null;
   }

   public String prepareError() {
      return this.prepareError;
   }

   /** Millisekunden Spielzeit bis zur nächsten Aufgabe (0 = fällig). */
   public long msUntilNext() {
      return this.data == null ? -1L : Math.max(0L, this.nextDueAt - this.data.playMs);
   }

   /** Für die Anzeige: warum gerade keine Aufgaben kommen, oder leer. */
   public String blocker() {
      UniConfig c = this.config.get();
      if (this.data == null) {
         return "";
      }
      if (!c.hasKey()) {
         return "Kein OpenRouter-Key";
      }
      if (!this.hasMaterial()) {
         return this.materials.working() ? "PDF wird gelesen …" : "Keine Unterlagen";
      }
      if (!c.enabled) {
         return "Automatische Aufgaben aus";
      }
      return "";
   }
}
