package dev.lernen.uni.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Holt das JSON-Objekt aus einer KI-Antwort, auch wenn drumherum Text oder ```-Blöcke stehen. */
public final class AiJson {
   /**
    * LaTeX-Befehle, deren Backslash in JSON als Escape gelesen würde (\f, \b, \n, \t, \r …). Die KI soll
    * zwar kein LaTeX schreiben, tut es aber manchmal; dann wird hier aus "\frac" wieder "\\frac".
    */
   private static final Pattern BAD_ESCAPES = Pattern.compile(
      "(?<!\\\\)\\\\(?=(frac|dfrac|tfrac|forall|binom|beta|bar|bmatrix|begin|big|Big|bigl|bigr|boldsymbol|bot|neq|ne|nabla|nu|not|notin|newline|"
         + "times|theta|Theta|tau|to|text|textbf|tilde|top|triangle|tfrac|rightarrow|Rightarrow|rho|rangle|right|rfloor|rceil|"
         + "underline|uparrow|cup|cap|cdot|cdots|circ|mathbb|mathrm|mathcal|langle|left|leq|le|lim|ln|log|ldots|lambda|"
         + "infty|int|in|iff|partial|pi|phi|psi|pm|prod|sum|sqrt|sigma|subset|subseteq|setminus|sin|cos|tan|exp|det|dots|"
         + "equiv|epsilon|varepsilon|varphi|alpha|gamma|delta|Delta|mu|omega|Omega|xi|zeta|eta|kappa|chi|geq|ge|approx|mid|vec|hat|overline|mapsto|implies|exists|emptyset|quad|qquad)\\b)"
   );
   private static final Pattern FENCE = Pattern.compile("```(?:json|JSON)?\\s*(.*?)```", Pattern.DOTALL);

   private AiJson() {
   }

   /** Das erste vollständige JSON-Objekt im Text. Wirft {@link IllegalArgumentException}, wenn keins da ist. */
   public static JsonObject object(String reply) {
      if (reply == null) {
         throw new IllegalArgumentException("leere Antwort");
      }

      List<String> candidates = new ArrayList<>();
      Matcher fence = FENCE.matcher(reply);
      while (fence.find()) {
         candidates.add(fence.group(1));
      }
      candidates.add(reply);

      RuntimeException last = null;
      for (String c : candidates) {
         String body = outermostObject(c);
         if (body == null) {
            continue;
         }
         // Zuerst mit reparierten LaTeX-Backslashes: "\frac" wäre sonst gültiges JSON, aber ein Seitenvorschub + "rac".
         String repaired = BAD_ESCAPES.matcher(body).replaceAll("\\\\\\\\");
         for (String attempt : repaired.equals(body) ? List.of(body) : List.of(repaired, body)) {
            try {
               JsonReader reader = new JsonReader(new StringReader(attempt));
               // parseReader liest nachsichtig (lenient): einfache Anführungszeichen, Kommas am Ende …
               JsonElement e = JsonParser.parseReader(reader);
               if (e.isJsonObject()) {
                  return e.getAsJsonObject();
               }
            } catch (RuntimeException ex) {
               last = ex;
            }
         }
      }

      throw new IllegalArgumentException("Die KI-Antwort enthält kein gültiges JSON" + (last == null ? "" : ": " + last.getMessage()));
   }

   /** Von der ersten { bis zur passenden }, mit Rücksicht auf Strings. */
   static String outermostObject(String s) {
      int start = s.indexOf('{');
      if (start < 0) {
         return null;
      }
      int depth = 0;
      boolean inString = false;
      boolean escaped = false;
      for (int i = start; i < s.length(); i++) {
         char ch = s.charAt(i);
         if (inString) {
            if (escaped) {
               escaped = false;
            } else if (ch == '\\') {
               escaped = true;
            } else if (ch == '"') {
               inString = false;
            }
         } else if (ch == '"') {
            inString = true;
         } else if (ch == '{') {
            depth++;
         } else if (ch == '}') {
            depth--;
            if (depth == 0) {
               return s.substring(start, i + 1);
            }
         }
      }
      // Abgeschnittene Antwort: bis zum Ende nehmen, der Parser meldet dann den Fehler.
      return s.substring(start);
   }

   public static String string(JsonObject o, String... keys) {
      for (String key : keys) {
         JsonElement e = o.get(key);
         if (e != null && !e.isJsonNull()) {
            if (e.isJsonPrimitive()) {
               return e.getAsString().trim();
            }
            if (e.isJsonArray()) {
               List<String> parts = new ArrayList<>();
               for (JsonElement x : e.getAsJsonArray()) {
                  parts.add(x.isJsonPrimitive() ? x.getAsString() : x.toString());
               }
               return String.join("\n", parts).trim();
            }
            return e.toString();
         }
      }
      return "";
   }

   public static boolean bool(JsonObject o, String... keys) {
      for (String key : keys) {
         JsonElement e = o.get(key);
         if (e != null && e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isBoolean()) {
               return e.getAsBoolean();
            }
            String s = e.getAsString().trim().toLowerCase();
            return s.equals("true") || s.equals("ja") || s.equals("yes") || s.equals("richtig") || s.equals("1");
         }
      }
      return false;
   }

   public static JsonArray array(JsonObject o, String... keys) {
      for (String key : keys) {
         JsonElement e = o.get(key);
         if (e != null && e.isJsonArray()) {
            return e.getAsJsonArray();
         }
      }
      return new JsonArray();
   }
}
