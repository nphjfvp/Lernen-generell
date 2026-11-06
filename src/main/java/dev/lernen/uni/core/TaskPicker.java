package dev.lernen.uni.core;

import java.util.List;
import java.util.Random;

/** Wählt die nächste Vorlage: oft falsch gelöste öfter, gerade gestellte seltener. */
public final class TaskPicker {
   private TaskPicker() {
   }

   static double weight(WorldData.Task t, long playMs) {
      double w = t.isMaterialOnly() ? 0.6 : 1.0;
      if (t.asked == 0) {
         w *= 2.0;
      }
      w *= 1.0 + 1.5 * t.wrong;
      w /= 1.0 + 0.7 * t.correct;
      if (t.lastAskedAt >= 0L) {
         long since = playMs - t.lastAskedAt;
         if (since < 30 * 60_000L) {
            w *= 0.15;
         } else if (since < 2 * 3_600_000L) {
            w *= 0.5;
         }
      }
      return Math.max(0.02, w);
   }

   public static WorldData.Task pick(List<WorldData.Task> tasks, long playMs, Random random) {
      if (tasks.isEmpty()) {
         return null;
      }
      double total = 0.0;
      for (WorldData.Task t : tasks) {
         total += weight(t, playMs);
      }
      double r = random.nextDouble() * total;
      for (WorldData.Task t : tasks) {
         r -= weight(t, playMs);
         if (r < 0.0) {
            return t;
         }
      }
      return tasks.getLast();
   }

   /** Der Stil für die nächste Aufgabe. Stoff-Abschnitte haben keine Originalaufgabe. */
   public static String style(UniConfig c, WorldData.Task t, Random random) {
      String s = c.style;
      if ("gemischt".equals(s)) {
         double r = random.nextDouble();
         s = r < 0.4 ? "original" : r < 0.8 ? "aehnlich" : "kurz";
      }
      if ("original".equals(s) && t.isMaterialOnly()) {
         s = "kurz";
      }
      return s;
   }
}
