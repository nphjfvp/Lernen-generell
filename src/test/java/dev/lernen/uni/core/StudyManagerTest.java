package dev.lernen.uni.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyManagerTest {
   @TempDir
   Path dir;

   final UniConfig config = new UniConfig();
   final FakeAi ai = new FakeAi();
   final FakeGame game = new FakeGame();
   long now = 1_000_000L;
   StudyManager study;
   WorldData data;

   static final class FakeAi implements Ai {
      boolean correct;
      boolean failCheck;
      int generated;
      int extracted;

      @Override
      public CompletableFuture<Extraction> extract(String fileName, byte[] pdf) {
         this.extracted++;
         WorldData.Task t = new WorldData.Task();
         t.id = "x#a1";
         t.source = fileName;
         t.text = "Berechne 3·4.";
         t.solution = "12";
         return CompletableFuture.completedFuture(new Extraction("Mathe", "Rechnen", List.of(t)));
      }

      @Override
      public CompletableFuture<WorldData.Challenge> generate(WorldData.Task task, String style, String subjects) {
         this.generated++;
         WorldData.Challenge c = new WorldData.Challenge();
         c.taskId = task.id;
         c.style = style;
         c.question = "Berechne 5·6.";
         c.solution = "30";
         c.hints.add("Fünfmal sechs.");
         return CompletableFuture.completedFuture(c);
      }

      @Override
      public CompletableFuture<Verdict> check(WorldData.Challenge challenge, String answer) {
         if (this.failCheck) {
            return CompletableFuture.failedFuture(new OpenRouter.ApiException(503, "weg"));
         }
         return CompletableFuture.completedFuture(new Verdict(this.correct, this.correct ? "Stimmt." : "Nein."));
      }

      @Override
      public CompletableFuture<String> hint(WorldData.Challenge challenge) {
         return CompletableFuture.completedFuture("Noch ein Tipp");
      }

      @Override
      public CompletableFuture<String> solve(WorldData.Challenge challenge) {
         return CompletableFuture.completedFuture("Die Lösung");
      }

      @Override
      public CompletableFuture<WorldData.Challenge> generateSpeaking(WorldData.Task task, String language, String subjects) {
         return CompletableFuture.completedFuture(SpeakingTest.chinese());
      }

      @Override
      public CompletableFuture<SpeechCompare.Heard> transcribe(byte[] wav, String language) {
         return CompletableFuture.completedFuture(new SpeechCompare.Heard("我", List.of("wǒ"), 1.0, false));
      }
   }

   static final class FakeGame implements StudyManager.Game {
      boolean open;
      int opened;
      List<String> penalties = new ArrayList<>();
      List<String> messages = new ArrayList<>();

      @Override
      public void openChallenge() {
         this.open = true;
         this.opened++;
      }

      @Override
      public boolean challengeOpen() {
         return this.open;
      }

      @Override
      public void penalize(String mode) {
         this.penalties.add(mode);
      }

      @Override
      public void message(String text) {
         this.messages.add(text);
      }
   }

   @BeforeEach
   void setUp() {
      this.config.apiKey = "sk-or-test";
      this.config.intervalMinutes = 5.0F;
      this.config.jitterPercent = 0;
      this.config.style = "aehnlich";
      this.study = new StudyManager(() -> this.config, this.ai, Runnable::run, new Random(1), () -> this.now);
      this.data = WorldData.load(this.dir.resolve("unilernen/welt.json"));
      WorldData.Task t = new WorldData.Task();
      t.id = "blatt#a1";
      t.source = "blatt.pdf";
      t.text = "Berechne 2·3.";
      t.solution = "6";
      this.data.tasks.add(t);
      this.study.attach(this.data, this.game);
   }

   void play(long ms) {
      for (long t = 0; t < ms; t += 1000L) {
         this.now += 1000L;
         this.study.tick(1000L, !this.game.open, !this.game.open);
      }
   }

   @Test
   void asksAfterIntervalAndPreparesEarly() {
      this.play(60_000L);
      assertNull(this.study.active());
      assertTrue(this.study.hasPrepared(), "die nächste Aufgabe wird vorab erstellt");
      this.play(4 * 60_000L + 1000L);
      assertNotNull(this.study.active());
      assertEquals(1, this.game.opened);
      assertEquals("Berechne 5·6.", this.study.active().question);
   }

   @Test
   void wrongPenalizesRightRewards() {
      this.study.askNow();
      this.play(1000L);
      WorldData.Challenge c = this.study.active();
      assertNotNull(c);

      this.study.submit("31");
      assertEquals(List.of("stapel"), this.game.penalties);
      assertTrue(this.study.statusError());
      assertNotNull(this.study.active(), "falsch: die Aufgabe bleibt offen");

      long before = this.data.playMs;
      this.play(10_000L);
      assertEquals(before, this.data.playMs, "solange die Aufgabe offen ist, läuft die Spielzeit nicht");

      this.ai.correct = true;
      this.study.submit("30");
      assertNull(this.study.active());
      assertEquals(1, this.data.boosterTotal());
      assertEquals(1, this.data.stats.solved);
      assertEquals(0, this.data.stats.solvedFirstTry);
      assertEquals(1, this.data.task("blatt#a1").wrong);
      assertEquals(1, this.data.task("blatt#a1").correct);
      assertEquals(c, this.study.solved());
   }

   @Test
   void emptyAnswerDoesNotCount() {
      this.study.askNow();
      this.play(1000L);
      this.study.submit("   ");
      assertTrue(this.game.penalties.isEmpty());
      assertTrue(this.study.active().attempts.isEmpty());
   }

   @Test
   void hintsAndSolution() {
      this.config.solutionAfterWrong = 2;
      this.config.penalty = "keine";
      this.study.askNow();
      this.play(1000L);
      WorldData.Challenge c = this.study.active();
      this.study.requestHint();
      assertEquals(1, c.hintsShown);
      this.study.requestHint();
      assertEquals(2, c.hintsShown);
      assertEquals("Noch ein Tipp", c.hints.get(1));

      assertFalse(this.study.canShowSolution());
      this.study.submit("1");
      this.study.submit("2");
      assertTrue(this.game.penalties.isEmpty());
      assertTrue(this.study.canShowSolution());
      this.study.requestSolution();
      assertTrue(c.solutionShown);

      this.ai.correct = true;
      this.study.submit("30");
      assertNull(this.study.active());
      assertEquals(0, this.data.boosterTotal(), "mit gezeigter Lösung gibt es keinen Booster");
   }

   @Test
   void skipOnlyAfterRepeatedApiErrors() {
      this.study.askNow();
      this.play(1000L);
      this.ai.failCheck = true;
      for (int i = 0; i < StudyManager.SKIP_AFTER_ERRORS - 1; i++) {
         this.study.submit("30");
      }
      assertFalse(this.study.canSkip());
      assertTrue(this.game.penalties.isEmpty(), "KI-Fehler werden nicht bestraft");
      this.study.submit("30");
      assertTrue(this.study.canSkip());
      this.study.skip();
      assertNull(this.study.active());
   }

   @Test
   void openTaskSurvivesRejoin() {
      this.study.askNow();
      this.play(1000L);
      this.study.detach();
      WorldData again = WorldData.load(this.dir.resolve("unilernen/welt.json"));
      assertNotNull(again.active);
      FakeGame g2 = new FakeGame();
      this.study.attach(again, g2);
      this.study.tick(50L, true, true);
      assertEquals(1, g2.opened, "die offene Aufgabe kommt beim Betreten sofort wieder");
   }

   @Test
   void originalStyleNeedsNoAi() {
      this.config.style = "original";
      this.study.askNow();
      this.play(1000L);
      assertEquals("Berechne 2·3.", this.study.active().question);
      assertEquals(0, this.ai.generated);
   }

   @Test
   void noKeyNoTasks() {
      this.config.apiKey = "";
      this.study.askNow();
      this.play(10_000L);
      assertNull(this.study.active());
      assertEquals("Kein OpenRouter-Key", this.study.blocker());
   }

   @Test
   void importsAndReadsPdf() throws Exception {
      Path pdf = this.dir.resolve("Blatt 2.pdf");
      Files.write(pdf, "%PDF-1.4\n...".getBytes(StandardCharsets.ISO_8859_1));
      String msg = this.study.materials().importPdf(pdf);
      assertTrue(msg.contains("Hochgeladen"), msg);
      assertTrue(Files.exists(this.data.pdfFolder().resolve("Blatt 2.pdf")));
      this.play(1000L);
      assertEquals(1, this.ai.extracted);
      WorldData.Source s = this.data.source("Blatt 2.pdf");
      assertEquals("fertig", s.status);
      assertEquals("Mathe", s.subject);
      assertEquals(2, this.data.tasks.size());

      Path fake = this.dir.resolve("kein.pdf");
      Files.writeString(fake, "hallo");
      assertTrue(this.study.materials().importPdf(fake).contains("Keine PDF"));

      this.study.materials().remove("Blatt 2.pdf");
      assertEquals(1, this.data.tasks.size());
      assertFalse(Files.exists(this.data.pdfFolder().resolve("Blatt 2.pdf")));
   }

   @Test
   void speakingChallengeFlow() {
      this.config.speechMode = "nur";
      this.study.setSpeechAvailable(() -> true);
      this.study.askNow();
      this.play(1000L);
      WorldData.Challenge c = this.study.active();
      assertTrue(c.isSpeaking());
      this.study.speechFailed("zu langsam");
      assertTrue(this.game.penalties.isEmpty(), "Sprechfehler kosten standardmäßig nichts");
      this.config.speechPenalty = true;
      this.study.speechFailed("zu langsam");
      assertEquals(List.of("stapel"), this.game.penalties);
      this.study.finishSpeaking();
      assertNull(this.study.active());
      assertEquals(1, this.data.boosterTotal());
      assertEquals("sprechen", this.data.history.getLast().kind);
      assertEquals("wǒ zài wā ní tǔ", this.data.history.getLast().reading);
   }

   @Test
   void noSpeakingWithoutRecognizer() {
      this.config.speechMode = "nur";
      this.study.askNow();
      this.play(1000L);
      assertFalse(this.study.active().isSpeaking(), "ohne Spracherkennung gibt es normale Aufgaben");
      this.study.speakNow();
   }

   @Test
   void boosterInventory() {
      this.data.addBooster("feuerfest");
      this.data.addBooster("feuerfest");
      assertEquals(2, this.data.boosterTotal());
      assertTrue(this.data.takeBooster("feuerfest"));
      assertTrue(this.data.takeBooster("feuerfest"));
      assertFalse(this.data.takeBooster("feuerfest"));
      for (Boosters.Booster b : Boosters.ALL) {
         assertEquals(b, Boosters.byId(b.id()));
      }
   }
}
