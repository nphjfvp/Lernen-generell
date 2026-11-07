package dev.lernen.uni.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exportiert die Aufgaben als Textdatei, die Anki direkt importieren kann (Datei → Importieren). Die
 * Kopfzeilen sagen Anki Trennzeichen, Notiztyp, Deck- und Schlagwort-Spalte, man muss beim Import also
 * nichts einstellen. Karten: Vorderseite = Aufgabe, Rückseite = Lösung; bei Sprechsätzen Vorderseite =
 * Übersetzung, Rückseite = Satz mit Aussprache.
 */
public final class AnkiExport {
   public static final class Options {
      /** Alle Aufgaben und Themen, die die KI aus den PDFs gelesen hat. */
      public boolean pdfTasks = true;
      /** Die im Spiel gestellten Aufgaben (mit Musterlösung). */
      public boolean asked = true;
      /** Von den gestellten nur die, bei denen man mindestens einmal falsch lag. */
      public boolean onlyMistakes = false;
      /** Die Sätze aus den Sprechübungen. */
      public boolean speaking = true;
   }

   public record Result(Path file, int cards) {
   }

   private AnkiExport() {
   }

   public static String build(WorldData data, Options o, String deckName) {
      String deck = clean(deckName.isBlank() ? "Lernen generell" : deckName).replace("\t", " ");
      List<String[]> rows = new ArrayList<>();
      Set<String> seen = new LinkedHashSet<>();

      if (o.pdfTasks) {
         for (WorldData.Task t : data.tasks) {
            String front = (t.title.isBlank() ? "" : "<b>" + html(t.title) + "</b><br>") + html(t.text);
            String back;
            if (t.isMaterialOnly()) {
               front = "<b>" + html(t.topic.isBlank() ? "Thema" : t.topic) + "</b><br>Erkläre: Was besagt das?";
               back = html(t.text);
            } else {
               back = t.solution.isBlank() ? "<i>(Keine Lösung in der PDF)</i>" : html(t.solution);
            }
            add(rows, seen, front, back, deck + "::" + sub(source(data, t.source)), tags("pdf", t.source, t.topic));
         }
      }

      for (WorldData.HistoryEntry h : data.history) {
         if ("sprechen".equals(h.kind)) {
            if (!o.speaking) {
               continue;
            }
            String lang = Languages.name(h.language) == null ? h.language : Languages.name(h.language);
            String front = (h.translation.isBlank() ? "" : html(h.translation) + "<br>") + "<i>Sag es auf " + html(lang) + "</i>";
            String back = "<span style='font-size:1.4em'>" + html(h.question) + "</span>" + (h.reading.isBlank() ? "" : "<br>" + html(h.reading));
            add(rows, seen, front, back, deck + "::Sprechen " + sub(lang), tags("sprechen", "", lang));
         } else {
            if (!o.asked || (o.onlyMistakes && h.wrongAttempts == 0 && h.solved)) {
               continue;
            }
            String front = (h.title.isBlank() ? "" : "<b>" + html(h.title) + "</b><br>") + html(h.question);
            String back = h.solution.isBlank() ? "<i>(Keine Musterlösung gespeichert)</i>" : html(h.solution);
            String extra = h.wrongAttempts > 0 ? "falsch_" + h.wrongAttempts + "x" : "";
            add(rows, seen, front, back, deck + "::Gestellt", tags("gestellt", h.source, h.topic) + (extra.isEmpty() ? "" : " " + extra));
         }
      }

      StringBuilder b = new StringBuilder();
      b.append("#separator:tab\n#html:true\n#notetype:Basic\n#deck column:3\n#tags column:4\n");
      for (String[] r : rows) {
         b.append(r[0]).append('\t').append(r[1]).append('\t').append(r[2]).append('\t').append(r[3]).append('\n');
      }
      return b.toString();
   }

   /** Schreibt die Datei in den Ordner {@code unilernen/anki} der Welt. */
   public static Result write(WorldData data, Options o, String deckName) throws IOException {
      String text = build(data, o, deckName);
      int cards = (int)text.lines().filter(l -> !l.startsWith("#")).count();
      Path folder = data.folder().resolve("anki");
      Files.createDirectories(folder);
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm", Locale.ROOT));
      Path file = folder.resolve("lernen-generell_" + stamp + ".txt");
      Files.writeString(file, text, StandardCharsets.UTF_8);
      return new Result(file, cards);
   }

   private static void add(List<String[]> rows, Set<String> seen, String front, String back, String deck, String tags) {
      if (front.isBlank() || !seen.add(front)) {
         return;
      }
      rows.add(new String[]{front, back, deck, tags});
   }

   private static String source(WorldData data, String file) {
      WorldData.Source s = data.source(file);
      if (s != null && !s.subject.isBlank()) {
         return s.subject;
      }
      return file.replaceAll("(?i)\\.pdf$", "");
   }

   /** Ein Teil des Decknamens: "::" würde Unterdecks anlegen. */
   private static String sub(String s) {
      String t = clean(s).replace("::", ":").replace("\t", " ").strip();
      return t.isEmpty() ? "Allgemein" : t;
   }

   private static String tags(String kind, String source, String topic) {
      List<String> t = new ArrayList<>();
      t.add("lernen-generell");
      t.add(kind);
      for (String x : new String[]{source.replaceAll("(?i)\\.pdf$", ""), topic}) {
         String tag = x.strip().replaceAll("\\s+", "_").replaceAll("[\"\\t]", "");
         if (!tag.isEmpty() && tag.length() <= 60) {
            t.add(tag);
         }
      }
      return String.join(" ", t);
   }

   private static String clean(String s) {
      return s == null ? "" : s.replace("\r", "").replace("\n", " ");
   }

   /** Text für ein HTML-Feld: Sonderzeichen maskiert, Zeilenumbrüche als <br>, keine Tabs. */
   static String html(String s) {
      if (s == null) {
         return "";
      }
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
         .replace("\r", "").replace("\t", "    ").replace("\n", "<br>");
   }
}
