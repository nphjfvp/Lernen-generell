package dev.lernen.uni.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Alle Einstellungen der Mod. Liegt in {@code .minecraft/config/unilernen.json}. */
public final class UniConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   public static final List<String> STYLES = List.of("gemischt", "original", "aehnlich", "kurz");
   public static final List<String> PENALTIES = List.of("stapel", "einzeln", "keine");
   public static final List<String> PDF_ENGINES = List.of("auto", "pdf-text", "mistral-ocr", "native");
   public static final List<String> HUD_POSITIONS = List.of("oben_rechts", "oben_links", "aus");

   public int configVersion = 1;
   /** OpenRouter-API-Key (sk-or-…). */
   public String apiKey = "";
   /** OpenRouter-Modell, z. B. google/gemini-2.5-flash. */
   public String model = "google/gemini-2.5-flash";
   /** Wie OpenRouter die PDF liest: auto, pdf-text (gratis, nur Text-PDFs), mistral-ocr (Scans), native. */
   public String pdfEngine = "auto";
   /** Ungefährer Abstand zwischen zwei Aufgaben in Minuten Spielzeit. */
   public float intervalMinutes = 5.0F;
   /** Zufällige Abweichung vom Abstand, in Prozent (20 = ±20 %). */
   public int jitterPercent = 20;
   /** gemischt, original (Aufgabe wie im PDF), aehnlich (neue Zahlen, gleicher Typ), kurz (kurze Verständnisfrage). */
   public String style = "gemischt";
   /** Strafe bei falscher Antwort: stapel (ganzer Stapel weg), einzeln (ein Gegenstand weg), keine. */
   public String penalty = "stapel";
   /** Nach so vielen falschen Versuchen darf man sich die Lösung zeigen lassen (0 = nie). */
   public int solutionAfterWrong = 3;
   /** Sprache, in der die Aufgaben gestellt und bewertet werden. */
   public String language = "Deutsch";
   /** Zusätzliche Wünsche an die KI, z. B. "Fokus auf Beweise" oder "nur Rechenaufgaben". */
   public String extraInstructions = "";
   /** Anzeige oben am Bildschirm: Zeit bis zur nächsten Aufgabe und Booster. */
   public String hudPosition = "oben_rechts";
   /** Aufgaben automatisch stellen (aus = nur über das Menü). */
   public boolean enabled = true;

   public boolean hasKey() {
      return this.apiKey != null && !this.apiKey.isBlank();
   }

   public long intervalMs() {
      return Math.max(15_000L, (long)(this.intervalMinutes * 60_000.0F));
   }

   public static UniConfig load(Path file) {
      if (Files.exists(file)) {
         try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            UniConfig c = GSON.fromJson(r, UniConfig.class);
            if (c != null) {
               c.sanitize();
               return c;
            }
         } catch (RuntimeException | IOException e) {
            Log.warn("Konnte " + file + " nicht lesen, nehme Standardwerte: " + e);
         }
      }

      UniConfig c = new UniConfig();
      c.save(file);
      return c;
   }

   public void sanitize() {
      if (this.apiKey == null) {
         this.apiKey = "";
      }
      this.apiKey = this.apiKey.trim();
      if (this.model == null || this.model.isBlank()) {
         this.model = new UniConfig().model;
      }
      if (!STYLES.contains(this.style)) {
         this.style = "gemischt";
      }
      if (!PENALTIES.contains(this.penalty)) {
         this.penalty = "stapel";
      }
      if (!PDF_ENGINES.contains(this.pdfEngine)) {
         this.pdfEngine = "auto";
      }
      if (!HUD_POSITIONS.contains(this.hudPosition)) {
         this.hudPosition = "oben_rechts";
      }
      if (this.language == null || this.language.isBlank()) {
         this.language = "Deutsch";
      }
      if (this.extraInstructions == null) {
         this.extraInstructions = "";
      }
      this.intervalMinutes = Math.max(0.5F, Math.min(120.0F, this.intervalMinutes));
      this.jitterPercent = Math.max(0, Math.min(90, this.jitterPercent));
      this.solutionAfterWrong = Math.max(0, Math.min(20, this.solutionAfterWrong));
   }

   public void save(Path file) {
      try {
         Files.createDirectories(file.getParent());
         try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
         }
      } catch (IOException e) {
         Log.warn("Konnte " + file + " nicht schreiben: " + e);
      }
   }

   /** Zeigt nur Anfang und Ende des Keys, damit er nicht im Stream landet. */
   public String maskedKey() {
      if (!this.hasKey()) {
         return "(keiner)";
      }
      String k = this.apiKey;
      return k.length() <= 12 ? "••••" : k.substring(0, 6) + "…" + k.substring(k.length() - 4);
   }
}
