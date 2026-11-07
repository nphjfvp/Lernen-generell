package dev.lernen.uni.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alles, was zu einer Welt gehört: die hochgeladenen PDFs, die daraus gelesenen Aufgaben, die offene
 * Aufgabe, die Booster und eine kleine Statistik. Liegt im Weltordner unter {@code unilernen/welt.json}.
 */
public final class WorldData {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   public int version = 1;
   public List<Source> sources = new ArrayList<>();
   public List<Task> tasks = new ArrayList<>();
   /** Die gerade gestellte Aufgabe. Solange sie gesetzt ist, geht das Spiel nicht weiter. */
   public Challenge active;
   /** Booster-ID → Anzahl. Das ist das separate Booster-Inventar. */
   public Map<String, Integer> boosters = new LinkedHashMap<>();
   public Stats stats = new Stats();
   /** Gestellte Aufgaben mit Lösung, für den Anki-Export (die neuesten hinten). */
   public List<HistoryEntry> history = new ArrayList<>();
   /** Gespielte Zeit in dieser Welt (ms, ohne Pausen); misst die Abstände der Aufgaben. */
   public long playMs;

   private transient Path file;

   public static final class Source {
      /** Dateiname im Ordner {@code unilernen/pdfs}. */
      public String file = "";
      public long addedAt;
      /** wartet, liest, fertig, fehler */
      public String status = "wartet";
      public String error = "";
      public String subject = "";
      public String summary = "";
      public int taskCount;

      public boolean done() {
         return "fertig".equals(this.status);
      }
   }

   public static final class Task {
      public String id = "";
      /** Dateiname der PDF, aus der die Aufgabe stammt. */
      public String source = "";
      /** aufgabe (echte Aufgabe aus dem PDF) oder stoff (Thema aus einem Skript, ohne fertige Aufgabe) */
      public String kind = "aufgabe";
      public String title = "";
      public String text = "";
      public String solution = "";
      public String topic = "";
      public int asked;
      public int correct;
      public int wrong;
      /** Spielzeit (ms) beim letzten Stellen. */
      public long lastAskedAt = -1L;

      public boolean isMaterialOnly() {
         return "stoff".equals(this.kind);
      }
   }

   public static final class Challenge {
      public String taskId = "";
      /** original, aehnlich, kurz */
      public String style = "";
      public String title = "";
      public String question = "";
      /** Musterlösung, wird erst nach mehreren Fehlversuchen gezeigt. Darf leer sein. */
      public String solution = "";
      /** Tipps; die ersten {@link #hintsShown} davon hat man schon gesehen. */
      public List<String> hints = new ArrayList<>();
      public int hintsShown;
      public List<Attempt> attempts = new ArrayList<>();
      public boolean solutionShown;
      public long createdAt;
      /** Gesetzt bei einer Sprechübung; dann gibt es statt Frage und Antwortfeld die drei Sprechstufen. */
      public SpeechTask speech;
      /** Wie weit die Sprechübung ist (Stufe 0–2, Wort in Stufe 0), damit sie nach dem Neuladen weitergeht. */
      public int speechStage;
      public int speechWord;

      public boolean isSpeaking() {
         return this.speech != null && !this.speech.words.isEmpty();
      }

      public int wrongAttempts() {
         int n = 0;
         for (Attempt a : this.attempts) {
            if (!a.correct) {
               n++;
            }
         }
         return n;
      }
   }

   /** Ein Satz zum Nachsprechen, in Wörter zerlegt. */
   public static final class SpeechTask {
      /** Sprachcode: zh, en, de, es, fr, it, pt, ja … */
      public String language = "zh";
      public String sentence = "";
      public String translation = "";
      public List<SpeechWord> words = new ArrayList<>();

      /** Die Lesung des ganzen Satzes (Pinyin …), aus den Wörtern. */
      public String reading() {
         List<String> parts = new ArrayList<>();
         for (SpeechWord w : this.words) {
            if (!w.reading.isBlank()) {
               parts.add(w.reading.strip());
            }
         }
         return String.join(" ", parts);
      }
   }

   public static final class SpeechWord {
      public String text = "";
      /** Aussprache-Hilfe: Pinyin mit Tönen, Kana … oder leer. */
      public String reading = "";
      public String meaning = "";

      public SpeechWord() {
      }

      public SpeechWord(String text, String reading, String meaning) {
         this.text = text;
         this.reading = reading;
         this.meaning = meaning;
      }
   }

   /** Eine gestellte Aufgabe, wie sie in den Anki-Export kommt. */
   public static final class HistoryEntry {
      /** aufgabe oder sprechen */
      public String kind = "aufgabe";
      public String title = "";
      public String question = "";
      public String solution = "";
      public String source = "";
      public String topic = "";
      public int wrongAttempts;
      public boolean solved;
      public long at;
      /** Nur bei Sprechübungen. */
      public String language = "";
      public String reading = "";
      public String translation = "";
   }

   public static final class Attempt {
      public String answer = "";
      public boolean correct;
      public String feedback = "";

      public Attempt() {
      }

      public Attempt(String answer, boolean correct, String feedback) {
         this.answer = answer;
         this.correct = correct;
         this.feedback = feedback;
      }
   }

   public static final class Stats {
      public int solved;
      public int solvedFirstTry;
      public int wrongAnswers;
      public int itemsLost;
      public int boostersEarned;
      public int boostersUsed;
      public int streak;
      public int bestStreak;
   }

   public static WorldData load(Path file) {
      WorldData data = null;
      if (Files.exists(file)) {
         try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data = GSON.fromJson(r, WorldData.class);
         } catch (RuntimeException | IOException e) {
            Log.warn("Konnte " + file + " nicht lesen: " + e);
            try {
               Files.copy(file, file.resolveSibling(file.getFileName() + ".kaputt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
         }
      }

      if (data == null) {
         data = new WorldData();
      }
      data.repair();
      data.file = file;
      return data;
   }

   private void repair() {
      if (this.sources == null) {
         this.sources = new ArrayList<>();
      }
      if (this.tasks == null) {
         this.tasks = new ArrayList<>();
      }
      if (this.boosters == null) {
         this.boosters = new LinkedHashMap<>();
      }
      if (this.stats == null) {
         this.stats = new Stats();
      }
      if (this.history == null) {
         this.history = new ArrayList<>();
      }
      this.boosters.values().removeIf(v -> v == null || v <= 0);
      if (this.active != null) {
         if (this.active.hints == null) {
            this.active.hints = new ArrayList<>();
         }
         if (this.active.attempts == null) {
            this.active.attempts = new ArrayList<>();
         }
         if (this.active.speech != null && (this.active.speech.words == null || this.active.speech.words.isEmpty())) {
            this.active.speech = null;
         }
         if (this.active.question == null || this.active.question.isBlank()) {
            this.active = null;
         }
      }
      // Eine PDF, die beim Beenden gerade gelesen wurde, wird beim nächsten Mal neu gelesen.
      for (Source s : this.sources) {
         if ("liest".equals(s.status)) {
            s.status = "wartet";
         }
      }
   }

   public synchronized void save() {
      if (this.file == null) {
         return;
      }
      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
         }
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         Log.warn("Konnte " + this.file + " nicht schreiben: " + e);
      }
   }

   public Path file() {
      return this.file;
   }

   /** Der Ordner {@code unilernen/} der Welt. */
   public Path folder() {
      return this.file == null ? null : this.file.getParent();
   }

   public Path pdfFolder() {
      return this.folder() == null ? null : this.folder().resolve("pdfs");
   }

   public Task task(String id) {
      for (Task t : this.tasks) {
         if (t.id.equals(id)) {
            return t;
         }
      }
      return null;
   }

   public Source source(String fileName) {
      for (Source s : this.sources) {
         if (s.file.equals(fileName)) {
            return s;
         }
      }
      return null;
   }

   public int boosterTotal() {
      int n = 0;
      for (int v : this.boosters.values()) {
         n += v;
      }
      return n;
   }

   public void addBooster(String id) {
      this.boosters.merge(id, 1, Integer::sum);
   }

   /** Nimmt einen Booster aus dem Booster-Inventar; false, wenn keiner da ist. */
   public boolean takeBooster(String id) {
      Integer n = this.boosters.get(id);
      if (n == null || n <= 0) {
         return false;
      }
      if (n == 1) {
         this.boosters.remove(id);
      } else {
         this.boosters.put(id, n - 1);
      }
      return true;
   }

   /** Ein Fach-Name für die Prompts, aus den gelesenen PDFs. */
   public String subjects() {
      List<String> names = new ArrayList<>();
      for (Source s : this.sources) {
         if (s.subject != null && !s.subject.isBlank() && !names.contains(s.subject.trim())) {
            names.add(s.subject.trim());
         }
      }
      return String.join(", ", names);
   }
}
