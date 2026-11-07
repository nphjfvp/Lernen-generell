package dev.lernen.uni.core;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Nimmt im Spiel selbst über das Standard-Mikrofon auf (16 kHz, mono) und lässt die Aufnahme dann von einem
 * Audio-Modell über OpenRouter mitschreiben. Merkt wie der Sprach-Helfer selbst, wann man fertig ist:
 * erst Sprechen, dann 0,9 s Stille.
 */
public final class MicSpeech implements SpeechInput {
   static final int RATE = 16_000;
   private static final double FRAME_S = 0.05;
   private static final double SPEECH_MIN_S = 0.25;
   private static final double SILENCE_END_S = 0.9;

   private final Function<byte[], CompletableFuture<SpeechCompare.Heard>> transcribe;
   private final String modelName;
   private volatile TargetDataLine line;
   private volatile boolean done;
   private volatile double peak;
   private ByteArrayOutputStream pcm = new ByteArrayOutputStream();
   private Thread reader;
   private long startedAt;

   /** @param transcribe macht aus einer WAV-Datei den gehörten Text (Sprache schon festgelegt) */
   public MicSpeech(String modelName, Function<byte[], CompletableFuture<SpeechCompare.Heard>> transcribe) {
      this.modelName = modelName;
      this.transcribe = transcribe;
   }

   @Override
   public String name() {
      return "Mikrofon + OpenRouter (" + this.modelName + ")";
   }

   @Override
   public synchronized CompletableFuture<Void> start() {
      if (this.line != null) {
         return CompletableFuture.completedFuture(null);
      }
      try {
         AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
         TargetDataLine l = (TargetDataLine)AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
         l.open(format, (int)(RATE * 2 * FRAME_S) * 8);
         l.start();
         this.line = l;
         this.done = false;
         this.peak = 0.0;
         this.pcm = new ByteArrayOutputStream();
         this.startedAt = System.currentTimeMillis();
         this.reader = new Thread(() -> this.read(l), "unilernen-mikrofon");
         this.reader.setDaemon(true);
         this.reader.start();
         return CompletableFuture.completedFuture(null);
      } catch (Exception | LinkageError e) {
         return CompletableFuture.failedFuture(new IllegalStateException("Kein Mikrofon nutzbar: " + e.getMessage()
            + " (Mikrofon-Freigabe für Minecraft prüfen oder den Sprach-Helfer von Chinese-Minecraft starten)"));
      }
   }

   private void read(TargetDataLine l) {
      byte[] frame = new byte[(int)(RATE * FRAME_S) * 2];
      double floor = 0.0;
      int floorFrames = 0;
      double speech = 0.0;
      double silence = 0.0;
      while (l.isOpen()) {
         int n = l.read(frame, 0, frame.length);
         if (n <= 0) {
            continue;
         }
         synchronized (this) {
            this.pcm.write(frame, 0, n);
         }
         double rms = rms(frame, n);
         this.peak = Math.max(this.peak, rms);
         if (floorFrames < 4) {
            floor = Math.max(floor, rms);
            floorFrames++;
            continue;
         }
         boolean talking = rms > Math.max(3.0 * floor, 0.004);
         if (talking) {
            speech += FRAME_S;
            silence = 0.0;
         } else if (speech >= SPEECH_MIN_S) {
            silence += FRAME_S;
            if (silence >= SILENCE_END_S) {
               this.done = true;
            }
         }
      }
   }

   static double rms(byte[] b, int n) {
      ByteBuffer buf = ByteBuffer.wrap(b, 0, n).order(ByteOrder.LITTLE_ENDIAN);
      double sum = 0.0;
      int count = n / 2;
      for (int i = 0; i < count; i++) {
         double v = buf.getShort() / 32768.0;
         sum += v * v;
      }
      return count == 0 ? 0.0 : Math.sqrt(sum / count);
   }

   @Override
   public boolean finished() {
      return this.done;
   }

   private synchronized byte[] close() {
      TargetDataLine l = this.line;
      this.line = null;
      if (l != null) {
         l.stop();
         l.close();
      }
      return this.pcm.toByteArray();
   }

   @Override
   public CompletableFuture<SpeechCompare.Heard> stop(String language) {
      double seconds = (System.currentTimeMillis() - this.startedAt) / 1000.0;
      byte[] data = this.close();
      if (this.peak < 0.002 || data.length < RATE / 5) {
         return CompletableFuture.completedFuture(new SpeechCompare.Heard("", null, seconds, true));
      }
      return this.transcribe.apply(wav(data)).thenApply(h -> new SpeechCompare.Heard(h.text(), h.reading(), seconds, h.silent()));
   }

   @Override
   public void cancel() {
      this.close();
   }

   /** 16-bit-PCM als WAV-Datei. */
   static byte[] wav(byte[] pcm) {
      ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
      b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36 + pcm.length);
      b.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      b.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
      b.putInt(RATE).putInt(RATE * 2).putShort((short)2).putShort((short)16);
      b.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
      return b.array();
   }
}
