package dev.lernen.uni.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Die hochgeladenen PDFs einer Welt. Sie werden in den Weltordner kopiert ({@code unilernen/pdfs}) und
 * nacheinander von der KI gelesen; die gefundenen Aufgaben landen in {@link WorldData#tasks}.
 */
public final class MaterialLibrary {
   /** OpenRouter nimmt große Dateien an, aber sehr große dauern lange und kosten viel. */
   public static final long MAX_BYTES = 40L * 1024L * 1024L;

   private final Supplier<UniConfig> config;
   private final Ai ai;
   private final Executor main;
   private final LongSupplier clock;
   private WorldData data;
   private int session;
   private boolean working;
   private long retryAt;

   MaterialLibrary(Supplier<UniConfig> config, Ai ai, Executor main, LongSupplier clock) {
      this.config = config;
      this.ai = ai;
      this.main = main;
      this.clock = clock;
   }

   void attach(WorldData data, int session) {
      this.data = data;
      this.session = session;
      this.working = false;
      this.retryAt = 0L;
      this.scanFolder();
   }

   void detach() {
      this.data = null;
      this.working = false;
   }

   public boolean working() {
      return this.working;
   }

   /** Kopiert eine PDF in die Welt. Liefert eine Meldung für den Spieler. */
   public String importPdf(Path source) {
      if (this.data == null || this.data.pdfFolder() == null) {
         return "§cKeine Welt geladen.";
      }
      try {
         if (!Files.isRegularFile(source)) {
            return "§cDatei nicht gefunden: " + source;
         }
         if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf") || !isPdf(source)) {
            return "§cKeine PDF: " + source.getFileName();
         }
         long size = Files.size(source);
         if (size > MAX_BYTES) {
            return "§cZu groß (" + size / 1024 / 1024 + " MB, höchstens " + MAX_BYTES / 1024 / 1024 + " MB): " + source.getFileName();
         }
         Path folder = this.data.pdfFolder();
         Files.createDirectories(folder);
         if (source.toAbsolutePath().normalize().getParent().equals(folder.toAbsolutePath().normalize())) {
            return this.add(source.getFileName().toString()) ? "§aHinzugefügt: " + source.getFileName() : "§7Schon da: " + source.getFileName();
         }
         String name = uniqueName(folder, safeName(source.getFileName().toString()));
         Files.copy(source, folder.resolve(name));
         this.add(name);
         return "§aHochgeladen: " + name + " §7(wird gelesen)";
      } catch (IOException | RuntimeException e) {
         Log.warn("PDF-Import fehlgeschlagen: " + e);
         return "§cFehler beim Kopieren: " + e.getMessage();
      }
   }

   private boolean add(String fileName) {
      if (this.data.source(fileName) != null) {
         return false;
      }
      WorldData.Source s = new WorldData.Source();
      s.file = fileName;
      s.addedAt = System.currentTimeMillis();
      s.status = "wartet";
      this.data.sources.add(s);
      this.data.save();
      return true;
   }

   /** Nimmt PDFs auf, die man selbst in den Ordner gelegt hat. Liefert die Zahl der neuen. */
   public int scanFolder() {
      if (this.data == null || this.data.pdfFolder() == null || !Files.isDirectory(this.data.pdfFolder())) {
         return 0;
      }
      int added = 0;
      try (DirectoryStream<Path> files = Files.newDirectoryStream(this.data.pdfFolder(), "*.{pdf,PDF,Pdf}")) {
         for (Path p : files) {
            if (Files.isRegularFile(p) && this.add(p.getFileName().toString())) {
               added++;
            }
         }
      } catch (IOException e) {
         Log.warn("Konnte den PDF-Ordner nicht lesen: " + e);
      }
      return added;
   }

   /** Entfernt die PDF samt ihren Aufgaben. */
   public void remove(String fileName) {
      if (this.data == null) {
         return;
      }
      this.data.sources.removeIf(s -> s.file.equals(fileName));
      this.data.tasks.removeIf(t -> t.source.equals(fileName));
      try {
         Files.deleteIfExists(this.data.pdfFolder().resolve(fileName));
      } catch (IOException e) {
         Log.warn("Konnte " + fileName + " nicht löschen: " + e);
      }
      this.data.save();
   }

   /** Liest die PDF noch einmal (z. B. nach einem Fehler oder mit einem anderen Modell). */
   public void retry(String fileName) {
      if (this.data == null) {
         return;
      }
      WorldData.Source s = this.data.source(fileName);
      if (s != null && !"liest".equals(s.status)) {
         s.status = "wartet";
         s.error = "";
         this.retryAt = 0L;
         this.data.save();
      }
   }

   void tick() {
      if (this.data == null || this.working || !this.config.get().hasKey() || this.clock.getAsLong() < this.retryAt) {
         return;
      }
      for (WorldData.Source s : this.data.sources) {
         if ("wartet".equals(s.status)) {
            this.read(s);
            return;
         }
      }
   }

   private void read(WorldData.Source s) {
      byte[] bytes;
      try {
         bytes = Files.readAllBytes(this.data.pdfFolder().resolve(s.file));
      } catch (IOException e) {
         s.status = "fehler";
         s.error = "Datei nicht lesbar: " + e.getMessage();
         this.data.save();
         return;
      }
      s.status = "liest";
      s.error = "";
      this.working = true;
      int mySession = this.session;
      WorldData data = this.data;
      Log.info("Lese " + s.file + " (" + bytes.length / 1024 + " KB) mit " + this.config.get().model);
      this.ai.extract(s.file, bytes).whenComplete((result, error) -> this.main.execute(() -> {
         if (mySession != this.session || this.data != data) {
            return;
         }
         this.working = false;
         if (data.source(s.file) == null) {
            return;
         }
         if (error != null || result == null) {
            s.status = "fehler";
            s.error = error == null ? "keine Antwort" : OpenRouter.describe(error);
            this.retryAt = this.clock.getAsLong() + StudyManager.RETRY_MS;
            Log.warn("PDF " + s.file + " nicht gelesen: " + s.error);
         } else if (result.tasks().isEmpty()) {
            s.status = "fehler";
            s.error = "Keine Aufgaben oder Inhalte gefunden (Scan? PDF-Leser in den Einstellungen auf mistral-ocr stellen).";
         } else {
            data.tasks.removeIf(t -> t.source.equals(s.file));
            data.tasks.addAll(result.tasks());
            s.status = "fertig";
            s.subject = result.subject();
            s.summary = result.summary();
            s.taskCount = result.tasks().size();
            Log.info("PDF " + s.file + ": " + s.taskCount + " Einträge, Fach " + s.subject);
         }
         data.save();
      }));
   }

   public List<WorldData.Source> sources() {
      return this.data == null ? List.of() : new ArrayList<>(this.data.sources);
   }

   static boolean isPdf(Path p) throws IOException {
      try (InputStream in = Files.newInputStream(p)) {
         byte[] head = in.readNBytes(1024);
         String s = new String(head, java.nio.charset.StandardCharsets.ISO_8859_1);
         return s.contains("%PDF-");
      }
   }

   static String safeName(String name) {
      String n = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
      if (n.isEmpty() || n.equals(".pdf")) {
         n = "unterlagen.pdf";
      }
      if (n.length() > 120) {
         n = n.substring(0, 116) + ".pdf";
      }
      return n;
   }

   static String uniqueName(Path folder, String name) {
      if (!Files.exists(folder.resolve(name))) {
         return name;
      }
      String base = name.replaceAll("(?i)\\.pdf$", "");
      for (int i = 2; ; i++) {
         String candidate = base + " (" + i + ").pdf";
         if (!Files.exists(folder.resolve(candidate))) {
            return candidate;
         }
      }
   }
}
