package dev.lernen.uni.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vergleicht, was die Spracherkennung gehört hat, Wort für Wort mit dem erwarteten Satz. Chinesisch wird
 * über Pinyin-Silben ohne Töne verglichen (so stören falsch geratene, gleich klingende Schriftzeichen
 * nicht), Japanisch Zeichen für Zeichen, alle anderen Sprachen Wort für Wort mit Tippfehler-Toleranz.
 */
public final class SpeechCompare {
   /** Ab diesem Wert gilt ein Wort als richtig gesprochen. */
   public static final double WORD_OK = 0.75;

   /** Was die Erkennung gehört hat. {@code reading} sind Pinyin-Silben (nur Chinesisch, sonst leer). */
   public record Heard(String text, List<String> reading, double seconds, boolean silent) {
      public Heard {
         text = text == null ? "" : text.strip();
         reading = reading == null ? List.of() : List.copyOf(reading);
      }

      public boolean empty() {
         return this.text.isBlank() && this.reading.isEmpty();
      }
   }

   private SpeechCompare() {
   }

   /** Ein Wert pro erwartetem Wort zwischen 0 (nicht gehört) und 1 (genau so gehört). */
   public static double[] score(List<WorldData.SpeechWord> words, String language, Heard heard) {
      double[] out = new double[words.size()];
      if (words.isEmpty() || heard == null || heard.empty()) {
         return out;
      }

      // Jede Einheit (Silbe, Zeichen oder Wort) gehört zu einem Wort.
      List<String> units = new ArrayList<>();
      List<Integer> owner = new ArrayList<>();
      List<String> got;
      Similarity sim;
      boolean pinyin = "zh".equals(language) && !heard.reading().isEmpty() && allHaveReading(words);
      if (pinyin) {
         for (int i = 0; i < words.size(); i++) {
            for (String syllable : syllables(words.get(i).reading)) {
               units.add(syllable);
               owner.add(i);
            }
         }
         got = new ArrayList<>();
         for (String s : heard.reading()) {
            got.addAll(syllables(s));
         }
         sim = SpeechCompare::syllableSimilarity;
      } else if (Languages.isCjk(language)) {
         for (int i = 0; i < words.size(); i++) {
            for (String ch : characters(words.get(i).text)) {
               units.add(ch);
               owner.add(i);
            }
         }
         got = characters(heard.text());
         sim = (a, b) -> a.equals(b) ? 1.0 : 0.0;
      } else {
         for (int i = 0; i < words.size(); i++) {
            for (String w : words(words.get(i).text)) {
               units.add(w);
               owner.add(i);
            }
         }
         got = words(heard.text());
         sim = SpeechCompare::wordSimilarity;
      }

      double[] per = align(units, got, sim);
      double[] sum = new double[words.size()];
      int[] count = new int[words.size()];
      for (int u = 0; u < units.size(); u++) {
         sum[owner.get(u)] += per[u];
         count[owner.get(u)]++;
      }
      for (int i = 0; i < words.size(); i++) {
         out[i] = count[i] == 0 ? 1.0 : sum[i] / count[i];
      }
      return out;
   }

   private static boolean allHaveReading(List<WorldData.SpeechWord> words) {
      for (WorldData.SpeechWord w : words) {
         if (!characters(w.text).isEmpty() && w.reading.isBlank()) {
            return false;
         }
      }
      return true;
   }

   /** Wie viele Einheiten der Satz hat (Silben, Zeichen oder Wörter); misst das Sprechtempo. */
   public static int units(List<WorldData.SpeechWord> words, String language) {
      int n = 0;
      for (WorldData.SpeechWord w : words) {
         if ("zh".equals(language) && !w.reading.isBlank()) {
            n += syllables(w.reading).size();
         } else if (Languages.isCjk(language)) {
            n += characters(w.text).size();
         } else {
            n += words(w.text).size();
         }
      }
      return Math.max(1, n);
   }

   interface Similarity {
      double of(String expected, String heard);
   }

   /** Beste Zuordnung in Reihenfolge; liefert pro erwarteter Einheit, wie gut sie getroffen wurde. */
   static double[] align(List<String> expected, List<String> heard, Similarity sim) {
      int n = expected.size();
      int m = heard.size();
      double[][] best = new double[n + 1][m + 1];
      for (int i = 1; i <= n; i++) {
         for (int j = 1; j <= m; j++) {
            double diag = best[i - 1][j - 1] + sim.of(expected.get(i - 1), heard.get(j - 1));
            best[i][j] = Math.max(diag, Math.max(best[i - 1][j], best[i][j - 1]));
         }
      }
      double[] per = new double[n];
      int i = n;
      int j = m;
      while (i > 0 && j > 0) {
         double s = sim.of(expected.get(i - 1), heard.get(j - 1));
         if (s > 0 && Math.abs(best[i][j] - (best[i - 1][j - 1] + s)) < 1e-9) {
            per[i - 1] = s;
            i--;
            j--;
         } else if (Math.abs(best[i][j] - best[i - 1][j]) < 1e-9) {
            i--;
         } else {
            j--;
         }
      }
      return per;
   }

   /** Pinyin-Silben ohne Töne: "ní tǔ" → [ni, tu], "ni2tu3" → [ni, tu], "nǚ" → [nv]. */
   static List<String> syllables(String reading) {
      List<String> out = new ArrayList<>();
      String s = reading.toLowerCase(Locale.ROOT).replace("ü", "v").replace("u:", "v");
      s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
      for (String part : s.split("[^a-z]+")) {
         if (!part.isEmpty()) {
            out.add(part);
         }
      }
      return out;
   }

   private static final String[] INITIALS = {"zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w"};

   /** 1 = gleiche Silbe, 0.5 = gleicher Anlaut oder gleicher Auslaut (tu/ku, wa/wai), sonst 0. Wie im Sprach-Helfer. */
   static double syllableSimilarity(String a, String b) {
      if (a.equals(b)) {
         return 1.0;
      }
      String[] x = split(a);
      String[] y = split(b);
      return (!x[0].isEmpty() && x[0].equals(y[0])) || x[1].equals(y[1]) ? 0.5 : 0.0;
   }

   private static String[] split(String s) {
      for (String ini : INITIALS) {
         if (s.startsWith(ini) && s.length() > ini.length()) {
            return new String[]{ini, s.substring(ini.length())};
         }
      }
      return new String[]{"", s};
   }

   /** Einzelne Schriftzeichen ohne Satzzeichen und Leerzeichen. */
   static List<String> characters(String text) {
      List<String> out = new ArrayList<>();
      text.codePoints().forEach(cp -> {
         if (Character.isLetterOrDigit(cp)) {
            out.add(new String(Character.toChars(cp)));
         }
      });
      return out;
   }

   /** Wörter klein, ohne Akzente und Satzzeichen ("¿Dónde está?" → [donde, esta]). */
   static List<String> words(String text) {
      List<String> out = new ArrayList<>();
      String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
      for (String part : s.split("[^\\p{L}\\p{N}']+")) {
         String w = part.replaceAll("^'+|'+$", "");
         if (!w.isEmpty()) {
            out.add(w);
         }
      }
      return out;
   }

   /** 1 - Editierabstand / Länge, also kleine Abweichungen ("ich"/"isch") zählen fast voll. */
   static double wordSimilarity(String a, String b) {
      if (a.equals(b)) {
         return 1.0;
      }
      int max = Math.max(a.length(), b.length());
      double s = 1.0 - (double)distance(a, b) / max;
      return s < 0.5 ? 0.0 : s;
   }

   static int distance(String a, String b) {
      int[] prev = new int[b.length() + 1];
      int[] cur = new int[b.length() + 1];
      for (int j = 0; j <= b.length(); j++) {
         prev[j] = j;
      }
      for (int i = 1; i <= a.length(); i++) {
         cur[0] = i;
         for (int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
         }
         int[] t = prev;
         prev = cur;
         cur = t;
      }
      return prev[b.length()];
   }
}
