package dev.lernen.uni.core;

import java.util.List;
import java.util.Random;

/**
 * Die Booster, die man für richtige Antworten bekommt. Sie liegen im Booster-Inventar der Welt und
 * wirken erst, wenn man sie einsetzt. {@code effect} ist der Name des Minecraft-Effekts.
 */
public final class Boosters {
   public record Booster(String id, String name, String effect, int seconds, int level, int weight, String description) {
      public String duration() {
         if (this.seconds <= 0) {
            return "sofort";
         }
         return this.seconds % 60 == 0 ? this.seconds / 60 + " min" : this.seconds >= 60 ? this.seconds / 60 + ":" + String.format("%02d", this.seconds % 60) + " min" : this.seconds + " s";
      }
   }

   public static final List<Booster> ALL = List.of(
      new Booster("extra_herzen", "5 Extra-Herzen", "absorption", 300, 3, 8, "5 goldene Zusatzherzen für 5 Minuten (Absorption)"),
      new Booster("mehr_leben", "Mehr Leben", "health_boost", 300, 2, 6, "+4 Herzen Maximalleben für 5 Minuten, vorher voll geheilt"),
      new Booster("feuerfest", "Feuerresistenz", "fire_resistance", 180, 1, 8, "Immun gegen Feuer und Lava für 3 Minuten"),
      new Booster("heilung", "Sofortheilung", "instant_health", 0, 2, 7, "Heilt sofort 4 Herzen"),
      new Booster("regeneration", "Regeneration", "regeneration", 30, 2, 7, "Regeneration II für 30 Sekunden"),
      new Booster("tempo", "Tempo", "speed", 180, 2, 8, "Schneller laufen (Tempo II) für 3 Minuten"),
      new Booster("eile", "Eile", "haste", 180, 2, 8, "Schneller abbauen (Eile II) für 3 Minuten"),
      new Booster("staerke", "Stärke", "strength", 120, 2, 6, "Mehr Schaden (Stärke II) für 2 Minuten"),
      new Booster("resistenz", "Resistenz", "resistance", 120, 2, 5, "Weniger Schaden (Resistenz II) für 2 Minuten"),
      new Booster("nachtsicht", "Nachtsicht", "night_vision", 300, 1, 7, "Im Dunkeln sehen für 5 Minuten"),
      new Booster("wasseratmung", "Wasseratmung", "water_breathing", 300, 1, 5, "Unter Wasser atmen für 5 Minuten"),
      new Booster("sprungkraft", "Sprungkraft", "jump_boost", 180, 2, 5, "Höher springen für 3 Minuten"),
      new Booster("sanfter_fall", "Sanfter Fall", "slow_falling", 120, 1, 5, "Langsam fallen, kein Fallschaden, für 2 Minuten"),
      new Booster("saettigung", "Satt", "saturation", 1, 10, 6, "Hunger sofort gestillt"),
      new Booster("glueck", "Glück", "luck", 300, 2, 3, "Bessere Beute beim Angeln und in Truhen für 5 Minuten"),
      new Booster("unsichtbar", "Unsichtbarkeit", "invisibility", 120, 1, 3, "Unsichtbar für 2 Minuten"),
      new Booster("delfin", "Delfingnade", "dolphins_grace", 120, 1, 3, "Schnell schwimmen für 2 Minuten"),
      new Booster("meereskraft", "Meereskraft", "conduit_power", 180, 1, 3, "Unter Wasser atmen, sehen und schneller abbauen für 3 Minuten"),
      new Booster("held", "Held des Dorfes", "hero_of_the_village", 600, 2, 2, "Bessere Preise bei Dorfbewohnern für 10 Minuten")
   );

   private Boosters() {
   }

   public static Booster byId(String id) {
      for (Booster b : ALL) {
         if (b.id().equals(id)) {
            return b;
         }
      }
      return null;
   }

   /** Ein zufälliger Booster, häufige öfter als seltene. */
   public static Booster random(Random random) {
      int total = 0;
      for (Booster b : ALL) {
         total += b.weight();
      }
      int r = random.nextInt(total);
      for (Booster b : ALL) {
         r -= b.weight();
         if (r < 0) {
            return b;
         }
      }
      return ALL.getFirst();
   }
}
