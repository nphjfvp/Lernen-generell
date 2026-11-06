package dev.lernen.uni.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class OpenRouterTest {
   @Test
   void readsAnswer() {
      String body = "{\"id\":\"x\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"richtig\\\":true}\"},\"finish_reason\":\"stop\"}]}";
      assertEquals("{\"richtig\":true}", OpenRouter.content(200, body));
   }

   @Test
   void explainsErrors() {
      OpenRouter.ApiException e = assertThrows(OpenRouter.ApiException.class,
         () -> OpenRouter.content(401, "{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}"));
      assertEquals(401, e.status);
      assertTrue(e.getMessage().startsWith("OpenRouter-Key ungültig"), e.getMessage());
      OpenRouter.ApiException e2 = assertThrows(OpenRouter.ApiException.class, () -> OpenRouter.content(502, "<html>Bad gateway</html>"));
      assertEquals(502, e2.status);
      assertThrows(OpenRouter.ApiException.class, () -> OpenRouter.content(200, "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}"));
   }

   @Test
   void pdfMessageAndPlugin() {
      UniConfig c = new UniConfig();
      c.apiKey = "sk-or-test";
      c.pdfEngine = "mistral-ocr";
      OpenRouter client = new OpenRouter(() -> c);
      JsonArray messages = new JsonArray();
      messages.add(OpenRouter.pdfMessage("lies", "blatt.pdf", new byte[]{'%', 'P', 'D', 'F'}));
      JsonObject body = client.body(messages, true, 0.1);
      assertEquals("google/gemini-2.5-flash", body.get("model").getAsString());
      assertEquals("mistral-ocr", body.getAsJsonArray("plugins").get(0).getAsJsonObject().getAsJsonObject("pdf").get("engine").getAsString());
      String file = body.getAsJsonArray("messages").get(0).getAsJsonObject().getAsJsonArray("content").get(1).getAsJsonObject()
         .getAsJsonObject("file").get("file_data").getAsString();
      assertEquals("data:application/pdf;base64,JVBERg==", file);
      c.pdfEngine = "auto";
      assertFalse(client.body(messages, true, 0.1).has("plugins"));
   }

   @Test
   void parsesExtraction() {
      JsonObject o = AiJson.object("{\"fach\":\"Analysis I\",\"aufgaben\":[{\"titel\":\"A1a\",\"text\":\"Berechne $\\\\int_0^1 x^2 dx$\",\"loesung\":\"1/3\"},"
         + "{\"titel\":\"leer\",\"text\":\"\"}],\"stoff\":[{\"thema\":\"Stetigkeit\",\"inhalt\":\"ε-δ-Definition\"}]}");
      Ai.Extraction x = OpenRouterAi.parseExtraction("Blatt 1.pdf", o);
      assertEquals("Analysis I", x.subject());
      assertEquals(2, x.tasks().size());
      assertEquals("Berechne ∫₀¹ x² dx", x.tasks().get(0).text);
      assertEquals("Blatt 1#a1", x.tasks().get(0).id);
      assertTrue(x.tasks().get(1).isMaterialOnly());
   }
}
