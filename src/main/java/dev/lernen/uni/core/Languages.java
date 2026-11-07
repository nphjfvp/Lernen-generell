package dev.lernen.uni.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Die Sprachen für Sprechübungen. */
public final class Languages {
   private static final Map<String, String> NAMES = new LinkedHashMap<>();
   /** Diese kann das Sprach-Hilfsprogramm von Chinese-Minecraft erkennen. */
   public static final List<String> HELPER = List.of("zh", "en", "de", "es", "pt", "ja");

   static {
      NAMES.put("zh", "Chinesisch");
      NAMES.put("en", "Englisch");
      NAMES.put("de", "Deutsch");
      NAMES.put("es", "Spanisch");
      NAMES.put("fr", "Französisch");
      NAMES.put("it", "Italienisch");
      NAMES.put("pt", "Portugiesisch");
      NAMES.put("ja", "Japanisch");
   }

   private Languages() {
   }

   public static List<String> codes() {
      return List.copyOf(NAMES.keySet());
   }

   public static String name(String code) {
      return NAMES.get(code);
   }

   /** Sprachen ohne Leerzeichen zwischen den Wörtern: hier zählen Zeichen bzw. Silben statt Wörter. */
   public static boolean isCjk(String code) {
      return "zh".equals(code) || "ja".equals(code);
   }
}
