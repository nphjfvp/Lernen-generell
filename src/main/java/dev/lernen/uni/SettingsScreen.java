package dev.lernen.uni;

import com.google.gson.JsonArray;
import dev.lernen.uni.core.OpenRouter;
import dev.lernen.uni.core.UniConfig;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Einstellungen: OpenRouter-Key, Modell, wie oft und welche Aufgaben kommen, Strafe. */
public final class SettingsScreen extends Screen {
   private static final int LABEL_W = 92;
   private static final int ROW = 22;
   @Nullable
   private final Screen parent;
   private EditBox key;
   private EditBox model;
   private EditBox language;
   private EditBox wishes;
   private String testResult = "";
   private boolean testing;
   private int labelsTop;
   private int languageY;
   private int buttonsY;

   public SettingsScreen(@Nullable Screen parent) {
      super(Component.literal("Lernen generell – Einstellungen"));
      this.parent = parent;
   }

   private int left() {
      return this.width / 2 - 155;
   }

   @Override
   protected void init() {
      UniConfig c = UniLernen.config;
      int x = this.left();
      int fieldX = x + LABEL_W;
      int fieldW = 310 - LABEL_W;
      int y = 24;
      this.labelsTop = y;

      String keyValue = this.key != null ? this.key.getValue() : "";
      this.key = new EditBox(this.font, fieldX, y, fieldW, 18, Component.literal("API-Key"));
      this.key.setMaxLength(300);
      this.key.setValue(keyValue);
      this.key.setHint(Component.literal(c.hasKey() ? "gespeichert: " + c.maskedKey() : "sk-or-… hier einfügen (Strg+V)"));
      this.addRenderableWidget(this.key);
      y += 20;

      String modelValue = this.model != null ? this.model.getValue() : c.model;
      this.model = new EditBox(this.font, fieldX, y, fieldW, 18, Component.literal("Modell"));
      this.model.setMaxLength(200);
      this.model.setValue(modelValue);
      this.model.setTooltip(Tooltip.create(Component.literal(
         "Modell-ID von openrouter.ai/models, z. B. google/gemini-2.5-flash, anthropic/claude-sonnet-4.5 oder openai/gpt-5-mini. "
            + "Es muss PDFs lesen können (sonst PDF-Leser auf pdf-text oder mistral-ocr stellen).")));
      this.addRenderableWidget(this.model);
      y += 22;

      int half = 152;
      this.addRenderableWidget(Button.builder(Component.literal("−"), b -> this.changeInterval(-1)).bounds(x, y, 20, 20).build());
      this.addRenderableWidget(this.label(() -> "Alle " + format(c.intervalMinutes) + " min eine Aufgabe", x + 22, y, half - 44));
      this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.changeInterval(1)).bounds(x + half - 20, y, 20, 20).build());
      this.addRenderableWidget(this.cycle(UniConfig.STYLES, () -> c.style, v -> c.style = v, v -> "Aufgaben: " + switch (v) {
         case "original" -> "aus dem PDF";
         case "aehnlich" -> "ähnliche (KI)";
         case "kurz" -> "Kurzfragen";
         default -> "gemischt";
      }, "gemischt = mal Original-Aufgaben aus dem PDF, mal ähnliche mit neuen Zahlen, mal kurze Verständnisfragen", x + 158, y, half));
      y += ROW + 2;

      this.addRenderableWidget(this.cycle(UniConfig.PENALTIES, () -> c.penalty, v -> c.penalty = v, v -> "Falsch: " + switch (v) {
         case "einzeln" -> "1 Gegenstand weg";
         case "keine" -> "keine Strafe";
         default -> "1 Stapel weg";
      }, "Was bei einer falschen Antwort aus dem Inventar gelöscht wird (zufällig)", x, y, half));
      this.addRenderableWidget(this.cycle(List.of("0", "1", "2", "3", "4", "5", "7", "10"), () -> String.valueOf(c.solutionAfterWrong),
         v -> c.solutionAfterWrong = Integer.parseInt(v), v -> v.equals("0") ? "Lösung zeigen: nie" : "Lösung nach " + v + " Fehlern",
         "Ab so vielen falschen Versuchen darf man sich die Lösung zeigen lassen (dann ohne Booster)", x + 158, y, half));
      y += ROW + 2;

      this.addRenderableWidget(this.cycle(UniConfig.PDF_ENGINES, () -> c.pdfEngine, v -> c.pdfEngine = v, v -> "PDF-Leser: " + v,
         "auto = OpenRouter entscheidet · pdf-text = kostenlos, nur Text-PDFs · mistral-ocr = auch Scans und Handschrift (kostet etwas) · native = das Modell liest selbst",
         x, y, half));
      this.addRenderableWidget(this.cycle(UniConfig.HUD_POSITIONS, () -> c.hudPosition, v -> c.hudPosition = v,
         v -> "Anzeige: " + v.replace("_", " "), "Countdown bis zur nächsten Aufgabe und Zahl der Booster", x + 158, y, half));
      y += ROW + 2;

      this.addRenderableWidget(this.cycle(List.of("an", "aus"), () -> c.enabled ? "an" : "aus", v -> c.enabled = v.equals("an"),
         v -> "Automatische Aufgaben: " + v, "aus = Aufgaben nur über \"Jetzt eine Aufgabe\" im Lernmenü", x, y, half));
      this.addRenderableWidget(this.cycle(List.of("0", "10", "20", "30", "50"), () -> String.valueOf(c.jitterPercent),
         v -> c.jitterPercent = Integer.parseInt(v), v -> "Zufall: ±" + v + " %", "Wie stark der Abstand zwischen den Aufgaben schwankt", x + 158, y, half));
      y += ROW + 2;
      this.languageY = y;

      String langValue = this.language != null ? this.language.getValue() : c.language;
      this.language = new EditBox(this.font, x + LABEL_W, y, fieldW, 18, Component.literal("Sprache"));
      this.language.setMaxLength(40);
      this.language.setValue(langValue);
      this.addRenderableWidget(this.language);
      y += 20;

      String wishValue = this.wishes != null ? this.wishes.getValue() : c.extraInstructions;
      this.wishes = new EditBox(this.font, x + LABEL_W, y, fieldW, 18, Component.literal("Wünsche"));
      this.wishes.setMaxLength(500);
      this.wishes.setValue(wishValue);
      this.wishes.setHint(Component.literal("z. B. \"mehr Beweise\", \"Klausurniveau\""));
      this.addRenderableWidget(this.wishes);
      y += 24;
      this.buttonsY = y;

      int third = 100;
      Button test = this.addRenderableWidget(Button.builder(Component.literal(this.testing ? "Teste …" : "Verbindung testen"), b -> this.test()).bounds(x, y, third, 20).build());
      test.active = !this.testing;
      this.addRenderableWidget(Button.builder(Component.literal("Sprechen …"), b -> {
         this.apply();
         this.minecraft.gui.setScreen(new SpeechSettingsScreen(this));
      }).bounds(x + third + 5, y, third, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose()).bounds(x + 2 * (third + 5), y, third, 20).build());
   }

   private Button label(Supplier<String> text, int x, int y, int w) {
      Button b = Button.builder(Component.literal(text.get()), x0 -> {}).bounds(x, y, w, 20).build();
      b.active = false;
      return b;
   }

   private Button cycle(List<String> values, Supplier<String> get, Consumer<String> set, Function<String, String> label, String tip, int x, int y, int w) {
      Button b = Button.builder(Component.literal(label.apply(get.get())), button -> {
         int i = values.indexOf(get.get());
         String next = values.get((i + 1) % values.size());
         set.accept(next);
         button.setMessage(Component.literal(label.apply(next)));
      }).bounds(x, y, w, 20).build();
      b.setTooltip(Tooltip.create(Component.literal(tip)));
      return b;
   }

   private void changeInterval(int dir) {
      UniConfig c = UniLernen.config;
      float[] steps = {0.5F, 1F, 2F, 3F, 4F, 5F, 6F, 8F, 10F, 15F, 20F, 30F, 45F, 60F};
      int best = 0;
      for (int i = 0; i < steps.length; i++) {
         if (Math.abs(steps[i] - c.intervalMinutes) < Math.abs(steps[best] - c.intervalMinutes)) {
            best = i;
         }
      }
      c.intervalMinutes = steps[Math.max(0, Math.min(steps.length - 1, best + dir))];
      this.rebuildWidgets();
   }

   private static String format(float v) {
      return v == Math.rint(v) ? String.valueOf((int)v) : String.format(Locale.ROOT, "%.1f", v);
   }

   /** Übernimmt die Textfelder in die Einstellungen. */
   private void apply() {
      UniConfig c = UniLernen.config;
      String k = this.key.getValue().strip();
      if (!k.isEmpty()) {
         c.apiKey = k;
         this.key.setValue("");
         this.key.setHint(Component.literal("gespeichert: " + c.maskedKey()));
      }
      c.model = this.model.getValue().strip();
      c.language = this.language.getValue().strip();
      c.extraInstructions = this.wishes.getValue().strip();
      UniLernen.saveConfig();
   }

   private void test() {
      this.apply();
      UniConfig c = UniLernen.config;
      if (!c.hasKey()) {
         this.testResult = "§cErst einen OpenRouter-Key eintragen (openrouter.ai → Keys).";
         return;
      }
      this.testing = true;
      this.testResult = "§7Frage " + c.model + " …";
      this.rebuildWidgets();
      JsonArray messages = new JsonArray();
      messages.add(OpenRouter.textMessage("user", "Antworte nur mit dem Wort OK."));
      new OpenRouter(() -> UniLernen.config).chat(messages, false, 0.0, Duration.ofSeconds(60)).whenComplete((reply, error) -> this.minecraft.execute(() -> {
         this.testing = false;
         this.testResult = error != null
            ? "§c" + OpenRouter.describe(error)
            : "§a✔ Verbindung ok – " + c.model + " antwortet: §f" + reply.strip().replace("\n", " ");
         if (this.minecraft.gui.screen() == this) {
            this.rebuildWidgets();
         }
      }));
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.centered(g, this.font, "Lernen generell – Einstellungen", this.width / 2, 10, Ui.GOLD);
      int x = this.left();
      int y = this.labelsTop + 5;
      Ui.text(g, this.font, "OpenRouter-Key", x, y, Ui.GREY);
      Ui.text(g, this.font, "Modell", x, y + 20, Ui.GREY);
      Ui.text(g, this.font, "Sprache", x, this.languageY + 5, Ui.GREY);
      Ui.text(g, this.font, "Wünsche an KI", x, this.languageY + 25, Ui.GREY);
      if (!this.testResult.isEmpty()) {
         var lines = this.font.split(Component.literal(this.testResult), Math.min(this.width - 20, 420));
         int ty = this.buttonsY + 23;
         for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(this.font, lines.get(i), this.width / 2 - this.font.width(lines.get(i)) / 2, ty + i * 10, Ui.WHITE, true);
         }
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
