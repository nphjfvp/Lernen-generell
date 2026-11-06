package dev.lernen.uni;

import dev.lernen.uni.core.Boosters;
import dev.lernen.uni.core.WorldData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/** Das Booster-Inventar: getrennt vom normalen Inventar, ein Klick setzt einen Booster ein. */
public final class BoosterScreen extends Screen {
   private static final int BUTTON_W = 150;
   @Nullable
   private final Screen parent;
   private int page;
   private String info = "";

   public BoosterScreen(@Nullable Screen parent) {
      super(Component.literal("Booster-Inventar"));
      this.parent = parent;
   }

   private List<Boosters.Booster> owned() {
      List<Boosters.Booster> list = new ArrayList<>();
      WorldData data = UniLernen.study.data();
      if (data != null) {
         for (Boosters.Booster b : Boosters.ALL) {
            if (data.boosters.getOrDefault(b.id(), 0) > 0) {
               list.add(b);
            }
         }
      }
      return list;
   }

   private int columns() {
      return Math.max(1, Math.min(3, (this.width - 32 + 6) / (BUTTON_W + 6)));
   }

   private int rows() {
      return Math.max(1, (this.height - 40 - 64) / 24);
   }

   @Override
   protected void init() {
      WorldData data = UniLernen.study.data();
      List<Boosters.Booster> owned = this.owned();
      int cols = this.columns();
      int perPage = cols * this.rows();
      int pages = Math.max(1, (owned.size() + perPage - 1) / perPage);
      this.page = Math.min(this.page, pages - 1);
      int x0 = this.width / 2 - (cols * (BUTTON_W + 6) - 6) / 2;
      int y0 = 40;
      for (int i = 0; i < perPage && this.page * perPage + i < owned.size(); i++) {
         Boosters.Booster b = owned.get(this.page * perPage + i);
         int n = data == null ? 0 : data.boosters.getOrDefault(b.id(), 0);
         Button button = Button.builder(Component.literal(b.name() + "  ×" + n), x -> this.use(b))
            .bounds(x0 + i % cols * (BUTTON_W + 6), y0 + i / cols * 24, BUTTON_W, 20)
            .build();
         button.setTooltip(Tooltip.create(Component.literal(b.description() + "\nDauer: " + b.duration() + "\nKlicken zum Einsetzen")));
         this.addRenderableWidget(button);
      }
      int by = this.height - 28;
      if (pages > 1) {
         Button prev = this.addRenderableWidget(Button.builder(Component.literal("◀"), x -> {
            this.page--;
            this.rebuildWidgets();
         }).bounds(this.width / 2 - 104, by, 20, 20).build());
         prev.active = this.page > 0;
         Button next = this.addRenderableWidget(Button.builder(Component.literal("▶"), x -> {
            this.page++;
            this.rebuildWidgets();
         }).bounds(this.width / 2 + 84, by, 20, 20).build());
         next.active = this.page < pages - 1;
      }
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), x -> this.onClose()).bounds(this.width / 2 - 75, by, 150, 20).build());
   }

   private void use(Boosters.Booster b) {
      WorldData data = UniLernen.study.data();
      if (data == null) {
         return;
      }
      if (!UniLernen.singleplayer(this.minecraft)) {
         this.info = "§cBooster gehen nur im Einzelspieler.";
         return;
      }
      if (!data.takeBooster(b.id())) {
         return;
      }
      if (GameActions.applyBooster(this.minecraft, b)) {
         data.stats.boostersUsed++;
         data.save();
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BREWING_STAND_BREW, 1.2F, 0.8F));
         this.info = "§a" + b.name() + " eingesetzt (" + b.duration() + ").";
      } else {
         data.addBooster(b.id());
         this.info = "§cKonnte den Booster nicht einsetzen.";
      }
      this.rebuildWidgets();
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (UniLernen.keyBoosters.matches(event)) {
         this.onClose();
         return true;
      }
      return super.keyPressed(event);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.centered(g, this.font, "⚡ Booster-Inventar", this.width / 2, 10, Ui.GOLD);
      Ui.centered(g, this.font, "Für jede richtig gelöste Aufgabe gibt es einen Booster. Klick = einsetzen.", this.width / 2, 22, Ui.DARK_GREY);
      if (this.owned().isEmpty()) {
         Ui.centered(g, this.font, "Noch keine Booster – löse Aufgaben, um welche zu bekommen!", this.width / 2, this.height / 2 - 10, Ui.GREY);
      }
      if (!this.info.isEmpty()) {
         Ui.centered(g, this.font, this.info, this.width / 2, this.height - 42, Ui.WHITE);
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
