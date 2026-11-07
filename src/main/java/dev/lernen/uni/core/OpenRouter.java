package dev.lernen.uni.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Ein schlanker Client für die Chat-API von OpenRouter (https://openrouter.ai/docs). */
public final class OpenRouter {
   public static final String URL = "https://openrouter.ai/api/v1/chat/completions";
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

   private final Supplier<UniConfig> config;

   public OpenRouter(Supplier<UniConfig> config) {
      this.config = config;
   }

   /** Ein Fehler, den man dem Spieler so zeigen kann. */
   public static final class ApiException extends RuntimeException {
      public final int status;

      public ApiException(int status, String message) {
         super(message);
         this.status = status;
      }
   }

   /** Eine Nachricht nur mit Text. */
   public static JsonObject textMessage(String role, String text) {
      JsonObject m = new JsonObject();
      m.addProperty("role", role);
      m.addProperty("content", text);
      return m;
   }

   /** Eine Nachricht mit Text und einer PDF-Datei (als data:-URL). */
   public static JsonObject pdfMessage(String text, String fileName, byte[] pdf) {
      JsonArray content = new JsonArray();
      JsonObject t = new JsonObject();
      t.addProperty("type", "text");
      t.addProperty("text", text);
      content.add(t);
      JsonObject file = new JsonObject();
      file.addProperty("filename", fileName);
      file.addProperty("file_data", "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdf));
      JsonObject f = new JsonObject();
      f.addProperty("type", "file");
      f.add("file", file);
      content.add(f);
      JsonObject m = new JsonObject();
      m.addProperty("role", "user");
      m.add("content", content);
      return m;
   }

   /** Eine Nachricht mit Text und einer Sprachaufnahme (WAV). */
   public static JsonObject audioMessage(String text, byte[] wav) {
      JsonArray content = new JsonArray();
      JsonObject t = new JsonObject();
      t.addProperty("type", "text");
      t.addProperty("text", text);
      content.add(t);
      JsonObject audio = new JsonObject();
      audio.addProperty("data", Base64.getEncoder().encodeToString(wav));
      audio.addProperty("format", "wav");
      JsonObject a = new JsonObject();
      a.addProperty("type", "input_audio");
      a.add("input_audio", audio);
      content.add(a);
      JsonObject m = new JsonObject();
      m.addProperty("role", "user");
      m.add("content", content);
      return m;
   }

   public JsonObject body(JsonArray messages, boolean withPdf, double temperature) {
      return this.body(this.config.get().model, messages, withPdf, temperature);
   }

   public JsonObject body(String model, JsonArray messages, boolean withPdf, double temperature) {
      UniConfig c = this.config.get();
      JsonObject body = new JsonObject();
      body.addProperty("model", model.trim());
      body.add("messages", messages);
      body.addProperty("temperature", temperature);
      if (withPdf && !"auto".equals(c.pdfEngine)) {
         JsonObject pdf = new JsonObject();
         pdf.addProperty("engine", c.pdfEngine);
         JsonObject plugin = new JsonObject();
         plugin.addProperty("id", "file-parser");
         plugin.add("pdf", pdf);
         JsonArray plugins = new JsonArray();
         plugins.add(plugin);
         body.add("plugins", plugins);
      }
      return body;
   }

   /** Schickt die Nachrichten ab und liefert den Text der Antwort. */
   public CompletableFuture<String> chat(JsonArray messages, boolean withPdf, double temperature, Duration timeout) {
      return this.chat(this.config.get().model, messages, withPdf, temperature, timeout);
   }

   public CompletableFuture<String> chat(String model, JsonArray messages, boolean withPdf, double temperature, Duration timeout) {
      UniConfig c = this.config.get();
      if (!c.hasKey()) {
         return CompletableFuture.failedFuture(new ApiException(401, "Kein OpenRouter-Key eingetragen (Lernmenü → Einstellungen)."));
      }

      String json = this.body(model, messages, withPdf, temperature).toString();
      HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
         .timeout(timeout)
         .header("Authorization", "Bearer " + c.apiKey.trim())
         .header("Content-Type", "application/json")
         .header("HTTP-Referer", "https://github.com/nphjfvp/Lernen-generell")
         .header("X-Title", "Lernen generell (Minecraft-Mod)")
         .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
         .build();
      return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
         .thenApply(response -> content(response.statusCode(), response.body()));
   }

   /** Liest den Antworttext aus der API-Antwort oder wirft einen verständlichen Fehler. */
   static String content(int status, String body) {
      JsonObject root = null;
      try {
         JsonElement e = JsonParser.parseString(body == null ? "" : body.trim());
         if (e.isJsonObject()) {
            root = e.getAsJsonObject();
         }
      } catch (RuntimeException ignored) {
      }

      if (root != null && root.has("error") && root.get("error").isJsonObject()) {
         JsonObject err = root.getAsJsonObject("error");
         int code = err.has("code") && err.get("code").isJsonPrimitive() && err.get("code").getAsJsonPrimitive().isNumber() ? err.get("code").getAsInt() : status;
         throw new ApiException(code, explain(code, AiJson.string(err, "message")));
      }

      if (status < 200 || status >= 300) {
         throw new ApiException(status, explain(status, body == null ? "" : body.length() > 200 ? body.substring(0, 200) : body));
      }

      if (root == null) {
         throw new ApiException(status, "OpenRouter hat keine lesbare Antwort geschickt.");
      }

      JsonArray choices = AiJson.array(root, "choices");
      if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
         throw new ApiException(status, "OpenRouter hat keine Antwort vom Modell geliefert.");
      }
      JsonObject choice = choices.get(0).getAsJsonObject();
      if (choice.has("error") && choice.get("error").isJsonObject()) {
         JsonObject err = choice.getAsJsonObject("error");
         throw new ApiException(status, "Modellfehler: " + AiJson.string(err, "message"));
      }
      JsonObject message = choice.has("message") && choice.get("message").isJsonObject() ? choice.getAsJsonObject("message") : new JsonObject();
      JsonElement content = message.get("content");
      String text;
      if (content == null || content.isJsonNull()) {
         text = "";
      } else if (content.isJsonArray()) {
         StringBuilder b = new StringBuilder();
         for (JsonElement part : content.getAsJsonArray()) {
            if (part.isJsonObject()) {
               b.append(AiJson.string(part.getAsJsonObject(), "text"));
            } else if (part.isJsonPrimitive()) {
               b.append(part.getAsString());
            }
         }
         text = b.toString();
      } else {
         text = content.getAsString();
      }

      if (text.isBlank()) {
         String reason = AiJson.string(choice, "finish_reason");
         throw new ApiException(status, "Das Modell hat leer geantwortet" + (reason.isEmpty() ? "." : " (" + reason + ")."));
      }
      return text;
   }

   static String explain(int status, String message) {
      String m = message == null ? "" : message.trim();
      String hint = switch (status) {
         case 400 -> "Anfrage abgelehnt";
         case 401 -> "OpenRouter-Key ungültig";
         case 402 -> "Kein Guthaben mehr auf OpenRouter";
         case 403 -> "Anfrage von der Moderation blockiert";
         case 404 -> "Modell nicht gefunden – Modellname in den Einstellungen prüfen";
         case 408 -> "Zeitüberschreitung";
         case 413 -> "Datei zu groß";
         case 429 -> "Zu viele Anfragen – kurz warten";
         case 502, 503 -> "Modell gerade nicht erreichbar";
         default -> "Fehler " + status;
      };
      return m.isEmpty() ? hint + "." : hint + ": " + m;
   }

   /** Der Fehlertext für den Spieler, auch für Netzwerkfehler. */
   public static String describe(Throwable t) {
      Throwable e = t;
      while (e.getCause() != null && (e instanceof java.util.concurrent.CompletionException || e instanceof java.util.concurrent.ExecutionException)) {
         e = e.getCause();
      }
      if (e instanceof ApiException || e instanceof IllegalArgumentException) {
         return e.getMessage();
      }
      if (e instanceof java.net.http.HttpTimeoutException) {
         return "Zeitüberschreitung bei OpenRouter.";
      }
      if (e instanceof java.net.ConnectException || e instanceof java.nio.channels.UnresolvedAddressException) {
         return "Keine Verbindung zu OpenRouter (Internet?).";
      }
      String msg = e.getMessage();
      return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
   }
}
