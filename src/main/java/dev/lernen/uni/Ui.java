package dev.lernen.uni;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Kleine Zeichenhilfen für die Fenster. Farben immer mit Alpha (0xFF……), sonst bleibt Text unsichtbar. */
final class Ui {
   static final int WHITE = 0xFFFFFFFF;
   static final int GREY = 0xFFBBBBBB;
   static final int DARK_GREY = 0xFF888888;
   static final int GOLD = 0xFFFFC75F;
   static final int GREEN = 0xFF7DFF8A;
   static final int RED = 0xFFFF7070;
   static final int YELLOW = 0xFFFFEE88;
   static final int BLUE = 0xFF8FD3FF;
   static final int LINE = 0xFF6A5ACD;

   private Ui() {
   }

   static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
      g.fill(x0, y0, x1, y1, 0xC8101018);
      frame(g, x0, y0, x1, y1, 0xAA6A5ACD);
   }

   static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb) {
      g.fill(x0, y0, x1, y0 + 1, argb);
      g.fill(x0, y1 - 1, x1, y1, argb);
      g.fill(x0, y0, x0 + 1, y1, argb);
      g.fill(x1 - 1, y0, x1, y1, argb);
   }

   /** Ein Textblock, umgebrochen auf die Breite; Leerzeilen im Text bleiben erhalten. */
   static void wrap(Font font, List<Line> out, String text, int width, int color) {
      if (text == null) {
         return;
      }
      for (String paragraph : text.split("\n", -1)) {
         if (paragraph.isBlank()) {
            out.add(new Line(FormattedCharSequence.EMPTY, color));
            continue;
         }
         for (FormattedCharSequence seq : font.split(Component.literal(paragraph), width)) {
            out.add(new Line(seq, color));
         }
      }
   }

   static void wrap(Font font, List<Line> out, Component text, int width, int color) {
      for (FormattedCharSequence seq : font.split(text, width)) {
         out.add(new Line(seq, color));
      }
   }

   static List<Line> lines() {
      return new ArrayList<>();
   }

   static void centered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
      g.text(font, Component.literal(text), cx - font.width(text) / 2, y, color, true);
   }

   static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
      g.text(font, Component.literal(text), x, y, color, true);
   }

   static String clock(long ms) {
      long s = Math.max(0L, ms) / 1000L;
      return s / 60 + ":" + String.format("%02d", s % 60);
   }

   record Line(FormattedCharSequence text, int color) {
   }
}
