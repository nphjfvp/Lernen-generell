package dev.lernen.uni;

import dev.lernen.uni.core.Boosters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Was auf dem (eingebauten) Server passiert: Gegenstände löschen und Booster-Effekte geben. Geht nur im
 * Einzelspieler, weil ein fremder Server die Mod nicht kennt.
 */
final class GameActions {
   private static final Random RANDOM = new Random();

   private GameActions() {
   }

   /** Löscht einen zufälligen Stapel (oder nur einen Gegenstand davon) aus dem Inventar. */
   static void deleteRandomItem(Minecraft mc, boolean single) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server == null || mc.player == null) {
         UniLernen.message(mc, "§7(Gegenstände löschen geht nur im Einzelspieler.)");
         return;
      }
      UUID uuid = mc.player.getUUID();
      server.execute(() -> {
         ServerPlayer p = server.getPlayerList().getPlayer(uuid);
         if (p == null) {
            return;
         }
         Inventory inventory = p.getInventory();
         List<Integer> filled = new ArrayList<>();
         for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
               filled.add(i);
            }
         }
         if (filled.isEmpty()) {
            p.sendSystemMessage(Component.literal("§c✘ Falsch! §7Dein Inventar ist leer – diesmal Glück gehabt."));
            return;
         }
         int slot = filled.get(RANDOM.nextInt(filled.size()));
         ItemStack stack = inventory.getItem(slot);
         int count = single ? 1 : stack.getCount();
         Component name = stack.getHoverName().copy();
         inventory.removeItem(slot, count);
         p.containerMenu.broadcastChanges();
         p.inventoryMenu.broadcastChanges();
         p.sendSystemMessage(Component.literal("§c✘ Falsch! §7Gelöscht: §f" + count + "× ").append(name));
         mc.execute(() -> UniLernen.study.onItemsLost(1));
      });
   }

   /** Setzt einen Booster ein. false, wenn das hier nicht geht (kein Einzelspieler). */
   static boolean applyBooster(Minecraft mc, Boosters.Booster booster) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server == null || mc.player == null) {
         return false;
      }
      UUID uuid = mc.player.getUUID();
      server.execute(() -> {
         ServerPlayer p = server.getPlayerList().getPlayer(uuid);
         if (p == null) {
            return;
         }
         switch (booster.effect()) {
            case "instant_health" -> p.heal(4.0F * booster.level());
            case "health_boost" -> {
               p.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, booster.seconds() * 20, booster.level() - 1));
               p.setHealth(p.getMaxHealth());
            }
            case "absorption" -> {
               p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, booster.seconds() * 20, booster.level() - 1));
               // Absorption III gäbe 6 Herzen, der Booster verspricht 5.
               p.setAbsorptionAmount(10.0F);
            }
            default -> {
               Holder<MobEffect> effect = effect(booster.effect());
               if (effect != null) {
                  p.addEffect(new MobEffectInstance(effect, Math.max(1, booster.seconds()) * 20, booster.level() - 1));
               }
            }
         }
         p.sendSystemMessage(Component.literal("§b⚡ Booster eingesetzt: §f" + booster.name() + " §7(" + booster.duration() + ")"));
      });
      return true;
   }

   private static Holder<MobEffect> effect(String name) {
      return switch (name) {
         case "fire_resistance" -> MobEffects.FIRE_RESISTANCE;
         case "regeneration" -> MobEffects.REGENERATION;
         case "speed" -> MobEffects.SPEED;
         case "haste" -> MobEffects.HASTE;
         case "strength" -> MobEffects.STRENGTH;
         case "resistance" -> MobEffects.RESISTANCE;
         case "night_vision" -> MobEffects.NIGHT_VISION;
         case "water_breathing" -> MobEffects.WATER_BREATHING;
         case "jump_boost" -> MobEffects.JUMP_BOOST;
         case "slow_falling" -> MobEffects.SLOW_FALLING;
         case "saturation" -> MobEffects.SATURATION;
         case "luck" -> MobEffects.LUCK;
         case "invisibility" -> MobEffects.INVISIBILITY;
         case "dolphins_grace" -> MobEffects.DOLPHINS_GRACE;
         case "conduit_power" -> MobEffects.CONDUIT_POWER;
         case "hero_of_the_village" -> MobEffects.HERO_OF_THE_VILLAGE;
         case "health_boost" -> MobEffects.HEALTH_BOOST;
         case "absorption" -> MobEffects.ABSORPTION;
         default -> null;
      };
   }
}
