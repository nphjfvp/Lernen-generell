package dev.lernen.uni;

import dev.lernen.uni.core.Languages;
import dev.lernen.uni.core.SpeechInput;
import dev.lernen.uni.core.UniConfig;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Einstellungen der Sprechübungen. */
public final class SpeechSettingsScreen extends Screen {
   @Nullable
   private final Screen parent;
   private EditBox helperUrl;
   private EditBox audioModel;

   public SpeechSettingsScreen(@Nullable Screen parent) {
      super(Component.literal("Sprechübungen"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      UniConfig c = UniLernen.config;
      int x = this.width / 2 - 155;
      int half = 152;
      int y = 30;
      this.addRenderableWidget(this.cycle(UniConfig.SPEECH_MODES, () -> c.speechMode, v -> c.speechMode = v, v -> "Sprechübungen: " + switch (v) {
         case "manchmal" -> "manchmal";
         case "oft" -> "oft";
         case "nur" -> "nur Sprechen";
         default -> "aus";
      }, "manchmal = etwa jede 4. Aufgabe, oft = jede 2., nur = nur noch Sprechübungen", x, y, half));
      this.addRenderableWidget(this.cycle(Languages.codes(), () -> c.speechLanguage, v -> c.speechLanguage = v,
         v -> "Sprache: " + Languages.name(v), "In welcher Sprache gesprochen wird", x + 158, y, half));
      y += 24;
      this.addRenderableWidget(this.cycle(UniConfig.SPEECH_ENGINES, () -> c.speechEngine, v -> c.speechEngine = v, v -> "Erkennung: " + v,
         "auto = Sprach-Helfer von Chinese-Minecraft, wenn er läuft, sonst Aufnahme im Spiel + OpenRouter-Audio-Modell. "
            + "helfer = nur der lokale Helfer (offline, kann zh/en/de/es/pt/ja). openrouter = Minecraft nimmt selbst auf.",
         x, y, half));
      this.addRenderableWidget(this.cycle(List.of("60", "70", "80", "90", "100"), () -> String.valueOf(c.speechPassPercent),
         v -> c.speechPassPercent = Integer.parseInt(v), v -> "Nötig: " + v + " % der Wörter", "So viele Wörter müssen in Stufe 2 und 3 erkannt werden",
         x + 158, y, half));
      y += 24;
      this.addRenderableWidget(this.cycle(List.of("aus", "an"), () -> c.speechPenalty ? "an" : "aus", v -> c.speechPenalty = v.equals("an"),
         v -> "Strafe bei Sprechfehlern: " + v, "an = ein nicht geschaffter Satz (Stufe 2/3) kostet wie eine falsche Antwort einen Gegenstand. "
            + "Standard aus, weil die Erkennung auch mal danebenliegt.", x, y, 310));
      y += 30;
      this.helperUrl = new EditBox(this.font, x + 100, y, 210, 18, Component.literal("Helfer"));
      this.helperUrl.setMaxLength(200);
      this.helperUrl.setValue(c.speechHelperUrl);
      this.addRenderableWidget(this.helperUrl);
      y += 22;
      this.audioModel = new EditBox(this.font, x + 100, y, 210, 18, Component.literal("Audio-Modell"));
      this.audioModel.setMaxLength(200);
      this.audioModel.setValue(c.audioModel);
      this.audioModel.setHint(Component.literal("leer = " + c.model));
      this.audioModel.setTooltip(Tooltip.create(Component.literal("Nur für Erkennung \"openrouter\": ein Modell, das Audio versteht, z. B. google/gemini-2.5-flash")));
      this.addRenderableWidget(this.audioModel);
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose()).bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
   }

   private Button cycle(List<String> values, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set,
      java.util.function.Function<String, String> label, String tip, int x, int y, int w) {
      Button b = Button.builder(Component.literal(label.apply(get.get())), button -> {
         int i = values.indexOf(get.get());
         String next = values.get((i + 1) % values.size());
         set.accept(next);
         button.setMessage(Component.literal(label.apply(next)));
      }).bounds(x, y, w, 20).build();
      b.setTooltip(Tooltip.create(Component.literal(tip)));
      return b;
   }

   private void apply() {
      UniConfig c = UniLernen.config;
      c.speechHelperUrl = this.helperUrl.getValue().strip();
      c.audioModel = this.audioModel.getValue().strip();
      UniLernen.saveConfig();
   }

   @Override
   public void tick() {
      UniLernen.helper.checkHealth(UniLernen.config.speechLanguage);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.centered(g, this.font, "Sprechübungen", this.width / 2, 10, Ui.GOLD);
      int x = this.width / 2 - 155;
      int y = 30 + 24 + 24 + 30;
      Ui.text(g, this.font, "Sprach-Helfer", x, y + 5, Ui.GREY);
      Ui.text(g, this.font, "Audio-Modell", x, y + 27, Ui.GREY);
      y += 52;
      String lang = UniLernen.config.speechLanguage;
      SpeechInput in = UniLernen.speechInput(lang);
      String helperState = UniLernen.helper.ready() ? "§aläuft" : UniLernen.helper.up() ? "§elädt das Modell …" : "§7läuft nicht";
      Ui.centered(g, this.font, "Sprach-Helfer: " + helperState, this.width / 2, y, Ui.WHITE);
      y += 12;
      Ui.centered(g, this.font, in == null ? "§c" + UniLernen.speechProblem(lang) : "§aBenutzt wird: " + in.name(), this.width / 2, y, Ui.WHITE);
      y += 16;
      String[] help = {
         "Ablauf: Stufe 1 Wort für Wort langsam, Stufe 2 ganzer Satz etwas schneller, Stufe 3 normales Tempo.",
         "Oben wird jedes Wort grün (richtig) oder rot (falsch). Leertaste = Aufnahme starten/beenden."
      };
      for (String h : help) {
         Ui.centered(g, this.font, h, this.width / 2, y, Ui.DARK_GREY);
         y += 11;
      }
   }

   @Override
   public void removed() {
      this.apply();
      super.removed();
   }

   @Override
   public void onClose() {
      this.apply();
      this.minecraft.gui.setScreen(this.parent);
   }
}
