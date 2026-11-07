package dev.lernen.uni.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeakingTest {
   static WorldData.Challenge chinese() {
      WorldData.SpeechTask s = new WorldData.SpeechTask();
      s.language = "zh";
      s.sentence = "我在挖泥土。";
      s.translation = "Ich grabe die Erde.";
      s.words.add(new WorldData.SpeechWord("我", "wǒ", "ich"));
      s.words.add(new WorldData.SpeechWord("在", "zài", "gerade"));
      s.words.add(new WorldData.SpeechWord("挖", "wā", "graben"));
      s.words.add(new WorldData.SpeechWord("泥土", "ní tǔ", "Erde"));
      WorldData.Challenge c = new WorldData.Challenge();
      c.question = s.sentence;
      c.speech = s;
      return c;
   }

   static SpeechCompare.Heard heard(String text, String pinyin) {
      return new SpeechCompare.Heard(text, pinyin.isEmpty() ? List.of() : List.of(pinyin.split(" ")), 2.0, false);
   }

   @Test
   void chineseComparesPinyinWithoutTones() {
      WorldData.Challenge c = chinese();
      // Falsche Schriftzeichen, aber richtig ausgesprochen: zählt.
      double[] s = SpeechCompare.score(c.speech.words, "zh", heard("我再哇泥图", "wǒ zāi wà ní tú"));
      assertArrayEquals(new double[]{1, 1, 1, 1}, s, 1e-9);
      double[] t = SpeechCompare.score(c.speech.words, "zh", heard("我在", "wo zai"));
      assertEquals(1.0, t[0], 1e-9);
      assertEquals(0.0, t[3], 1e-9);
      // ku statt tu: halb.
      double[] u = SpeechCompare.score(c.speech.words, "zh", heard("", "wo zai wa ni ku"));
      assertEquals(0.75, u[3], 1e-9);
   }

   @Test
   void latinWordsTolerateSmallSlips() {
      List<WorldData.SpeechWord> words = List.of(new WorldData.SpeechWord("¿Dónde", "", ""), new WorldData.SpeechWord("está", "", ""),
         new WorldData.SpeechWord("la", "", ""), new WorldData.SpeechWord("biblioteca", "", ""));
      double[] s = SpeechCompare.score(words, "es", heard("donde esta la bibloteca", ""));
      assertTrue(s[0] == 1.0 && s[1] == 1.0 && s[2] == 1.0);
      assertTrue(s[3] >= SpeechCompare.WORD_OK, "ein fehlender Buchstabe ist noch richtig: " + s[3]);
      double[] t = SpeechCompare.score(words, "es", heard("donde la casa", ""));
      assertEquals(0.0, t[1], 1e-9);
      assertEquals(0.0, t[3], 1e-9);
   }

   @Test
   void threeStages() {
      WorldData.Challenge c = chinese();
      SpeakingSession s = new SpeakingSession(c, 80);
      assertEquals(SpeakingSession.WORDS, s.stage());
      assertEquals("我", s.expectedText());

      s.apply(heard("我", "wo"), 1.0);
      assertEquals(SpeakingSession.MARK_OK, s.mark(0));
      assertEquals(1, s.word());

      s.apply(heard("菜", "cai"), 1.0);
      assertEquals(SpeakingSession.MARK_WRONG, s.mark(1));
      assertEquals(1, s.word(), "ein falsches Wort wird wiederholt");
      assertFalse(s.canSkipWord());
      s.apply(heard("在", "zai"), 1.0);
      s.apply(heard("挖", "wa"), 1.0);
      s.apply(heard("泥土", "ni tu"), 1.0);
      assertEquals(SpeakingSession.SLOW, s.stage());
      assertEquals(SpeakingSession.MARK_NONE, s.mark(0));

      // 5 Silben: langsam höchstens 5/1.0 + 1.5 = 6.5 s
      assertEquals(6.5, s.timeLimit(), 1e-9);
      assertTrue(s.apply(heard("", "wo zai"), 3.0), "zu wenig Wörter");
      assertEquals(SpeakingSession.SLOW, s.stage());
      assertTrue(s.apply(heard("", "wo zai wa ni tu"), 9.0), "zu langsam");
      assertFalse(s.apply(heard("", "wo zai wa ni tu"), 5.0));
      assertEquals(SpeakingSession.NORMAL, s.stage());

      // normal: 5/2.2 + 1.5 ≈ 3.77 s
      assertTrue(s.apply(heard("", "wo zai wa ni tu"), 5.0));
      assertFalse(s.apply(heard("", "wo zai wa ni tu"), 3.0));
      assertTrue(s.done());
   }

   @Test
   void silenceIsNotAMistake() {
      WorldData.Challenge c = chinese();
      SpeakingSession s = new SpeakingSession(c, 80);
      assertFalse(s.apply(new SpeechCompare.Heard("", List.of(), 2.0, true), 2.0));
      assertEquals(SpeakingSession.MARK_NONE, s.mark(0));
      assertTrue(s.feedback().startsWith("Nichts gehört"));
   }

   @Test
   void skipStubbornWordAndResume() {
      WorldData.Challenge c = chinese();
      SpeakingSession s = new SpeakingSession(c, 80);
      for (int i = 0; i < SpeakingSession.SKIP_WORD_AFTER; i++) {
         s.apply(heard("你", "ni"), 1.0);
      }
      assertTrue(s.canSkipWord());
      s.skipWord();
      assertEquals(SpeakingSession.MARK_SKIPPED, s.mark(0));
      assertEquals(1, c.speechWord);
      // Nach dem Neuladen geht es beim selben Wort weiter.
      SpeakingSession again = new SpeakingSession(c, 80);
      assertEquals(1, again.word());
      assertEquals(SpeakingSession.MARK_OK, again.mark(0));
   }

   @Test
   void helperReplyAndWav() {
      SpeechCompare.Heard h = HelperSpeech.parse(200, "{\"text\":\"我在\",\"reading\":[\"wǒ\",\"zài\"],\"seconds\":1.4,\"silent\":false}");
      assertEquals("我在", h.text());
      assertEquals(List.of("wǒ", "zài"), h.reading());
      assertEquals(1.4, h.seconds(), 1e-9);
      byte[] wav = MicSpeech.wav(new byte[]{1, 0, 2, 0});
      assertEquals(48, wav.length);
      assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
   }

   @Test
   void parsesGeneratedSentence() {
      var o = AiJson.object("{\"satz\":\"我在挖泥土。\",\"uebersetzung\":\"Ich grabe.\",\"woerter\":[{\"text\":\"我\",\"lesung\":\"wǒ\"},"
         + "{\"text\":\"泥土。\",\"lesung\":\"ní tǔ\"}]}");
      WorldData.Challenge c = OpenRouterAi.parseSpeaking(o, null, "zh");
      assertTrue(c.isSpeaking());
      assertEquals("泥土", c.speech.words.get(1).text, "Satzzeichen fliegen raus");
      assertEquals("wǒ ní tǔ", c.speech.reading());
      var e = AiJson.object("{\"satz\":\"I like maths.\",\"uebersetzung\":\"Ich mag Mathe.\"}");
      assertEquals(3, OpenRouterAi.parseSpeaking(e, null, "en").speech.words.size());
   }
}
