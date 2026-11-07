package dev.lernen.uni;

import dev.lernen.uni.core.Languages;
import dev.lernen.uni.core.OpenRouter;
import dev.lernen.uni.core.SpeakingSession;
import dev.lernen.uni.core.SpeechCompare;
import dev.lernen.uni.core.SpeechInput;
import dev.lernen.uni.core.StudyManager;
import dev.lernen.uni.core.WorldData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;

/**
 * Die Sprechübung in drei Stufen. Oben steht der ganze Satz; jedes Wort wird nach dem Sprechen grün
 * (richtig) oder rot (falsch). Stufe 1 Wort für Wort, Stufe 2 der ganze Satz etwas schneller, Stufe 3 in
 * normalem Tempo. Das Spiel bleibt pausiert, bis alle drei Stufen geschafft sind.
 */
public final class SpeakScreen extends Screen {
   private static final int MARGIN = 16;
   /** SDL-Scancode der Leertaste. */
   private static final int SPACE = 44;

   private enum State { IDLE, STARTING, RECORDING, RECOGNIZING }

   private SpeakingSession session;
   private WorldData.Challenge challenge;
   private State state = State.IDLE;
   private SpeechInput input;
   private long recordStart;
   /** Ticks bis zur nächsten Aufnahme in Stufe 1 (nach einem richtigen Wort geht es von selbst weiter). */
   private int autoStartIn = -1;
   private int shownStage = -1;
   private Button talk;
   private Button skipWord;
   private Button skip;

   public SpeakScreen() {
      super(Component.literal("Sprechübung"));
   }

   private StudyManager study() {
      return UniLernen.study;
   }

   @Override
   protected void init() {
      WorldData.Challenge c = this.study().active();
      if (c != null && c.isSpeaking() && c != this.challenge) {
         this.challenge = c;
         this.session = new SpeakingSession(c, UniLernen.config.speechPassPercent);
      }
      int w = this.width - 2 * MARGIN;
      int bw = (w - 3 * 6) / 4;
      int y = this.height - 28;
      this.talk = this.addRenderableWidget(Button.builder(Component.literal(""), b -> this.toggle()).bounds(MARGIN, y, bw, 20).build());
      this.skipWord = this.addRenderableWidget(Button.builder(Component.literal("Wort überspringen"), b -> {
         this.session.skipWord();
         this.study().saveProgress();
      }).bounds(MARGIN + bw + 6, y, bw, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Pausemenü"), b -> {
         this.cancelRecording();
         this.minecraft.gui.setScreen(new PauseScreen(true));
      }).bounds(MARGIN + 2 * (bw + 6), y, bw, 20).build());
      this.skip = this.addRenderableWidget(Button.builder(Component.literal("Überspringen"), b -> {
         this.cancelRecording();
         this.study().skip();
      }).bounds(MARGIN + 3 * (bw + 6), y, bw, 20).build());
      this.updateButtons();
   }

   private void updateButtons() {
      if (this.talk == null || this.session == null) {
         return;
      }
      String label = switch (this.state) {
         case IDLE -> "Sprechen [Leertaste]";
         case STARTING -> "Mikrofon startet …";
         case RECORDING -> "■ Fertig [Leertaste]";
         case RECOGNIZING -> "Wird ausgewertet …";
      };
      this.talk.setMessage(Component.literal(label));
      this.talk.active = this.state == State.IDLE || this.state == State.RECORDING;
      this.skipWord.visible = this.session.canSkipWord();
      this.skipWord.active = this.state == State.IDLE;
      this.skip.visible = this.study().canSkip();
      this.skip.active = this.state == State.IDLE;
   }

   private void toggle() {
      if (this.state == State.IDLE) {
         this.startRecording();
      } else if (this.state == State.RECORDING) {
         this.stopRecording(false);
      }
   }

   private void startRecording() {
      if (this.session == null || this.session.done()) {
         return;
      }
      String language = this.session.task().language;
      SpeechInput in = UniLernen.speechInput(language);
      if (in == null) {
         this.study().speechError(UniLernen.speechProblem(language) + ".");
         return;
      }
      this.input = in;
      this.state = State.STARTING;
      this.autoStartIn = -1;
      in.start().whenComplete((ok, error) -> this.minecraft.execute(() -> {
         if (this.state != State.STARTING) {
            return;
         }
         if (error != null) {
            this.state = State.IDLE;
            this.study().speechError("Aufnahme startet nicht: " + OpenRouter.describe(error));
            return;
         }
         this.state = State.RECORDING;
         this.recordStart = System.currentTimeMillis();
      }));
   }

   private void stopRecording(boolean auto) {
      if (this.state != State.RECORDING || this.input == null) {
         return;
      }
      SpeechInput in = this.input;
      double measured = (System.currentTimeMillis() - this.recordStart) / 1000.0;
      this.state = State.RECOGNIZING;
      String language = this.session.task().language;
      in.stop(language).whenComplete((heard, error) -> this.minecraft.execute(() -> {
         if (this.state != State.RECOGNIZING) {
            return;
         }
         this.state = State.IDLE;
         if (error != null || heard == null) {
            this.study().speechError("Spracherkennung: " + (error == null ? "keine Antwort" : OpenRouter.describe(error)));
            return;
         }
         this.study().speechOk();
         double total = heard.seconds() > 0.0 ? heard.seconds() : measured;
         double speaking = Math.max(0.1, total - (auto ? in.trailingSilence() : 0.0));
         this.evaluate(heard, speaking);
      }));
   }

   private void evaluate(SpeechCompare.Heard heard, double speakingSeconds) {
      int stageBefore = this.session.stage();
      boolean failed = this.session.apply(heard, speakingSeconds);
      this.study().saveProgress();
      if (failed) {
         this.study().speechFailed(this.session.feedback());
      }
      if (this.session.lastGood()) {
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.6F));
      } else if (!heard.silent() && !heard.empty()) {
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, 0.4F));
      }
      if (this.session.done()) {
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.9F));
         this.study().finishSpeaking();
         this.minecraft.gui.setScreen(new TaskScreen());
         return;
      }
      // In Stufe 1 geht es nach einem richtigen Wort von selbst mit dem nächsten weiter.
      if (stageBefore == SpeakingSession.WORDS && this.session.stage() == SpeakingSession.WORDS && this.session.lastGood()) {
         this.autoStartIn = 8;
      }
   }

   private void cancelRecording() {
      if ((this.state == State.RECORDING || this.state == State.STARTING) && this.input != null) {
         this.input.cancel();
      }
      this.state = State.IDLE;
      this.autoStartIn = -1;
   }

   @Override
   public void tick() {
      StudyManager s = this.study();
      if (s.active() == null || !s.active().isSpeaking()) {
         if (s.active() == null && s.solved() == null) {
            this.minecraft.gui.setScreen(null);
         } else if (s.active() != null) {
            this.minecraft.gui.setScreen(new TaskScreen());
         }
         return;
      }
      if (this.session == null || s.active() != this.challenge) {
         this.rebuildWidgets();
         return;
      }
      if (this.state == State.RECORDING) {
         this.input.poll();
         double elapsed = (System.currentTimeMillis() - this.recordStart) / 1000.0;
         if (this.input.finished()) {
            this.stopRecording(true);
         } else if (elapsed >= this.session.maxRecordSeconds()) {
            this.stopRecording(false);
         }
      }
      if (this.autoStartIn > 0 && --this.autoStartIn == 0 && this.state == State.IDLE) {
         this.autoStartIn = -1;
         this.startRecording();
      }
      if (this.shownStage != this.session.stage()) {
         this.shownStage = this.session.stage();
      }
      this.updateButtons();
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (event.key() == SPACE) {
         this.toggle();
         return true;
      }
      return super.keyPressed(event);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      if (this.session == null) {
         return;
      }
      SpeakingSession s = this.session;
      WorldData.SpeechTask task = s.task();
      String lang = Languages.name(task.language) == null ? task.language : Languages.name(task.language);
      Ui.centered(g, this.font, "Sprechübung (" + lang + ") – " + SpeakingSession.stageName(s.stage()), this.width / 2, 6, Ui.GOLD);

      int x0 = MARGIN;
      int x1 = this.width - MARGIN;
      int top = 20;
      int panelBottom = this.drawWords(g, x0 + 8, top + 8, x1 - x0 - 16, false) + 6;
      Ui.panel(g, x0, top, x1, panelBottom);
      this.drawWords(g, x0 + 8, top + 8, x1 - x0 - 16, true);

      int y = panelBottom + 6;
      int textW = x1 - x0;
      List<Ui.Line> lines = Ui.lines();
      if (!task.translation.isBlank()) {
         Ui.wrap(this.font, lines, "Bedeutung: " + task.translation, textW, Ui.GREY);
      }
      if (s.stage() == SpeakingSession.WORDS && s.word() < s.words().size()) {
         WorldData.SpeechWord w = s.words().get(s.word());
         Ui.wrap(this.font, lines, "Jetzt: " + w.text + (w.reading.isBlank() ? "" : " (" + w.reading + ")") + (w.meaning.isBlank() ? "" : " = " + w.meaning), textW, Ui.YELLOW);
      }
      String howTo = switch (s.stage()) {
         case SpeakingSession.WORDS -> "Sprich nur das gelbe Wort, langsam und deutlich. Danach geht es von selbst mit dem nächsten weiter.";
         case SpeakingSession.SLOW -> String.format(Locale.ROOT,
            "Sprich jetzt den ganzen Satz in einem Zug – ruhig, aber ohne lange Pausen. Ziel: höchstens %.1f s.", s.timeLimit());
         default -> String.format(Locale.ROOT, "Jetzt in normalem Sprechtempo, flüssig wie im Gespräch. Ziel: höchstens %.1f s.", s.timeLimit());
      };
      Ui.wrap(this.font, lines, howTo, textW, Ui.WHITE);
      for (Ui.Line line : lines) {
         g.text(this.font, line.text(), x0, y, line.color(), true);
         y += 11;
      }
      y += 4;

      // Aufnahme-Anzeige
      if (this.state == State.RECORDING) {
         double elapsed = (System.currentTimeMillis() - this.recordStart) / 1000.0;
         double limit = s.stage() == SpeakingSession.WORDS ? s.maxRecordSeconds() : s.timeLimit();
         boolean blink = (System.currentTimeMillis() / 400L) % 2L == 0L;
         Ui.text(g, this.font, (blink ? "● " : "  ") + String.format(Locale.ROOT, "Aufnahme … %.1f s", elapsed), x0, y, Ui.RED);
         int barX = x0 + 110;
         int barW = Math.max(40, x1 - barX);
         float f = (float)Math.min(1.0, elapsed / Math.max(0.1, limit));
         g.fill(barX, y + 3, barX + barW, y + 5, 0x55FFFFFF);
         g.fill(barX, y + 3, barX + Math.round(barW * f), y + 5, f >= 1.0F ? 0xFFFF5555 : 0xFF7DFF8A);
         y += 13;
      } else if (this.state == State.RECOGNIZING) {
         Ui.text(g, this.font, "… wird ausgewertet", x0, y, Ui.YELLOW);
         y += 13;
      } else if (this.state == State.STARTING) {
         Ui.text(g, this.font, "Mikrofon startet …", x0, y, Ui.YELLOW);
         y += 13;
      }

      if (!s.feedback().isBlank()) {
         List<Ui.Line> fb = Ui.lines();
         Ui.wrap(this.font, fb, s.feedback(), textW, s.lastGood() ? Ui.GREEN : Ui.RED);
         for (Ui.Line line : fb) {
            if (y > this.height - 44) {
               break;
            }
            g.text(this.font, line.text(), x0, y, line.color(), true);
            y += 11;
         }
      }
      StudyManager st = this.study();
      if (st.statusError() && !st.status().isBlank() && y <= this.height - 44) {
         List<Ui.Line> err = Ui.lines();
         Ui.wrap(this.font, err, st.status(), textW, Ui.RED);
         for (Ui.Line line : err) {
            if (y > this.height - 44) {
               break;
            }
            g.text(this.font, line.text(), x0, y, line.color(), true);
            y += 11;
         }
      }

      SpeechInput in = UniLernen.speechInput(task.language);
      String engine = in == null ? "§c" + UniLernen.speechProblem(task.language) : "Erkennung: " + in.name();
      Ui.centered(g, this.font, engine, this.width / 2, this.height - 40, Ui.DARK_GREY);
   }

   /** Zeichnet den Satz Wort für Wort (groß, mit Lesung darunter); liefert die Unterkante. */
   private int drawWords(GuiGraphicsExtractor g, int x0, int y0, int maxW, boolean draw) {
      SpeakingSession s = this.session;
      boolean cjk = Languages.isCjk(s.task().language);
      List<WorldData.SpeechWord> words = s.words();
      float scale = 2.0F;
      int gap = cjk ? 6 : 8;
      // Erst messen, ob der Satz in zwei Zeilen passt; sonst kleiner.
      for (; scale > 1.0F; scale -= 0.25F) {
         if (this.rows(words, maxW, scale, gap).size() <= 2) {
            break;
         }
      }
      List<List<Integer>> rows = this.rows(words, maxW, scale, gap);
      int lineH = Math.round(9 * scale) + 12;
      int y = y0;
      if (!draw) {
         return y0 + rows.size() * lineH;
      }
      Matrix3x2fStack pose = g.pose();
      for (List<Integer> row : rows) {
         int rowW = 0;
         for (int i : row) {
            rowW += this.cellWidth(words.get(i), scale) + gap;
         }
         int x = x0 + (maxW - rowW + gap) / 2;
         for (int i : row) {
            WorldData.SpeechWord w = words.get(i);
            int cw = this.cellWidth(w, scale);
            int color = switch (s.mark(i)) {
               case SpeakingSession.MARK_OK -> Ui.GREEN;
               case SpeakingSession.MARK_WRONG -> Ui.RED;
               case SpeakingSession.MARK_SKIPPED -> 0xFFFFAA55;
               default -> Ui.WHITE;
            };
            boolean current = s.stage() == SpeakingSession.WORDS && i == s.word();
            if (current && s.mark(i) != SpeakingSession.MARK_WRONG) {
               color = Ui.YELLOW;
            }
            int textW = Math.round(this.font.width(w.text) * scale);
            pose.pushMatrix();
            pose.translate(x + (cw - textW) / 2.0F, y);
            pose.scale(scale, scale);
            g.text(this.font, Component.literal(w.text), 0, 0, color, true);
            pose.popMatrix();
            if (current) {
               g.fill(x, y + Math.round(9 * scale) + 1, x + cw, y + Math.round(9 * scale) + 2, 0xFFFFEE88);
            }
            if (!w.reading.isBlank()) {
               int rw = this.font.width(w.reading);
               g.text(this.font, Component.literal(w.reading), x + (cw - rw) / 2, y + Math.round(9 * scale) + 3, Ui.DARK_GREY, true);
            }
            x += cw + gap;
         }
         y += lineH;
      }
      return y;
   }

   private int cellWidth(WorldData.SpeechWord w, float scale) {
      return Math.max(Math.round(this.font.width(w.text) * scale), this.font.width(w.reading));
   }

   private List<List<Integer>> rows(List<WorldData.SpeechWord> words, int maxW, float scale, int gap) {
      List<List<Integer>> rows = new ArrayList<>();
      List<Integer> row = new ArrayList<>();
      int used = 0;
      for (int i = 0; i < words.size(); i++) {
         int cw = this.cellWidth(words.get(i), scale);
         if (!row.isEmpty() && used + cw > maxW) {
            rows.add(row);
            row = new ArrayList<>();
            used = 0;
         }
         row.add(i);
         used += cw + gap;
      }
      if (!row.isEmpty()) {
         rows.add(row);
      }
      return rows;
   }

   /** Escape schließt nicht: erst alle drei Stufen schaffen. */
   @Override
   public void onClose() {
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public void removed() {
      this.cancelRecording();
      super.removed();
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }
}
