package dev.lernen.uni;

import dev.lernen.uni.core.StudyManager;
import dev.lernen.uni.core.WorldData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Das Lernmenü (Taste O): Stand, Statistik und der Weg zu Booster, Unterlagen und Einstellungen. */
public final class MenuScreen extends Screen {
   @Nullable
   private final Screen parent;
   private Button now;

   public MenuScreen(@Nullable Screen parent) {
      super(Component.literal("Lernen generell"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      int bw = 150;
      int x = this.width / 2 - bw - 3;
      int y = this.height - 100;
      StudyManager s = UniLernen.study;
      WorldData data = s.data();
      int boosters = data == null ? 0 : data.boosterTotal();
      this.addRenderableWidget(Button.builder(Component.literal("Booster-Inventar (" + boosters + ")"), b -> this.minecraft.gui.setScreen(new BoosterScreen(this)))
         .bounds(x, y, bw, 20)
         .build());
      this.addRenderableWidget(Button.builder(Component.literal("Unterlagen (PDFs)"), b -> this.minecraft.gui.setScreen(new MaterialScreen(this)))
         .bounds(x + bw + 6, y, bw, 20)
         .build());
      this.now = this.addRenderableWidget(Button.builder(Component.literal("Jetzt eine Aufgabe"), b -> {
         s.askNow();
         this.minecraft.gui.setScreen(null);
         UniLernen.message(this.minecraft, "§e[Lernen] §7Die Aufgabe kommt, sobald sie fertig ist …");
      }).bounds(x, y + 24, bw, 20).build());
      String lang = UniLernen.config.speechLanguage;
      Button speak = this.addRenderableWidget(Button.builder(Component.literal("Jetzt eine Sprechübung"), b -> {
         s.speakNow();
         this.minecraft.gui.setScreen(null);
         UniLernen.message(this.minecraft, "§e[Lernen] §7Die Sprechübung kommt, sobald der Satz fertig ist …");
      }).bounds(x + bw + 6, y + 24, bw, 20).build());
      String problem = UniLernen.speechProblem(lang);
      speak.active = data != null && s.active() == null && UniLernen.config.hasKey() && problem.isEmpty();
      if (!problem.isEmpty()) {
         speak.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(problem)));
      }
      Button anki = this.addRenderableWidget(Button.builder(Component.literal("Anki-Export"), b -> this.minecraft.gui.setScreen(new AnkiScreen(this)))
         .bounds(x, y + 48, bw, 20)
         .build());
      anki.active = data != null;
      this.addRenderableWidget(Button.builder(Component.literal("Einstellungen"), b -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
         .bounds(x + bw + 6, y + 48, bw, 20)
         .build());
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose()).bounds(this.width / 2 - 75, y + 72, 150, 20).build());
      this.now.active = data != null && s.blocker().replace("Automatische Aufgaben aus", "").isEmpty() && s.active() == null;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (UniLernen.keyMenu.matches(event)) {
         this.onClose();
         return true;
      }
      return super.keyPressed(event);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.centered(g, this.font, "Lernen generell", this.width / 2, 10, Ui.GOLD);
      StudyManager s = UniLernen.study;
      WorldData data = s.data();
      int x0 = this.width / 2 - 156;
      int x1 = this.width / 2 + 156;
      int top = 26;
      int bottom = this.height - 106;
      Ui.panel(g, x0, top, x1, bottom);
      List<String[]> rows = new ArrayList<>();
      if (data == null) {
         rows.add(new String[]{"Keine Welt geladen.", ""});
      } else {
         String blocker = s.blocker();
         String next;
         if (s.active() != null) {
            next = "jetzt offen!";
         } else if (!blocker.isEmpty()) {
            next = "§c" + blocker;
         } else if (s.preparing() && s.msUntilNext() == 0L) {
            next = "wird erstellt …";
         } else {
            next = "in " + Ui.clock(s.msUntilNext()) + " min Spielzeit";
         }
         rows.add(new String[]{"Nächste Aufgabe", next});
         if (!s.prepareError().isEmpty()) {
            rows.add(new String[]{"Letzter KI-Fehler", "§c" + s.prepareError()});
         }
         int done = 0;
         for (WorldData.Source src : data.sources) {
            if (src.done()) {
               done++;
            }
         }
         rows.add(new String[]{"Unterlagen", data.sources.size() + " PDFs (" + done + " gelesen), " + data.tasks.size() + " Aufgaben/Themen"});
         String subjects = data.subjects();
         if (!subjects.isEmpty()) {
            rows.add(new String[]{"Fächer", subjects});
         }
         rows.add(new String[]{"KI-Modell", UniLernen.config.model + (UniLernen.config.hasKey() ? "" : " §c(kein Key)")});
         String lang = UniLernen.config.speechLanguage;
         String problem = UniLernen.speechProblem(lang);
         var input = UniLernen.speechInput(lang);
         rows.add(new String[]{"Sprechen", dev.lernen.uni.core.Languages.name(lang) + ", " + UniLernen.config.speechMode
            + (input != null ? " · " + input.name() : " · §c" + problem)});
         WorldData.Stats st = data.stats;
         rows.add(new String[]{"Gelöst", st.solved + " (beim ersten Versuch: " + st.solvedFirstTry + ")"});
         rows.add(new String[]{"Falsche Antworten", st.wrongAnswers + " (verlorene Stapel: " + st.itemsLost + ")"});
         rows.add(new String[]{"Serie", st.streak + " richtig in Folge (Rekord " + st.bestStreak + ")"});
         rows.add(new String[]{"Booster", data.boosterTotal() + " im Inventar, " + st.boostersEarned + " verdient, " + st.boostersUsed + " eingesetzt"});
         rows.add(new String[]{"Spielzeit hier", Ui.clock(data.playMs) + " min"});
      }
      int y = top + 8;
      int labelW = 110;
      for (String[] row : rows) {
         if (y > bottom - 12) {
            break;
         }
         Ui.text(g, this.font, row[0], x0 + 8, y, Ui.GREY);
         var parts = this.font.split(Component.literal(row[1]), x1 - x0 - 16 - labelW);
         for (var part : parts) {
            if (y > bottom - 12) {
               break;
            }
            g.text(this.font, part, x0 + 8 + labelW, y, Ui.WHITE, true);
            y += 11;
         }
         if (parts.isEmpty()) {
            y += 11;
         }
         y += 2;
      }
      Ui.centered(g, this.font, "Tasten: " + UniLernen.keyName(UniLernen.keyMenu) + " = dieses Menü, " + UniLernen.keyName(UniLernen.keyBoosters) + " = Booster", this.width / 2, this.height - 10, Ui.DARK_GREY);
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
