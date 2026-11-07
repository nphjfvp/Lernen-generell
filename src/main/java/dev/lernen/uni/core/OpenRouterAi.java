package dev.lernen.uni.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** {@link Ai} über OpenRouter. */
public final class OpenRouterAi implements Ai {
   private final Supplier<UniConfig> config;
   private final OpenRouter client;

   public OpenRouterAi(Supplier<UniConfig> config) {
      this.config = config;
      this.client = new OpenRouter(config);
   }

   private CompletableFuture<JsonObject> ask(String prompt, double temperature, Duration timeout) {
      JsonArray messages = new JsonArray();
      messages.add(OpenRouter.textMessage("user", prompt));
      return this.client.chat(messages, false, temperature, timeout).thenApply(AiJson::object);
   }

   @Override
   public CompletableFuture<Extraction> extract(String fileName, byte[] pdf) {
      JsonArray messages = new JsonArray();
      messages.add(OpenRouter.pdfMessage(Prompts.extraction(fileName, this.config.get()), fileName, pdf));
      return this.client.chat(messages, true, 0.1, Duration.ofMinutes(6)).thenApply(reply -> parseExtraction(fileName, AiJson.object(reply)));
   }

   static Extraction parseExtraction(String fileName, JsonObject o) {
      String subject = AiJson.string(o, "fach", "subject", "kurs");
      String summary = AiJson.string(o, "zusammenfassung", "summary");
      List<WorldData.Task> tasks = new ArrayList<>();
      String base = fileName.replaceAll("\\.[Pp][Dd][Ff]$", "");
      int n = 0;
      for (JsonElement e : AiJson.array(o, "aufgaben", "tasks", "exercises")) {
         if (!e.isJsonObject()) {
            continue;
         }
         JsonObject a = e.getAsJsonObject();
         String text = MathText.toUnicode(AiJson.string(a, "text", "aufgabe", "task"));
         if (text.isBlank()) {
            continue;
         }
         WorldData.Task t = new WorldData.Task();
         t.id = base + "#a" + ++n;
         t.source = fileName;
         t.kind = "aufgabe";
         t.title = MathText.toUnicode(AiJson.string(a, "titel", "title"));
         t.topic = MathText.toUnicode(AiJson.string(a, "thema", "topic"));
         t.text = text;
         t.solution = MathText.toUnicode(AiJson.string(a, "loesung", "lösung", "solution"));
         tasks.add(t);
      }
      int m = 0;
      for (JsonElement e : AiJson.array(o, "stoff", "material", "inhalte")) {
         WorldData.Task t = new WorldData.Task();
         if (e.isJsonObject()) {
            JsonObject s = e.getAsJsonObject();
            t.topic = MathText.toUnicode(AiJson.string(s, "thema", "topic"));
            t.text = MathText.toUnicode(AiJson.string(s, "inhalt", "text", "content"));
         } else if (e.isJsonPrimitive()) {
            t.text = MathText.toUnicode(e.getAsString());
         }
         if (t.text.isBlank()) {
            continue;
         }
         t.id = base + "#s" + ++m;
         t.source = fileName;
         t.kind = "stoff";
         t.title = t.topic;
         tasks.add(t);
      }
      return new Extraction(subject, summary, tasks);
   }

   @Override
   public CompletableFuture<WorldData.Challenge> generate(WorldData.Task task, String style, String subjects) {
      return this.ask(Prompts.generation(task, style, this.config.get(), subjects), 0.8, Duration.ofMinutes(3)).thenApply(o -> {
         WorldData.Challenge c = new WorldData.Challenge();
         c.taskId = task.id;
         c.style = style;
         c.title = MathText.toUnicode(AiJson.string(o, "titel", "title"));
         c.question = MathText.toUnicode(AiJson.string(o, "aufgabe", "frage", "task", "question"));
         c.solution = MathText.toUnicode(AiJson.string(o, "loesung", "lösung", "solution", "antwort"));
         String tip = MathText.toUnicode(AiJson.string(o, "tipp", "hinweis", "hint"));
         if (c.question.isBlank()) {
            throw new IllegalArgumentException("Die KI hat keine Aufgabe geliefert.");
         }
         if (!tip.isBlank()) {
            // Der erste Tipp ist schon da, wird aber erst auf Knopfdruck gezeigt.
            c.hints.add(tip);
         }
         return c;
      });
   }

   @Override
   public CompletableFuture<Verdict> check(WorldData.Challenge challenge, String answer) {
      return this.ask(Prompts.check(challenge, answer, this.config.get()), 0.0, Duration.ofMinutes(2))
         .thenApply(o -> new Verdict(AiJson.bool(o, "richtig", "correct", "korrekt"), MathText.toUnicode(AiJson.string(o, "feedback", "begruendung", "kommentar"))));
   }

   @Override
   public CompletableFuture<String> hint(WorldData.Challenge challenge) {
      return this.ask(Prompts.hint(challenge, this.config.get()), 0.5, Duration.ofMinutes(2)).thenApply(o -> MathText.toUnicode(AiJson.string(o, "tipp", "hinweis", "hint")));
   }

   @Override
   public CompletableFuture<WorldData.Challenge> generateSpeaking(WorldData.Task task, String language, String subjects) {
      return this.ask(Prompts.speaking(task, language, this.config.get(), subjects), 0.8, Duration.ofMinutes(2))
         .thenApply(o -> parseSpeaking(o, task, language));
   }

   static WorldData.Challenge parseSpeaking(JsonObject o, WorldData.Task task, String language) {
      WorldData.SpeechTask s = new WorldData.SpeechTask();
      s.language = language;
      s.sentence = AiJson.string(o, "satz", "sentence");
      s.translation = AiJson.string(o, "uebersetzung", "übersetzung", "translation");
      for (JsonElement e : AiJson.array(o, "woerter", "wörter", "words")) {
         if (e.isJsonObject()) {
            JsonObject w = e.getAsJsonObject();
            String text = AiJson.string(w, "text", "wort", "word").replaceAll("[\\p{P}&&[^'\\-]]", "").strip();
            if (!text.isEmpty()) {
               s.words.add(new WorldData.SpeechWord(text, AiJson.string(w, "lesung", "reading", "pinyin"), AiJson.string(w, "bedeutung", "meaning")));
            }
         } else if (e.isJsonPrimitive() && !e.getAsString().isBlank()) {
            s.words.add(new WorldData.SpeechWord(e.getAsString().strip(), "", ""));
         }
      }
      if (s.words.isEmpty() && !s.sentence.isBlank() && !Languages.isCjk(language)) {
         for (String w : s.sentence.split("\\s+")) {
            String t = w.replaceAll("[\\p{P}&&[^'\\-]]", "");
            if (!t.isEmpty()) {
               s.words.add(new WorldData.SpeechWord(t, "", ""));
            }
         }
      }
      if (s.words.isEmpty()) {
         throw new IllegalArgumentException("Die KI hat keinen Satz zum Sprechen geliefert.");
      }
      if (s.sentence.isBlank()) {
         s.sentence = String.join(Languages.isCjk(language) ? "" : " ", s.words.stream().map(w -> w.text).toList());
      }
      WorldData.Challenge c = new WorldData.Challenge();
      c.taskId = task == null ? "" : task.id;
      c.style = "sprechen";
      c.title = "Sprechen: " + Languages.name(language);
      c.question = s.sentence;
      c.solution = s.translation;
      c.speech = s;
      return c;
   }

   @Override
   public CompletableFuture<SpeechCompare.Heard> transcribe(byte[] wav, String language) {
      JsonArray messages = new JsonArray();
      messages.add(OpenRouter.audioMessage(Prompts.transcription(language), wav));
      return this.client.chat(this.config.get().audioModelOrDefault(), messages, false, 0.0, Duration.ofMinutes(1)).thenApply(reply -> {
         JsonObject o = AiJson.object(reply);
         List<String> reading = new ArrayList<>();
         for (String syl : AiJson.string(o, "lesung", "pinyin", "reading").split("\\s+")) {
            if (!syl.isBlank()) {
               reading.add(syl);
            }
         }
         return new SpeechCompare.Heard(AiJson.string(o, "text"), reading, 0.0, false);
      });
   }

   @Override
   public CompletableFuture<String> solve(WorldData.Challenge challenge) {
      return this.ask(Prompts.solve(challenge, this.config.get()), 0.1, Duration.ofMinutes(3)).thenApply(o -> MathText.toUnicode(AiJson.string(o, "loesung", "lösung", "solution")));
   }
}
