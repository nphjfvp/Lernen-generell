package dev.lernen.uni;

import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.lernen.uni.core.HelperSpeech;
import dev.lernen.uni.core.Languages;
import dev.lernen.uni.core.Log;
import dev.lernen.uni.core.MicSpeech;
import dev.lernen.uni.core.SpeechInput;
import dev.lernen.uni.core.OpenRouterAi;
import dev.lernen.uni.core.StudyManager;
import dev.lernen.uni.core.UniConfig;
import dev.lernen.uni.core.WorldData;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hält alles zusammen: Einstellungen, Tasten, den Aufgaben-Ablauf und die Daten der aktuellen Welt. */
public final class UniLernen {
   public static final String MOD_ID = "unilernen";
   public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
   public static UniConfig config = new UniConfig();
   public static StudyManager study;
   public static OpenRouterAi ai;
   /** Das Sprach-Hilfsprogramm von Chinese-Minecraft, falls es läuft. */
   public static HelperSpeech helper;
   public static KeyMapping keyMenu;
   public static KeyMapping keyBoosters;
   /** Was man ins Antwortfeld getippt hat, falls das Fenster zwischendurch zu war (Pausemenü). */
   public static String draft = "";

   private static final List<KeyMapping> KEYS = new ArrayList<>();
   private static Path configFile;
   private static Path configDir;
   private static String worldKey;
   private static long lastTickAt;
   private static boolean hintedKey;

   private UniLernen() {
   }

   public static void init(Path configDirectory) {
      Log.info = LOGGER::info;
      Log.warn = LOGGER::warn;
      configDir = configDirectory;
      configFile = configDir.resolve("unilernen.json");
      config = UniConfig.load(configFile);
      Minecraft mc = Minecraft.getInstance();
      ai = new OpenRouterAi(() -> config);
      helper = new HelperSpeech(() -> config);
      study = new StudyManager(() -> config, ai, mc::execute, new Random(), System::currentTimeMillis);
      study.setSpeechAvailable(() -> speechInput(config.speechLanguage) != null);
      Category category = Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
      // Tastencodes sind Scancodes: 18 ist O, 28 ist Y (auf deutscher Tastatur die Z-Taste).
      keyMenu = key("menu", 18, category);
      keyBoosters = key("boosters", 28, category);
   }

   private static KeyMapping key(String name, int code, Category category) {
      KeyMapping key = new KeyMapping("key.unilernen." + name, Type.KEYBOARD, code, category);
      KEYS.add(key);
      return key;
   }

   public static List<KeyMapping> keys() {
      return KEYS;
   }

   public static void saveConfig() {
      config.sanitize();
      config.save(configFile);
   }

   public static void tick(Minecraft mc) {
      long now = System.currentTimeMillis();
      long delta = lastTickAt == 0L ? 50L : now - lastTickAt;
      lastTickAt = now;

      if (mc.level == null || mc.player == null) {
         if (worldKey != null) {
            study.detach();
            worldKey = null;
            draft = "";
         }
         return;
      }

      String key = worldKey(mc);
      if (!key.equals(worldKey)) {
         if (worldKey != null) {
            study.detach();
         }
         worldKey = key;
         draft = "";
         Path file = dataFile(mc);
         LOGGER.info("Lernen generell: Daten der Welt in {}", file);
         study.attach(WorldData.load(file), new GameBridge(mc));
         hintedKey = false;
      }

      while (keyMenu.consumeClick()) {
         if (mc.gui.screen() == null) {
            mc.gui.setScreen(new MenuScreen(null));
         }
      }
      while (keyBoosters.consumeClick()) {
         if (mc.gui.screen() == null) {
            mc.gui.setScreen(new BoosterScreen(null));
         }
      }

      String speakLanguage = study.active() != null && study.active().isSpeaking() ? study.active().speech.language : config.speechLanguage;
      if (!"aus".equals(config.speechMode) || (study.active() != null && study.active().isSpeaking()) || mc.gui.screen() instanceof MenuScreen) {
         helper.checkHealth(speakLanguage);
      }

      boolean screenFree = mc.gui.screen() == null && mc.player.isAlive();
      boolean playing = !mc.isPaused();
      study.tick(delta, playing, screenFree);

      if (!hintedKey && screenFree && study.data() != null && study.data().playMs > 3000L) {
         hintedKey = true;
         String blocker = study.blocker();
         if (!blocker.isEmpty() && !"Automatische Aufgaben aus".equals(blocker)) {
            message(mc, "§e[Lernen] §f" + blocker + "§7 – öffne das Lernmenü mit §f" + keyName(keyMenu) + "§7.");
         }
      }
   }

   /** Woran man die Welt erkennt: der Ordner im Einzelspieler, sonst die Serveradresse. */
   static String worldKey(Minecraft mc) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server != null) {
         return "save:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
      }
      return mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip : "unbekannt";
   }

   /** Im Einzelspieler im Weltordner, sonst unter config/unilernen/server/. */
   static Path dataFile(Minecraft mc) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server != null) {
         return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve(MOD_ID).resolve("welt.json");
      }
      String name = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "unbekannt";
      return configDir.resolve(MOD_ID).resolve("server").resolve(name.replaceAll("[^A-Za-z0-9._-]", "_")).resolve("welt.json");
   }

   /**
    * Die Spracherkennung für diese Sprache, oder null, wenn gerade keine geht. "auto": der Sprach-Helfer von
    * Chinese-Minecraft, wenn er läuft und die Sprache kann, sonst Aufnahme im Spiel + OpenRouter.
    */
   public static SpeechInput speechInput(String language) {
      boolean helperOk = helper.ready() && Languages.HELPER.contains(language);
      switch (config.speechEngine) {
         case "helfer":
            return helperOk ? helper : null;
         case "openrouter":
            return config.hasKey() ? mic(language) : null;
         default:
            if (helperOk) {
               return helper;
            }
            return config.hasKey() ? mic(language) : null;
      }
   }

   private static MicSpeech micSpeech;
   private static String micKey = "";

   private static MicSpeech mic(String language) {
      String key = language + "|" + config.audioModelOrDefault();
      if (micSpeech == null || !key.equals(micKey)) {
         micKey = key;
         micSpeech = new MicSpeech(config.audioModelOrDefault(), wav -> ai.transcribe(wav, language));
      }
      return micSpeech;
   }

   /** Warum gerade nicht gesprochen werden kann, für die Anzeige; leer, wenn es geht. */
   public static String speechProblem(String language) {
      if (speechInput(language) != null) {
         return "";
      }
      if (!helper.loadingInfo().isEmpty()) {
         return helper.loadingInfo();
      }
      if ("helfer".equals(config.speechEngine)) {
         return Languages.HELPER.contains(language)
            ? "Sprach-Helfer läuft nicht (in Chinese-Minecraft starten, Adresse " + config.speechHelperUrl + ")"
            : "Der Sprach-Helfer kann kein " + Languages.name(language) + " – Spracherkennung auf openrouter stellen";
      }
      return "Kein OpenRouter-Key und kein Sprach-Helfer";
   }

   public static boolean singleplayer(Minecraft mc) {
      return mc.getSingleplayerServer() != null;
   }

   public static void message(Minecraft mc, String text) {
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal(text));
      }
   }

   public static String keyName(KeyMapping key) {
      return key.getTranslatedKeyMessage().getString();
   }
}
