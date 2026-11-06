package dev.lernen.uni.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Was die Mod von der KI braucht. Im Spiel steckt OpenRouter dahinter, in den Tests eine Attrappe. */
public interface Ai {
   CompletableFuture<Extraction> extract(String fileName, byte[] pdf);

   /** Eine neue Aufgabe im Stil "aehnlich" oder "kurz" zur Vorlage. */
   CompletableFuture<WorldData.Challenge> generate(WorldData.Task task, String style, String subjects);

   CompletableFuture<Verdict> check(WorldData.Challenge challenge, String answer);

   CompletableFuture<String> hint(WorldData.Challenge challenge);

   CompletableFuture<String> solve(WorldData.Challenge challenge);

   record Extraction(String subject, String summary, List<WorldData.Task> tasks) {
   }

   record Verdict(boolean correct, String feedback) {
   }
}
