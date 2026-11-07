package dev.lernen.uni.core;

import java.util.concurrent.CompletableFuture;

/** Eine Spracherkennung: Aufnahme starten, merken wann jemand fertig gesprochen hat, auswerten. */
public interface SpeechInput {
   /** Für die Anzeige, z. B. "Sprach-Helfer" oder "OpenRouter (google/gemini-2.5-flash)". */
   String name();

   CompletableFuture<Void> start();

   /** Regelmäßig aufrufen (jeden Tick), solange aufgenommen wird. */
   default void poll() {
   }

   /** Ob nach dem Sprechen genug Stille kam, dass die Aufnahme enden kann. */
   boolean finished();

   /** Beendet die Aufnahme und liefert, was gehört wurde. */
   CompletableFuture<SpeechCompare.Heard> stop(String language);

   /** Bricht eine laufende Aufnahme ab, ohne auszuwerten. */
   void cancel();

   /** So viel Stille hängt am Ende einer von selbst beendeten Aufnahme (zählt nicht zum Sprechtempo). */
   default double trailingSilence() {
      return 0.9;
   }
}
