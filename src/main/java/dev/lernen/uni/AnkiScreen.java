package dev.lernen.uni;

import dev.lernen.uni.core.AnkiExport;
import dev.lernen.uni.core.WorldData;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Exportiert Aufgaben und Sprechsätze als Datei für Anki (Datei → Importieren). */
public final class AnkiScreen extends Screen {
   @Nullable
   private final Screen parent;
   private final AnkiExport.Options options = new AnkiExport.Options();
   private EditBox deck;
   private String deckValue;
   private String info = "";
   private Path lastFile;

   public AnkiScreen(@Nullable Screen parent) {
      super(Component.literal("Anki-Export"));
      this.parent = parent;
      WorldData data = UniLernen.study.data();
      String subjects = data == null ? "" : data.subjects();
      this.deckValue = subjects.isBlank() ? "Lernen generell" : "Lernen generell::" + subjects.split(",")[0].strip();
   }

   @Override
   protected void init() {
      int w = 310;
      int x = this.width / 2 - w / 2;
      int y = 40;
      this.deck = new EditBox(this.font, x + 70, y, w - 70, 20, Component.literal("Deck"));
      this.deck.setMaxLength(120);
      this.deck.setValue(this.deckValue);
      this.deck.setResponder(v -> this.deckValue = v);
      this.addRenderableWidget(this.deck);
      y += 28;
      this.addRenderableWidget(this.toggle(() -> this.options.pdfTasks, v -> this.options.pdfTasks = v, "Aufgaben aus den PDFs", x, y, w));
      y += 24;
      this.addRenderableWidget(this.toggle(() -> this.options.asked, v -> this.options.asked = v, "Im Spiel gestellte Aufgaben", x, y, w));
      y += 24;
      this.addRenderableWidget(this.toggle(() -> this.options.onlyMistakes, v -> this.options.onlyMistakes = v, "  … davon nur falsch beantwortete", x, y, w));
      y += 24;
      this.addRenderableWidget(this.toggle(() -> this.options.speaking, v -> this.options.speaking = v, "Sätze aus den Sprechübungen", x, y, w));
      y += 30;
      int half = (w - 6) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("Exportieren"), b -> this.export()).bounds(x, y, half, 20).build());
      Button open = this.addRenderableWidget(Button.builder(Component.literal("Ordner öffnen"), b -> this.openFolder()).bounds(x + half + 6, y, half, 20).build());
      open.active = this.lastFile != null;
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose()).bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
   }

   private Button toggle(java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set, String label, int x, int y, int w) {
      return Button.builder(Component.literal((get.getAsBoolean() ? "☑ " : "☐ ") + label), b -> {
         set.accept(!get.getAsBoolean());
         b.setMessage(Component.literal((get.getAsBoolean() ? "☑ " : "☐ ") + label));
      }).bounds(x, y, w, 20).build();
   }

   private void export() {
      WorldData data = UniLernen.study.data();
      if (data == null) {
         return;
      }
      try {
         AnkiExport.Result r = AnkiExport.write(data, this.options, this.deckValue);
         this.lastFile = r.file();
         this.info = r.cards() == 0 ? "§eLeer: nichts zum Exportieren ausgewählt." : "§a" + r.cards() + " Karten gespeichert: §f" + r.file();
         this.minecraft.keyboardHandler.setClipboard(r.file().toString());
      } catch (Exception e) {
         this.info = "§cExport fehlgeschlagen: " + e.getMessage();
      }
      this.rebuildWidgets();
   }

   private void openFolder() {
      if (this.lastFile == null) {
         return;
      }
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      String dir = this.lastFile.getParent().toAbsolutePath().toString();
      try {
         new ProcessBuilder(os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open", dir).start();
      } catch (Exception e) {
         this.info = "§7Pfad ist in der Zwischenablage: " + dir;
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.centered(g, this.font, "Anki-Export", this.width / 2, 10, Ui.GOLD);
      Ui.centered(g, this.font, "Erstellt eine Textdatei, die Anki direkt importiert (Datei → Importieren).", this.width / 2, 22, Ui.GREY);
      Ui.text(g, this.font, "Deck", this.width / 2 - 155, 46, Ui.GREY);
      int y = 40 + 28 + 4 * 24 + 30 + 28;
      WorldData data = UniLernen.study.data();
      if (data != null) {
         Ui.centered(g, this.font, data.tasks.size() + " Aufgaben/Themen aus PDFs · " + data.history.size() + " gestellte Aufgaben und Sätze", this.width / 2, y, Ui.DARK_GREY);
         y += 14;
      }
      if (!this.info.isEmpty()) {
         var lines = this.font.split(Component.literal(this.info), Math.min(this.width - 20, 420));
         for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.text(this.font, lines.get(i), this.width / 2 - this.font.width(lines.get(i)) / 2, y + i * 10, Ui.WHITE, true);
         }
         y += 34;
         Ui.centered(g, this.font, "Der Pfad ist in der Zwischenablage. In Anki: Datei → Importieren → Datei wählen → Importieren.", this.width / 2, y, Ui.DARK_GREY);
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
