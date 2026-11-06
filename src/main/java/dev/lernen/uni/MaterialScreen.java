package dev.lernen.uni;

import dev.lernen.uni.core.MaterialLibrary;
import dev.lernen.uni.core.StudyManager;
import dev.lernen.uni.core.WorldData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Die Unterlagen der Welt: PDFs ins Fenster ziehen oder den Pfad einfügen. Jede PDF wird einmal von der
 * KI gelesen; die Aufgaben daraus kommen dann im Spiel dran.
 */
public final class MaterialScreen extends Screen {
   private static final int ROW = 22;
   @Nullable
   private final Screen parent;
   private EditBox path;
   private String pathValue = "";
   private String info = "";
   private int page;
   private String shownState = "";

   public MaterialScreen(@Nullable Screen parent) {
      super(Component.literal("Unterlagen"));
      this.parent = parent;
   }

   private MaterialLibrary library() {
      return UniLernen.study.materials();
   }

   private int listTop() {
      return 76;
   }

   private int rowsPerPage() {
      return Math.max(1, (this.height - this.listTop() - 40) / ROW);
   }

   private int listWidth() {
      return Math.min(440, this.width - 24);
   }

   @Override
   protected void init() {
      int w = this.listWidth();
      int x0 = this.width / 2 - w / 2;
      this.path = new EditBox(this.font, x0, 34, w - 106, 20, Component.literal("Pfad"));
      this.path.setMaxLength(1024);
      this.path.setValue(this.pathValue);
      this.path.setHint(Component.literal("Pfad zur PDF einfügen, z. B. C:\\Uni\\Blatt1.pdf"));
      this.path.setResponder(v -> this.pathValue = v);
      this.addRenderableWidget(this.path);
      this.addRenderableWidget(Button.builder(Component.literal("Hinzufügen"), b -> this.addFromField()).bounds(x0 + w - 100, 34, 100, 20).build());

      int bw = (w - 12) / 3;
      this.addRenderableWidget(Button.builder(Component.literal("PDF-Ordner öffnen"), b -> this.openFolder()).bounds(x0, 56, bw, 18).build())
         .setTooltip(Tooltip.create(Component.literal("Lege PDFs in diesen Ordner und klicke dann auf \"Ordner einlesen\".")));
      this.addRenderableWidget(Button.builder(Component.literal("Ordner einlesen"), b -> {
         int n = this.library().scanFolder();
         this.info = n == 0 ? "§7Keine neuen PDFs im Ordner." : "§a" + n + " neue PDF(s) gefunden.";
         this.rebuildWidgets();
      }).bounds(x0 + bw + 6, 56, bw, 18).build());
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose()).bounds(x0 + 2 * (bw + 6), 56, bw, 18).build());

      List<WorldData.Source> sources = this.library().sources();
      int per = this.rowsPerPage();
      int pages = Math.max(1, (sources.size() + per - 1) / per);
      this.page = Math.min(this.page, pages - 1);
      for (int i = 0; i < per && this.page * per + i < sources.size(); i++) {
         WorldData.Source s = sources.get(this.page * per + i);
         int y = this.listTop() + 4 + i * ROW;
         Button again = this.addRenderableWidget(Button.builder(Component.literal("↻"), b -> {
            this.library().retry(s.file);
            this.rebuildWidgets();
         }).bounds(x0 + w - 44, y, 20, 18).build());
         again.setTooltip(Tooltip.create(Component.literal("Noch einmal von der KI lesen lassen")));
         again.active = !"liest".equals(s.status) && !"wartet".equals(s.status);
         this.addRenderableWidget(Button.builder(Component.literal("✕"), b -> this.confirmRemove(s.file)).bounds(x0 + w - 22, y, 20, 18).build())
            .setTooltip(Tooltip.create(Component.literal("PDF und ihre Aufgaben entfernen")));
      }
      if (pages > 1) {
         int y = this.height - 26;
         Button prev = this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            this.page--;
            this.rebuildWidgets();
         }).bounds(this.width / 2 - 50, y, 20, 20).build());
         prev.active = this.page > 0;
         Button next = this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            this.page++;
            this.rebuildWidgets();
         }).bounds(this.width / 2 + 30, y, 20, 20).build());
         next.active = this.page < pages - 1;
      }
      this.shownState = this.state();
   }

   /** Ändert sich, wenn eine PDF fertig gelesen ist; dann wird die Liste neu aufgebaut. */
   private String state() {
      StringBuilder b = new StringBuilder();
      for (WorldData.Source s : this.library().sources()) {
         b.append(s.file).append(':').append(s.status).append(';');
      }
      return b.toString();
   }

   @Override
   public void tick() {
      if (!this.state().equals(this.shownState)) {
         this.rebuildWidgets();
      }
   }

   private void addFromField() {
      String raw = this.pathValue.strip();
      if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1) {
         raw = raw.substring(1, raw.length() - 1);
      }
      if (raw.startsWith("file://")) {
         raw = raw.substring("file://".length());
      }
      if (raw.isEmpty()) {
         this.info = "§cErst einen Pfad einfügen (Strg+V).";
         return;
      }
      try {
         Path p = Path.of(raw);
         if (Files.isDirectory(p)) {
            int n = 0;
            try (var files = Files.newDirectoryStream(p, "*.{pdf,PDF,Pdf}")) {
               for (Path f : files) {
                  this.info = this.library().importPdf(f);
                  n++;
               }
            }
            this.info = n == 0 ? "§cIn dem Ordner liegen keine PDFs." : "§a" + n + " PDF(s) aus dem Ordner übernommen.";
         } else {
            this.info = this.library().importPdf(p);
         }
      } catch (Exception e) {
         this.info = "§cUngültiger Pfad: " + e.getMessage();
      }
      this.pathValue = "";
      this.rebuildWidgets();
   }

   /** PDFs, die man aus dem Explorer/Finder ins Minecraft-Fenster zieht. */
   public void onFilesDrop(List<Path> files) {
      int ok = 0;
      String last = "";
      for (Path f : files) {
         last = this.library().importPdf(f);
         if (last.startsWith("§a")) {
            ok++;
         }
      }
      this.info = files.size() == 1 ? last : "§a" + ok + " von " + files.size() + " Dateien übernommen. " + (ok < files.size() ? "§c" + last : "");
      this.rebuildWidgets();
   }

   private void openFolder() {
      WorldData data = UniLernen.study.data();
      if (data == null || data.pdfFolder() == null) {
         return;
      }
      try {
         Files.createDirectories(data.pdfFolder());
         openInFileManager(data.pdfFolder());
         this.info = "§7Ordner geöffnet: " + data.pdfFolder();
      } catch (Exception e) {
         this.minecraft.keyboardHandler.setClipboard(data.pdfFolder().toString());
         this.info = "§7Pfad in die Zwischenablage kopiert: " + data.pdfFolder();
      }
   }

   /** Öffnet den Ordner im Explorer/Finder/Dateimanager. */
   private static void openInFileManager(Path folder) throws java.io.IOException {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      String dir = folder.toAbsolutePath().toString();
      ProcessBuilder pb;
      if (os.contains("win")) {
         pb = new ProcessBuilder("explorer.exe", dir);
      } else if (os.contains("mac")) {
         pb = new ProcessBuilder("open", dir);
      } else {
         pb = new ProcessBuilder("xdg-open", dir);
      }
      pb.start();
   }

   private void confirmRemove(String file) {
      this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
         if (yes) {
            this.library().remove(file);
            this.info = "§7Entfernt: " + file;
         }
         this.minecraft.gui.setScreen(this);
      }, Component.literal("PDF entfernen?"), Component.literal(file + " und alle Aufgaben daraus werden aus dieser Welt gelöscht.")));
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.centered(g, this.font, "Unterlagen dieser Welt", this.width / 2, 8, Ui.GOLD);
      Ui.centered(g, this.font, "PDFs einfach ins Minecraft-Fenster ziehen – oder den Pfad unten einfügen.", this.width / 2, 20, Ui.GREY);

      int w = this.listWidth();
      int x0 = this.width / 2 - w / 2;
      int top = this.listTop();
      int bottom = this.height - 32;
      Ui.panel(g, x0 - 4, top, x0 + w + 4, bottom);
      List<WorldData.Source> sources = this.library().sources();
      StudyManager study = UniLernen.study;
      if (study.data() == null) {
         Ui.text(g, this.font, "Keine Welt geladen.", x0 + 4, top + 8, Ui.GREY);
      } else if (sources.isEmpty()) {
         Ui.text(g, this.font, "Noch keine PDFs. Lade Übungsblätter, Altklausuren oder Skripte hoch.", x0 + 4, top + 8, Ui.GREY);
         if (!UniLernen.config.hasKey()) {
            Ui.text(g, this.font, "Ohne OpenRouter-Key (Einstellungen) werden PDFs nicht gelesen.", x0 + 4, top + 22, Ui.RED);
         }
      }
      int per = this.rowsPerPage();
      int textW = w - 54;
      for (int i = 0; i < per && this.page * per + i < sources.size(); i++) {
         WorldData.Source s = sources.get(this.page * per + i);
         int y = this.listTop() + 4 + i * ROW;
         String icon;
         int color;
         String detail;
         switch (s.status) {
            case "fertig" -> {
               icon = "✔ ";
               color = Ui.GREEN;
               detail = (s.subject.isBlank() ? "" : s.subject + " · ") + s.taskCount + " Aufgaben/Themen";
            }
            case "liest" -> {
               icon = "⏳ ";
               color = Ui.YELLOW;
               detail = "wird von der KI gelesen …";
            }
            case "fehler" -> {
               icon = "✘ ";
               color = Ui.RED;
               detail = s.error;
            }
            default -> {
               icon = "… ";
               color = Ui.GREY;
               detail = UniLernen.config.hasKey() ? "wartet" : "wartet auf OpenRouter-Key";
            }
         }
         g.text(this.font, this.font.split(Component.literal(icon + s.file), textW).getFirst(), x0 + 4, y, color, true);
         var lines = this.font.split(Component.literal(detail), textW);
         if (!lines.isEmpty()) {
            g.text(this.font, lines.getFirst(), x0 + 16, y + 10, Ui.DARK_GREY, true);
         }
      }
      if (!this.info.isEmpty()) {
         var lines = this.font.split(Component.literal(this.info), this.width - 24);
         if (!lines.isEmpty()) {
            g.text(this.font, lines.getFirst(), this.width / 2 - this.font.width(lines.getFirst()) / 2, this.height - 12, Ui.WHITE, true);
         }
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
