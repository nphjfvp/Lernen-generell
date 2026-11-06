package dev.lernen.uni.core;

import java.util.function.Consumer;

/** Kleines Log, damit der Kern ohne Minecraft getestet werden kann. Die Mod hängt hier ihren Logger ein. */
public final class Log {
   public static Consumer<String> info = s -> System.out.println("[unilernen] " + s);
   public static Consumer<String> warn = s -> System.err.println("[unilernen] WARN " + s);

   private Log() {
   }

   public static void info(String s) {
      info.accept(s);
   }

   public static void warn(String s) {
      warn.accept(s);
   }
}
