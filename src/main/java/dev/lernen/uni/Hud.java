package dev.lernen.uni;

import dev.lernen.uni.core.StudyManager;
import dev.lernen.uni.core.WorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Die kleine Anzeige oben: Zeit bis zur nächsten Aufgabe und Zahl der Booster. */
public final class Hud {
   public static final Identifier LAYER = Identifier.fromNamespaceAndPath(UniLernen.MOD_ID, "hud");

   private Hud() {
   }

   public static void render(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      String pos = UniLernen.config.hudPosition;
      StudyManager s = UniLernen.study;
      if ("aus".equals(pos) || s == null || mc.player == null || mc.gui.screen() != null) {
         return;
      }
      WorldData data = s.data();
      if (data == null) {
         return;
      }
      String blocker = s.blocker();
      String line;
      int color;
      if (!blocker.isEmpty()) {
         line = "✎ " + blocker + " (" + UniLernen.keyName(UniLernen.keyMenu) + ")";
         color = blocker.endsWith("aus") ? Ui.DARK_GREY : 0xFFFFAA55;
      } else {
         long ms = s.msUntilNext();
         line = ms == 0L ? "✎ Aufgabe kommt gleich …" : "✎ Aufgabe in " + Ui.clock(ms);
         color = ms < 30_000L ? Ui.YELLOW : Ui.WHITE;
      }
      String boosters = "⚡ " + data.boosterTotal() + " Booster (" + UniLernen.keyName(UniLernen.keyBoosters) + ")";
      int w = Math.max(mc.font.width(line), mc.font.width(boosters));
      int width = g.guiWidth();
      int x = "oben_links".equals(pos) ? 4 : width - w - 4;
      int y = 4;
      g.fill(x - 3, y - 3, x + w + 3, y + 21, 0x66000000);
      Ui.text(g, mc.font, line, x, y, color);
      Ui.text(g, mc.font, boosters, x, y + 10, data.boosterTotal() > 0 ? Ui.GOLD : Ui.DARK_GREY);
   }
}
