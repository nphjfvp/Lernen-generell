package dev.lernen.uni;

import dev.lernen.uni.core.StudyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Verbindet den Aufgaben-Ablauf mit dem Spiel. */
final class GameBridge implements StudyManager.Game {
   private final Minecraft mc;

   GameBridge(Minecraft mc) {
      this.mc = mc;
   }

   @Override
   public void openChallenge() {
      if (this.mc.player != null) {
         var c = UniLernen.study.active();
         this.mc.gui.setScreen(c != null && c.isSpeaking() ? new SpeakScreen() : new TaskScreen());
      }
   }

   @Override
   public boolean challengeOpen() {
      return this.mc.gui.screen() instanceof TaskScreen || this.mc.gui.screen() instanceof SpeakScreen;
   }

   @Override
   public void penalize(String mode) {
      this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, 0.8F));
      GameActions.deleteRandomItem(this.mc, "einzeln".equals(mode));
   }

   @Override
   public void message(String text) {
      UniLernen.message(this.mc, text);
   }
}
