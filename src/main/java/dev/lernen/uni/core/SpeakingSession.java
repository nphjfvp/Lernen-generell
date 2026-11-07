package dev.lernen.uni.core;

import java.util.Arrays;
import java.util.List;

/**
 * Die drei Stufen einer Sprechübung:
 * <ol>
 *   <li><b>Langsam, Wort für Wort:</b> Der ganze Satz steht da, man spricht ein Wort nach dem anderen; jedes Wort
 *   wird oben grün (richtig) oder rot (falsch) markiert. Ein falsches Wort wird wiederholt.</li>
 *   <li><b>Etwas schneller:</b> der ganze Satz in einem Zug, mit großzügigem Zeitlimit.</li>
 *   <li><b>Normales Tempo:</b> der ganze Satz in normaler Sprechgeschwindigkeit.</li>
 * </ol>
 * In Stufe 2 und 3 müssen genug Wörter erkannt werden ({@link UniConfig#speechPassPercent}) und das Tempo stimmen.
 */
public final class SpeakingSession {
   public static final int WORDS = 0;
   public static final int SLOW = 1;
   public static final int NORMAL = 2;
   public static final int DONE = 3;
   /** Nach so vielen Fehlversuchen an einem Wort darf man es in Stufe 1 überspringen. */
   public static final int SKIP_WORD_AFTER = 3;
   /** Reaktionszeit vor dem ersten Wort, die nicht zum Tempo zählt. */
   static final double GRACE_S = 1.5;

   public static final int MARK_NONE = 0;
   public static final int MARK_OK = 1;
   public static final int MARK_WRONG = 2;
   public static final int MARK_SKIPPED = 3;

   private final WorldData.Challenge challenge;
   private final WorldData.SpeechTask task;
   private final int passPercent;
   private final int[] marks;
   private final double[] scores;
   private int failsOnWord;
   private int sentenceTries;
   private String feedback = "";
   private boolean lastGood;

   public SpeakingSession(WorldData.Challenge challenge, int passPercent) {
      this.challenge = challenge;
      this.task = challenge.speech;
      this.passPercent = passPercent;
      this.marks = new int[this.task.words.size()];
      this.scores = new double[this.task.words.size()];
      // Nach dem Neuladen: die Wörter vor dem aktuellen gelten in Stufe 1 als geschafft.
      if (challenge.speechStage == WORDS) {
         for (int i = 0; i < Math.min(challenge.speechWord, this.marks.length); i++) {
            this.marks[i] = MARK_OK;
         }
      }
   }

   public int stage() {
      return this.challenge.speechStage;
   }

   public int word() {
      return this.challenge.speechWord;
   }

   public boolean done() {
      return this.stage() >= DONE;
   }

   public List<WorldData.SpeechWord> words() {
      return this.task.words;
   }

   public WorldData.SpeechTask task() {
      return this.task;
   }

   public int mark(int i) {
      return this.marks[i];
   }

   public double score(int i) {
      return this.scores[i];
   }

   public String feedback() {
      return this.feedback;
   }

   /** Ob der letzte Versuch geklappt hat (für Ton und Farbe). */
   public boolean lastGood() {
      return this.lastGood;
   }

   public boolean canSkipWord() {
      return this.stage() == WORDS && this.failsOnWord >= SKIP_WORD_AFTER;
   }

   public int sentenceTries() {
      return this.sentenceTries;
   }

   /** Mindest-Sprechtempo der Stufe in Einheiten (Silben/Zeichen/Wörter) pro Sekunde. */
   static double rate(String language, int stage) {
      boolean slow = stage == SLOW;
      if ("zh".equals(language)) {
         return slow ? 1.0 : 2.2;
      }
      if ("ja".equals(language)) {
         return slow ? 1.5 : 3.5;
      }
      return slow ? 0.9 : 1.8;
   }

   /** Wie lange man für den ganzen Satz höchstens brauchen darf (Sekunden), 0 = egal (Stufe 1). */
   public double timeLimit() {
      if (this.stage() != SLOW && this.stage() != NORMAL) {
         return 0.0;
      }
      int units = SpeechCompare.units(this.task.words, this.task.language);
      return units / rate(this.task.language, this.stage()) + GRACE_S;
   }

   /** Wie lange eine Aufnahme höchstens läuft, bevor sie von selbst endet. */
   public double maxRecordSeconds() {
      return this.stage() == WORDS ? 6.0 : this.timeLimit() + 3.0;
   }

   /** Was gerade gesprochen werden soll: ein Wort (Stufe 1) oder der ganze Satz. */
   public List<WorldData.SpeechWord> expected() {
      if (this.stage() == WORDS) {
         return List.of(this.task.words.get(this.word()));
      }
      return this.task.words;
   }

   /** Der erwartete Text für die Erkennung. */
   public String expectedText() {
      if (this.stage() == WORDS) {
         return this.task.words.get(this.word()).text;
      }
      return this.task.sentence;
   }

   /**
    * Wertet eine Aufnahme aus.
    *
    * @param speechSeconds Sprechdauer ohne die Stille am Ende
    * @return true, wenn ein Satz (Stufe 2/3) nicht geschafft wurde – dafür kann es eine Strafe geben
    */
   public boolean apply(SpeechCompare.Heard heard, double speechSeconds) {
      if (this.done()) {
         return false;
      }
      if (heard.silent() || heard.empty()) {
         this.lastGood = false;
         this.feedback = "Nichts gehört – ist das Mikrofon an? Sprich deutlich und etwas lauter.";
         return false;
      }
      if (this.stage() == WORDS) {
         this.applyWord(heard);
         return false;
      }
      return this.applySentence(heard, speechSeconds);
   }

   private void applyWord(SpeechCompare.Heard heard) {
      int i = this.word();
      double s = SpeechCompare.score(this.expected(), this.task.language, heard)[0];
      this.scores[i] = s;
      WorldData.SpeechWord w = this.task.words.get(i);
      if (s >= SpeechCompare.WORD_OK) {
         this.marks[i] = MARK_OK;
         this.lastGood = true;
         this.feedback = "✔ " + w.text + " – richtig!";
         this.nextWord();
      } else {
         this.marks[i] = MARK_WRONG;
         this.lastGood = false;
         this.failsOnWord++;
         this.feedback = "✘ " + w.text + " – gehört: \"" + shown(heard) + "\". Nochmal langsam: " + w.text + (w.reading.isBlank() ? "" : " (" + w.reading + ")");
      }
   }

   /** In Stufe 1 ein Wort auslassen, an dem die Erkennung scheitert. */
   public void skipWord() {
      if (this.canSkipWord()) {
         this.marks[this.word()] = MARK_SKIPPED;
         this.feedback = "Wort übersprungen.";
         this.nextWord();
      }
   }

   private void nextWord() {
      this.failsOnWord = 0;
      this.challenge.speechWord++;
      if (this.challenge.speechWord >= this.task.words.size()) {
         this.feedback = "Stufe 1 geschafft! Jetzt den ganzen Satz in einem Zug, etwas schneller.";
         this.enterStage(SLOW);
      }
   }

   private void enterStage(int stage) {
      this.challenge.speechStage = stage;
      this.challenge.speechWord = 0;
      this.sentenceTries = 0;
      Arrays.fill(this.marks, MARK_NONE);
      Arrays.fill(this.scores, 0.0);
   }

   private boolean applySentence(SpeechCompare.Heard heard, double speechSeconds) {
      this.sentenceTries++;
      double[] s = SpeechCompare.score(this.task.words, this.task.language, heard);
      int ok = 0;
      for (int i = 0; i < s.length; i++) {
         this.scores[i] = s[i];
         this.marks[i] = s[i] >= SpeechCompare.WORD_OK ? MARK_OK : MARK_WRONG;
         if (this.marks[i] == MARK_OK) {
            ok++;
         }
      }
      int percent = Math.round(100.0F * ok / Math.max(1, s.length));
      double limit = this.timeLimit();
      boolean wordsOk = percent >= this.passPercent;
      boolean fastEnough = speechSeconds <= limit;
      String time = String.format(java.util.Locale.ROOT, "%.1f s (Ziel ≤ %.1f s)", speechSeconds, limit);
      if (wordsOk && fastEnough) {
         this.lastGood = true;
         if (this.stage() == SLOW) {
            this.feedback = "✔ " + percent + " % richtig in " + time + ". Letzte Stufe: normales Sprechtempo!";
            this.enterStage(NORMAL);
         } else {
            this.feedback = "✔ " + percent + " % richtig in " + time + " – geschafft!";
            this.challenge.speechStage = DONE;
         }
         return false;
      }
      this.lastGood = false;
      if (!wordsOk) {
         this.feedback = "✘ Nur " + percent + " % der Wörter erkannt (nötig: " + this.passPercent + " %). Rot = nochmal üben. Gehört: \"" + shown(heard) + "\"";
      } else {
         this.feedback = "✘ Zu langsam: " + time + ". Flüssiger sprechen, ohne Pausen zwischen den Wörtern.";
      }
      return true;
   }

   private static String shown(SpeechCompare.Heard heard) {
      String t = heard.text();
      if (!heard.reading().isEmpty()) {
         t += " (" + String.join(" ", heard.reading()) + ")";
      }
      return t.length() > 80 ? t.substring(0, 80) + "…" : t;
   }

   public static String stageName(int stage) {
      return switch (stage) {
         case WORDS -> "Stufe 1/3: Langsam, Wort für Wort";
         case SLOW -> "Stufe 2/3: Ganzer Satz, etwas schneller";
         case NORMAL -> "Stufe 3/3: Normales Sprechtempo";
         default -> "Geschafft";
      };
   }
}
