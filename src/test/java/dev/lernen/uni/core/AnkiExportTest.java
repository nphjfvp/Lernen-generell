package dev.lernen.uni.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnkiExportTest {
   @TempDir
   Path dir;

   WorldData data() {
      WorldData d = WorldData.load(this.dir.resolve("unilernen/welt.json"));
      WorldData.Source s = new WorldData.Source();
      s.file = "Blatt 1.pdf";
      s.subject = "Analysis I";
      s.status = "fertig";
      d.sources.add(s);
      WorldData.Task t = new WorldData.Task();
      t.id = "a";
      t.source = "Blatt 1.pdf";
      t.title = "A1";
      t.topic = "Integrale";
      t.text = "Berechne ∫₀¹ x² dx.\nBegründe\tkurz.";
      t.solution = "1/3 <weil>";
      d.tasks.add(t);
      WorldData.Task m = new WorldData.Task();
      m.id = "s";
      m.source = "Blatt 1.pdf";
      m.kind = "stoff";
      m.topic = "Stetigkeit";
      m.text = "ε-δ-Definition";
      d.tasks.add(m);
      WorldData.HistoryEntry h = new WorldData.HistoryEntry();
      h.question = "Berechne 5·6.";
      h.solution = "30";
      h.solved = true;
      d.history.add(h);
      WorldData.HistoryEntry w = new WorldData.HistoryEntry();
      w.question = "Leite x³ ab.";
      w.solution = "3x²";
      w.wrongAttempts = 2;
      w.solved = true;
      d.history.add(w);
      WorldData.HistoryEntry sp = new WorldData.HistoryEntry();
      sp.kind = "sprechen";
      sp.language = "zh";
      sp.question = "我在挖泥土。";
      sp.reading = "wǒ zài wā ní tǔ";
      sp.translation = "Ich grabe die Erde.";
      d.history.add(sp);
      return d;
   }

   @Test
   void buildsImportableFile() {
      String text = AnkiExport.build(this.data(), new AnkiExport.Options(), "Uni");
      List<String> lines = text.lines().toList();
      assertEquals("#separator:tab", lines.get(0));
      assertTrue(lines.contains("#deck column:3"));
      List<String> cards = lines.stream().filter(l -> !l.startsWith("#")).toList();
      assertEquals(5, cards.size());
      for (String c : cards) {
         assertEquals(4, c.split("\t", -1).length, "genau 4 Spalten: " + c);
      }
      String first = cards.getFirst();
      assertTrue(first.contains("Berechne ∫₀¹ x² dx.<br>Begründe    kurz."), first);
      assertTrue(first.contains("1/3 &lt;weil&gt;"));
      assertTrue(first.contains("\tUni::Analysis I\t"));
      assertTrue(cards.stream().anyMatch(c -> c.contains("Sprechen Chinesisch") && c.contains("wǒ zài wā ní tǔ")));
   }

   @Test
   void onlyMistakesAndWrite() throws Exception {
      AnkiExport.Options o = new AnkiExport.Options();
      o.pdfTasks = false;
      o.speaking = false;
      o.onlyMistakes = true;
      String text = AnkiExport.build(this.data(), o, "");
      assertTrue(text.contains("Leite x³ ab."));
      assertFalse(text.contains("Berechne 5·6."));
      AnkiExport.Result r = AnkiExport.write(this.data(), new AnkiExport.Options(), "Uni");
      assertEquals(5, r.cards());
      assertTrue(Files.readString(r.file()).startsWith("#separator:tab"));
   }
}
