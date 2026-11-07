package dev.lernen.uni;

import dev.lernen.uni.core.Boosters;
import dev.lernen.uni.core.StudyManager;
import dev.lernen.uni.core.UniConfig;
import dev.lernen.uni.core.WorldData;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Das Aufgabenfenster. Es pausiert das Spiel und lässt sich nicht schließen, bis die Aufgabe richtig
 * gelöst ist. Danach zeigt es "Richtig!" und den gewonnenen Booster.
 */
public final class TaskScreen extends Screen {
   private static final int MARGIN = 16;
   /** SDL-Scancodes der Eingabetasten. */
   private static final int ENTER = 40;
   private static final int KEYPAD_ENTER = 88;

   private EditBox answer;
   private Button submit;
   private Button hint;
   private Button solution;
   private Button skip;
   private int scroll;
   private int maxScroll;
   private boolean wasBusy;
   private int lastAttempts = -1;
   private boolean solvedView;

   public TaskScreen() {
      super(Component.literal("Aufgabe"));
   }

   private StudyManager study() {
      return UniLernen.study;
   }

   @Override
   protected void init() {
      StudyManager s = this.study();
      this.solvedView = s.active() == null && s.solved() != null;
      int w = this.width - 2 * MARGIN;
      int rowY = this.height - 48;
      if (this.solvedView) {
         int bw = Math.min(150, (w - 6) / 2);
         this.addRenderableWidget(Button.builder(Component.literal("Weiterspielen"), b -> this.close()).bounds(this.width / 2 - bw - 3, rowY, bw, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Booster ansehen"), b -> {
            s.clearSolved();
            this.minecraft.gui.setScreen(new BoosterScreen(null));
         }).bounds(this.width / 2 + 3, rowY, bw, 20).build());
         return;
      }

      int submitW = 100;
      String old = this.answer != null ? this.answer.getValue() : UniLernen.draft;
      this.answer = new EditBox(this.font, MARGIN, this.height - 74, w - submitW - 6, 20, Component.literal("Antwort"));
      this.answer.setMaxLength(3000);
      this.answer.setValue(old == null ? "" : old);
      this.answer.setHint(Component.literal("Deine Antwort … (Enter = abschicken)"));
      this.answer.setResponder(v -> UniLernen.draft = v);
      this.addRenderableWidget(this.answer);
      this.setInitialFocus(this.answer);
      this.submit = this.addRenderableWidget(Button.builder(Component.literal("Abschicken"), b -> this.submit())
         .bounds(this.width - MARGIN - submitW, this.height - 74, submitW, 20)
         .build());

      int bw = (w - 3 * 6) / 4;
      this.hint = this.addRenderableWidget(Button.builder(Component.literal("Tipp"), b -> s.requestHint()).bounds(MARGIN, rowY, bw, 20).build());
      this.solution = this.addRenderableWidget(Button.builder(Component.literal("Lösung zeigen"), b -> s.requestSolution())
         .bounds(MARGIN + (bw + 6), rowY, bw, 20)
         .build());
      this.addRenderableWidget(Button.builder(Component.literal("Pausemenü"), b -> this.minecraft.gui.setScreen(new PauseScreen(true)))
         .bounds(MARGIN + 2 * (bw + 6), rowY, bw, 20)
         .build());
      this.skip = this.addRenderableWidget(Button.builder(Component.literal("Überspringen"), b -> s.skip()).bounds(MARGIN + 3 * (bw + 6), rowY, bw, 20).build());
      this.updateButtons();
   }

   private void updateButtons() {
      StudyManager s = this.study();
      WorldData.Challenge c = s.active();
      if (c == null || this.submit == null) {
         return;
      }
      boolean busy = s.busy();
      this.submit.active = !busy;
      this.answer.setEditable(!busy);
      this.hint.active = !busy;
      this.hint.setMessage(Component.literal(c.hintsShown == 0 ? "Tipp" : "Noch ein Tipp"));
      int after = UniLernen.config.solutionAfterWrong;
      this.solution.active = !busy && s.canShowSolution();
      if (c.solutionShown) {
         this.solution.setMessage(Component.literal("Lösung steht oben"));
      } else if (after <= 0) {
         this.solution.setMessage(Component.literal("Keine Lösung"));
      } else if (!s.canShowSolution()) {
         this.solution.setMessage(Component.literal("Lösung (" + c.wrongAttempts() + "/" + after + " Fehler)"));
      } else {
         this.solution.setMessage(Component.literal("Lösung zeigen"));
      }
      this.skip.visible = s.canSkip();
      this.skip.active = s.canSkip();
   }

   private void submit() {
      StudyManager s = this.study();
      if (s.active() != null && !s.busy()) {
         s.submit(this.answer.getValue());
      }
   }

   @Override
   public void tick() {
      StudyManager s = this.study();
      if (s.active() != null && s.active().isSpeaking()) {
         this.minecraft.gui.setScreen(new SpeakScreen());
         return;
      }
      if (!this.solvedView && s.active() == null) {
         if (s.solved() != null) {
            // Gerade richtig gelöst: Ansicht wechseln.
            UniLernen.draft = "";
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.9F));
            this.rebuildWidgets();
         } else {
            // Übersprungen oder Welt verlassen.
            UniLernen.draft = "";
            this.minecraft.gui.setScreen(null);
         }
         return;
      }
      WorldData.Challenge c = s.active();
      if (c != null) {
         if (c.attempts.size() != this.lastAttempts) {
            if (this.lastAttempts >= 0 && !c.attempts.isEmpty() && !c.attempts.getLast().correct) {
               this.answer.setValue("");
               UniLernen.draft = "";
               this.scroll = Integer.MAX_VALUE;
            }
            this.lastAttempts = c.attempts.size();
         }
         if (this.wasBusy && !s.busy()) {
            this.setFocused(this.answer);
         }
         this.wasBusy = s.busy();
         this.updateButtons();
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      int k = event.key();
      if (!this.solvedView && (k == ENTER || k == KEYPAD_ENTER)) {
         this.submit();
         return true;
      }
      return super.keyPressed(event);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double dx, double dy) {
      this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int)Math.signum(dy) * 3));
      return true;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      StudyManager s = this.study();
      WorldData.Challenge c = this.solvedView ? s.solved() : s.active();
      if (c == null) {
         return;
      }

      String kind = switch (c.style) {
         case "original" -> "Aufgabe aus deinen Unterlagen";
         case "kurz" -> "Kurzfrage";
         case "sprechen" -> "Sprechübung";
         default -> "Übungsaufgabe";
      };
      String head = this.solvedView ? "✔ Richtig gelöst!" : "✎ " + kind + (c.title.isBlank() ? "" : " – " + c.title);
      Ui.centered(g, this.font, head, this.width / 2, 8, this.solvedView ? Ui.GREEN : Ui.GOLD);
      if (!this.solvedView) {
         Ui.centered(g, this.font, "Das Spiel geht erst weiter, wenn die Aufgabe richtig gelöst ist.", this.width / 2, 19, Ui.DARK_GREY);
      }

      int x0 = MARGIN;
      int x1 = this.width - MARGIN;
      int top = 32;
      int bottom = this.solvedView ? this.height - 58 : this.height - 92;
      Ui.panel(g, x0, top, x1, bottom);
      int textW = x1 - x0 - 16;
      List<Ui.Line> lines = Ui.lines();
      Ui.wrap(this.font, lines, c.question, textW, Ui.WHITE);

      for (int i = 0; i < Math.min(c.hintsShown, c.hints.size()); i++) {
         lines.add(new Ui.Line(net.minecraft.util.FormattedCharSequence.EMPTY, Ui.WHITE));
         Ui.wrap(this.font, lines, "➤ Tipp " + (i + 1) + ": " + c.hints.get(i), textW, Ui.YELLOW);
      }
      if (c.solutionShown && !c.solution.isBlank()) {
         lines.add(new Ui.Line(net.minecraft.util.FormattedCharSequence.EMPTY, Ui.WHITE));
         Ui.wrap(this.font, lines, "Lösung:", textW, Ui.BLUE);
         Ui.wrap(this.font, lines, c.solution, textW, Ui.BLUE);
      }
      if (!c.attempts.isEmpty()) {
         lines.add(new Ui.Line(net.minecraft.util.FormattedCharSequence.EMPTY, Ui.WHITE));
         int from = Math.max(0, c.attempts.size() - 4);
         if (from > 0) {
            Ui.wrap(this.font, lines, "… " + from + " frühere Versuche", textW, Ui.DARK_GREY);
         }
         for (int i = from; i < c.attempts.size(); i++) {
            WorldData.Attempt at = c.attempts.get(i);
            Ui.wrap(this.font, lines, (at.correct ? "✔ " : "✘ ") + "Deine Antwort: " + at.answer, textW, at.correct ? Ui.GREEN : Ui.RED);
            if (!at.feedback.isBlank()) {
               Ui.wrap(this.font, lines, "   " + at.feedback, textW, Ui.GREY);
            }
         }
      }
      if (this.solvedView) {
         lines.add(new Ui.Line(net.minecraft.util.FormattedCharSequence.EMPTY, Ui.WHITE));
         Boosters.Booster b = Boosters.byId(s.solvedBooster());
         if (b != null) {
            Ui.wrap(this.font, lines, "⚡ Gewonnen: " + b.name() + " – " + b.description() + ". Liegt im Booster-Inventar (" + UniLernen.keyName(UniLernen.keyBoosters) + ").", textW, Ui.GOLD);
         } else {
            Ui.wrap(this.font, lines, "Mit aufgedeckter Lösung gibt es keinen Booster. Nächstes Mal klappt's!", textW, Ui.GREY);
         }
         if (c.isSpeaking()) {
            lines.add(new Ui.Line(net.minecraft.util.FormattedCharSequence.EMPTY, Ui.WHITE));
            if (!c.speech.reading().isBlank()) {
               Ui.wrap(this.font, lines, "Aussprache: " + c.speech.reading(), textW, Ui.BLUE);
            }
            Ui.wrap(this.font, lines, "Bedeutung: " + c.speech.translation, textW, Ui.BLUE);
         } else if (!c.solution.isBlank() && !c.solutionShown) {
            lines.add(new Ui.Line(net.minecraft.util.FormattedCharSequence.EMPTY, Ui.WHITE));
            Ui.wrap(this.font, lines, "Musterlösung zum Vergleich:", textW, Ui.BLUE);
            Ui.wrap(this.font, lines, c.solution, textW, Ui.BLUE);
         }
      }

      int lineH = this.font.lineHeight + 2;
      int visible = Math.max(1, (bottom - top - 12) / lineH);
      this.maxScroll = Math.max(0, lines.size() - visible);
      this.scroll = Math.min(this.scroll, this.maxScroll);
      int y = top + 6;
      for (int i = this.scroll; i < Math.min(lines.size(), this.scroll + visible); i++) {
         Ui.Line line = lines.get(i);
         g.text(this.font, line.text(), x0 + 8, y, line.color(), true);
         y += lineH;
      }
      if (this.maxScroll > 0) {
         String more = (this.scroll > 0 ? "▲ " : "") + (this.scroll < this.maxScroll ? "▼ mehr (Mausrad)" : "");
         Ui.text(g, this.font, more, x1 - 8 - this.font.width(more), bottom - 11, Ui.DARK_GREY);
      }

      if (!this.solvedView) {
         String status = s.status();
         if (!status.isBlank()) {
            String shown = status.length() > 300 ? status.substring(0, 300) + "…" : status;
            List<net.minecraft.util.FormattedCharSequence> st = this.font.split(Component.literal(shown), x1 - x0);
            int sy = this.height - 88;
            if (!st.isEmpty()) {
               g.text(this.font, st.getFirst(), x0, sy, s.statusError() ? Ui.RED : s.busy() ? Ui.YELLOW : Ui.GREEN, true);
            }
         }
         UniConfig cfg = UniLernen.config;
         String foot = "Falsch = " + switch (cfg.penalty) {
            case "einzeln" -> "1 zufälliger Gegenstand weg";
            case "keine" -> "keine Strafe";
            default -> "1 zufälliger Stapel weg";
         } + " · Richtig = Booster" + (this.maxScroll > 0 ? " · Mausrad: scrollen" : "");
         Ui.centered(g, this.font, foot, this.width / 2, this.height - 20, Ui.DARK_GREY);
      }
   }

   private void close() {
      this.study().clearSolved();
      UniLernen.draft = "";
      this.minecraft.gui.setScreen(null);
   }

   /** Escape schließt nur, wenn die Aufgabe gelöst ist. */
   @Override
   public void onClose() {
      if (this.study().active() == null) {
         this.close();
      }
   }

   public boolean shouldCloseOnEsc() {
      return this.study().active() == null;
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }
}
