package dev.lernen.uni.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MathTextTest {
   @Test
   void fractionsRootsPowers() {
      assertEquals("1/2 + x²", MathText.toUnicode("$\\frac{1}{2} + x^2$"));
      assertEquals("(x+1)/(x-1)", MathText.toUnicode("\\frac{x+1}{x-1}"));
      assertEquals("√(x+1) · √2", MathText.toUnicode("\\sqrt{x+1} \\cdot \\sqrt{2}"));
      assertEquals("e^(-x/2)", MathText.toUnicode("e^{-x/2}"));
      assertEquals("x¹⁰ + aₙ", MathText.toUnicode("x^{10} + a_n"));
      assertEquals("x¹⁰", MathText.toUnicode("x^10"));
   }

   @Test
   void symbolsAndSets() {
      assertEquals("∀ ε > 0 ∃ δ > 0", MathText.toUnicode("\\forall \\varepsilon > 0 \\exists \\delta > 0"));
      assertEquals("f: ℝ → ℝ", MathText.toUnicode("f: \\mathbb{R} \\to \\mathbb{R}"));
      assertEquals("∑ k = n(n+1)/2", MathText.toUnicode("\\sum k = \\frac{n(n+1)}{2}"));
      assertEquals("{1, 2, 3}", MathText.toUnicode("\\{1, 2, 3\\}"));
   }

   @Test
   void plainTextStays() {
      assertEquals("Berechne die Ableitung von f(x) = x² · sin(x).", MathText.toUnicode("Berechne die Ableitung von f(x) = x² · sin(x)."));
      assertEquals("", MathText.toUnicode(null));
   }
}
