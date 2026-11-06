package dev.lernen.uni.neoforge;

import dev.lernen.uni.Hud;
import dev.lernen.uni.SettingsScreen;
import dev.lernen.uni.UniLernen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(value = UniLernen.MOD_ID, dist = {Dist.CLIENT})
public final class UniLernenEntry {
   public UniLernenEntry(IEventBus modBus, ModContainer container) {
      UniLernen.init(FMLPaths.CONFIGDIR.get());
      modBus.addListener((RegisterKeyMappingsEvent event) -> UniLernen.keys().forEach(event::register));
      modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(Hud.LAYER, (graphics, delta) -> Hud.render(graphics)));
      container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)(modContainer, parent) -> new SettingsScreen(parent));
      IEventBus game = NeoForge.EVENT_BUS;
      game.addListener((ClientTickEvent.Post event) -> UniLernen.tick(Minecraft.getInstance()));
      game.addListener((GameShuttingDownEvent event) -> {
         if (UniLernen.study != null && UniLernen.study.data() != null) {
            UniLernen.study.data().save();
         }
      });
   }
}
