package dev.lernen.uni.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Das lokale Sprach-Hilfsprogramm von Chinese-Minecraft ("Learn or Die", http://127.0.0.1:17645). Es nimmt
 * selbst über das Mikrofon auf und erkennt offline; Minecraft muss dafür keinen Mikrofonzugriff haben. Läuft
 * es (z. B. weil Chinese-Minecraft es gestartet hat), benutzt diese Mod es mit.
 */
public final class HelperSpeech implements SpeechInput {
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(600)).build();
   private final Supplier<UniConfig> config;
   private volatile boolean done;
   private volatile boolean up;
   private volatile boolean ready;
   private volatile String loadingInfo = "";
   private long lastPoll;
   private long lastHealth;
   private String healthLanguage = "";
   private CompletableFuture<?> pollInFlight;
   private CompletableFuture<?> healthInFlight;

   public HelperSpeech(Supplier<UniConfig> config) {
      this.config = config;
   }

   private String base() {
      return this.config.get().speechHelperUrl.strip().replaceAll("/+$", "");
   }

   @Override
   public String name() {
      return "Sprach-Helfer (lokal)";
   }

   /** Fragt höchstens alle 5 s nach, ob der Helfer läuft und das Sprachmodell geladen ist. */
   public void checkHealth(String language) {
      long now = System.currentTimeMillis();
      boolean changed = !language.equals(this.healthLanguage);
      if ((now - this.lastHealth < 5000L && !changed) || (this.healthInFlight != null && !this.healthInFlight.isDone())) {
         return;
      }
      this.lastHealth = now;
      this.healthLanguage = language;
      HttpRequest req = HttpRequest.newBuilder(URI.create(this.base() + "/health?language=" + URLEncoder.encode(language, StandardCharsets.UTF_8)))
         .timeout(Duration.ofSeconds(2))
         .GET()
         .build();
      this.healthInFlight = HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
         this.up = r.statusCode() == 200;
         boolean ok = false;
         String info = "";
         if (this.up) {
            try {
               JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
               ok = !o.has("ready") || o.get("ready").getAsBoolean();
               if (!ok && o.has("failed") && o.get("failed").isJsonObject() && o.getAsJsonObject("failed").has(language)) {
                  info = "Sprachmodell konnte nicht geladen werden: " + o.getAsJsonObject("failed").get(language).getAsString();
               } else if (!ok) {
                  info = "Sprach-Helfer lädt das Modell für " + Languages.name(language) + " (beim ersten Mal ca. 1,2 GB) …";
               }
            } catch (RuntimeException e) {
               ok = true;
            }
         }
         this.ready = ok;
         this.loadingInfo = info;
      }).exceptionally(e -> {
         this.up = false;
         this.ready = false;
         this.loadingInfo = "";
         return null;
      });
   }

   public boolean up() {
      return this.up;
   }

   public boolean ready() {
      return this.up && this.ready;
   }

   public String loadingInfo() {
      return this.loadingInfo;
   }

   @Override
   public CompletableFuture<Void> start() {
      this.done = false;
      JsonObject body = new JsonObject();
      HttpRequest req = HttpRequest.newBuilder(URI.create(this.base() + "/start"))
         .timeout(Duration.ofSeconds(3))
         .header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
         .build();
      return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
         if (r.statusCode() != 200) {
            throw new IllegalStateException("Sprach-Helfer: Aufnahme startet nicht (" + r.statusCode() + ")");
         }
      });
   }

   @Override
   public void poll() {
      long now = System.currentTimeMillis();
      if (this.done || now - this.lastPoll < 150L || (this.pollInFlight != null && !this.pollInFlight.isDone())) {
         return;
      }
      this.lastPoll = now;
      HttpRequest req = HttpRequest.newBuilder(URI.create(this.base() + "/status")).timeout(Duration.ofSeconds(1)).GET().build();
      this.pollInFlight = HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
         if (r.statusCode() == 200) {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("done") && o.get("done").getAsBoolean()) {
               this.done = true;
            }
         }
      }).exceptionally(e -> null);
   }

   @Override
   public boolean finished() {
      return this.done;
   }

   @Override
   public CompletableFuture<SpeechCompare.Heard> stop(String language) {
      JsonObject body = new JsonObject();
      body.addProperty("language", language);
      HttpRequest req = HttpRequest.newBuilder(URI.create(this.base() + "/stop"))
         .timeout(Duration.ofSeconds(25))
         .header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
         .build();
      return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> parse(r.statusCode(), r.body()));
   }

   static SpeechCompare.Heard parse(int status, String body) {
      JsonObject o;
      try {
         o = JsonParser.parseString(body).getAsJsonObject();
      } catch (RuntimeException e) {
         throw new IllegalStateException("Sprach-Helfer antwortet nicht lesbar (" + status + ")");
      }
      if (status != 200 || o.has("error")) {
         throw new IllegalStateException("Sprach-Helfer: " + AiJson.string(o, "error"));
      }
      List<String> reading = new ArrayList<>();
      JsonElement r = o.has("reading") ? o.get("reading") : o.get("pinyin");
      if (r != null && r.isJsonArray()) {
         r.getAsJsonArray().forEach(e -> reading.add(e.getAsString()));
      }
      double seconds = o.has("seconds") ? o.get("seconds").getAsDouble() : 0.0;
      boolean silent = o.has("silent") && o.get("silent").getAsBoolean();
      return new SpeechCompare.Heard(AiJson.string(o, "text"), reading, seconds, silent);
   }

   @Override
   public void cancel() {
      // Der Helfer kennt kein Abbrechen: stoppen und das Ergebnis wegwerfen.
      this.stop(this.healthLanguage.isEmpty() ? "zh" : this.healthLanguage).exceptionally(e -> null);
   }
}
