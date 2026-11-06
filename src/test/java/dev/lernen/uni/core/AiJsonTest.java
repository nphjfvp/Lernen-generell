package dev.lernen.uni.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AiJsonTest {
   @Test
   void readsFencedJsonWithTextAround() {
      JsonObject o = AiJson.object("Klar, hier:\n```json\n{\"richtig\": true, \"feedback\": \"Gut {so}\"}\n```\nViel Erfolg!");
      assertTrue(AiJson.bool(o, "richtig"));
      assertEquals("Gut {so}", AiJson.string(o, "feedback"));
   }

   @Test
   void readsBareJson() {
      JsonObject o = AiJson.object("{\"aufgabe\": \"Berechne 2+2\", \"loesung\": \"4\"} danach Text");
      assertEquals("Berechne 2+2", AiJson.string(o, "aufgabe"));
   }

   @Test
   void repairsLatexBackslashes() {
      // \f und \t wären in JSON Steuerzeichen; daraus soll wieder LaTeX werden.
      JsonObject o = AiJson.object("{\"loesung\": \"\\frac{1}{2} \\times \\theta\"}");
      String s = AiJson.string(o, "loesung");
      assertTrue(s.contains("\\frac{1}{2}"), s);
      assertTrue(s.contains("\\times"), s);
      assertTrue(s.contains("\\theta"), s);
   }

   @Test
   void keepsRealNewlines() {
      JsonObject o = AiJson.object("{\"text\": \"Zeile 1\\nZeile 2\"}");
      assertEquals("Zeile 1\nZeile 2", AiJson.string(o, "text"));
   }

   @Test
   void stringBooleansAndArrays() {
      JsonObject o = AiJson.object("{\"richtig\": \"ja\", \"loesung\": [\"a\", \"b\"]}");
      assertTrue(AiJson.bool(o, "richtig"));
      assertEquals("a\nb", AiJson.string(o, "loesung"));
      assertFalse(AiJson.bool(o, "fehlt"));
   }

   @Test
   void failsWithoutJson() {
      assertThrows(IllegalArgumentException.class, () -> AiJson.object("Tut mir leid, das kann ich nicht."));
   }
}
