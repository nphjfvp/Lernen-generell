package dev.lernen.uni.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Macht aus LaTeX-Resten in KI-Texten lesbare Unicode-Mathematik, weil Minecraft kein LaTeX kann:
 * {@code \frac{a}{b}} → (a)/(b), {@code x^2} → x², {@code \alpha} → α, {@code \mathbb{R}} → ℝ.
 */
public final class MathText {
   private static final Map<String, String> SYMBOLS = new LinkedHashMap<>();
   private static final String SUP_FROM = "0123456789+-=()niaxyk";
   private static final String SUP_TO = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱᵃˣʸᵏ";
   private static final String SUB_FROM = "0123456789+-=()aeijknmx";
   private static final String SUB_TO = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑᵢⱼₖₙₘₓ";
   private static final Pattern COMMAND = Pattern.compile("\\\\([A-Za-z]+)");
   private static final Pattern FRAC = Pattern.compile("\\\\[dt]?frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}");
   private static final Pattern SQRT_N = Pattern.compile("\\\\sqrt\\s*\\[([^\\]]*)\\]\\s*\\{([^{}]*)\\}");
   private static final Pattern SQRT = Pattern.compile("\\\\sqrt\\s*\\{([^{}]*)\\}");
   private static final Pattern WRAPPER = Pattern.compile("\\\\(?:text|mathrm|textbf|mathbf|operatorname|mathit|boldsymbol|textit|emph)\\s*\\{([^{}]*)\\}");
   private static final Pattern BLACKBOARD = Pattern.compile("\\\\(?:mathbb|mathbf|Bbb)\\s*\\{?([A-Z])\\}?");
   private static final Pattern SUP_GROUP = Pattern.compile("\\^\\{([^{}]*)\\}");
   private static final Pattern SUP_ONE = Pattern.compile("\\^(\\d+|[a-zA-Z]|[+\\-])");
   private static final Pattern SUB_GROUP = Pattern.compile("_\\{([^{}]*)\\}");
   private static final Pattern SUB_ONE = Pattern.compile("(?<=[A-Za-z0-9)\\]])_(\\d+|[a-zA-Z])");
   private static final Pattern VEC = Pattern.compile("\\\\(?:vec|overrightarrow)\\s*\\{?([A-Za-z])\\}?");
   private static final Pattern BAR = Pattern.compile("\\\\(?:bar|overline)\\s*\\{([^{}]*)\\}");
   private static final Pattern HAT = Pattern.compile("\\\\hat\\s*\\{?([A-Za-z])\\}?");
   private static final Pattern ENVIRONMENT = Pattern.compile("\\\\(?:begin|end)\\s*\\{[^{}]*\\}");
   private static final Pattern DOLLARS = Pattern.compile("\\$\\$?|\\\\\\(|\\\\\\)|\\\\\\[|\\\\\\]");

   static {
      String[][] pairs = {
         {"alpha", "α"}, {"beta", "β"}, {"gamma", "γ"}, {"delta", "δ"}, {"epsilon", "ε"}, {"varepsilon", "ε"}, {"zeta", "ζ"},
         {"eta", "η"}, {"theta", "θ"}, {"vartheta", "ϑ"}, {"iota", "ι"}, {"kappa", "κ"}, {"lambda", "λ"}, {"mu", "μ"}, {"nu", "ν"},
         {"xi", "ξ"}, {"pi", "π"}, {"rho", "ρ"}, {"sigma", "σ"}, {"tau", "τ"}, {"upsilon", "υ"}, {"phi", "φ"}, {"varphi", "φ"},
         {"chi", "χ"}, {"psi", "ψ"}, {"omega", "ω"}, {"Gamma", "Γ"}, {"Delta", "Δ"}, {"Theta", "Θ"}, {"Lambda", "Λ"}, {"Xi", "Ξ"},
         {"Pi", "Π"}, {"Sigma", "Σ"}, {"Phi", "Φ"}, {"Psi", "Ψ"}, {"Omega", "Ω"},
         {"cdot", "·"}, {"times", "×"}, {"div", "÷"}, {"pm", "±"}, {"mp", "∓"}, {"leq", "≤"}, {"le", "≤"}, {"geq", "≥"}, {"ge", "≥"},
         {"neq", "≠"}, {"ne", "≠"}, {"approx", "≈"}, {"equiv", "≡"}, {"sim", "∼"}, {"cong", "≅"}, {"propto", "∝"},
         {"infty", "∞"}, {"partial", "∂"}, {"nabla", "∇"}, {"int", "∫"}, {"iint", "∬"}, {"oint", "∮"}, {"sum", "∑"}, {"prod", "∏"},
         {"in", "∈"}, {"notin", "∉"}, {"ni", "∋"}, {"subset", "⊂"}, {"subseteq", "⊆"}, {"supset", "⊃"}, {"supseteq", "⊇"},
         {"cup", "∪"}, {"cap", "∩"}, {"setminus", "∖"}, {"emptyset", "∅"}, {"varnothing", "∅"}, {"forall", "∀"}, {"exists", "∃"},
         {"neg", "¬"}, {"lnot", "¬"}, {"land", "∧"}, {"wedge", "∧"}, {"lor", "∨"}, {"vee", "∨"}, {"oplus", "⊕"}, {"otimes", "⊗"},
         {"to", "→"}, {"rightarrow", "→"}, {"leftarrow", "←"}, {"Rightarrow", "⇒"}, {"implies", "⇒"}, {"Leftarrow", "⇐"},
         {"Leftrightarrow", "⇔"}, {"iff", "⇔"}, {"leftrightarrow", "↔"}, {"mapsto", "↦"}, {"uparrow", "↑"}, {"downarrow", "↓"},
         {"langle", "⟨"}, {"rangle", "⟩"}, {"lfloor", "⌊"}, {"rfloor", "⌋"}, {"lceil", "⌈"}, {"rceil", "⌉"}, {"mid", "∣"},
         {"parallel", "∥"}, {"perp", "⊥"}, {"angle", "∠"}, {"circ", "∘"}, {"ldots", "…"}, {"cdots", "⋯"}, {"dots", "…"},
         {"vdots", "⋮"}, {"ddots", "⋱"}, {"degree", "°"}, {"hbar", "ℏ"}, {"ell", "ℓ"}, {"Re", "ℜ"}, {"Im", "ℑ"}, {"aleph", "ℵ"},
         {"quad", "  "}, {"qquad", "    "}, {"left", ""}, {"right", ""}, {"big", ""}, {"Big", ""}, {"bigl", ""}, {"bigr", ""},
         {"displaystyle", ""}, {"limits", ""}, {"nolimits", ""}, {"lim", "lim"}, {"sin", "sin"}, {"cos", "cos"}, {"tan", "tan"},
         {"ln", "ln"}, {"log", "log"}, {"exp", "exp"}, {"det", "det"}, {"max", "max"}, {"min", "min"}, {"sup", "sup"}, {"inf", "inf"},
         {"dim", "dim"}, {"ker", "ker"}, {"deg", "deg"}, {"gcd", "ggT"}, {"arcsin", "arcsin"}, {"arccos", "arccos"}, {"arctan", "arctan"},
         {"sinh", "sinh"}, {"cosh", "cosh"}, {"tanh", "tanh"}, {"mod", "mod"}, {"bmod", "mod"}, {"pmod", "mod"}
      };
      for (String[] p : pairs) {
         SYMBOLS.put(p[0], p[1]);
      }
   }

   private MathText() {
   }

   public static String toUnicode(String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      String t = s.replace("\r\n", "\n");
      t = DOLLARS.matcher(t).replaceAll("");
      t = t.replace("\\{", "{").replace("\\}", "}").replace("\\,", " ").replace("\\;", " ").replace("\\!", "").replace("\\ ", " ")
         .replace("\\\\", "\n").replace("\\%", "%").replace("\\&", "&").replace("\\#", "#");
      t = ENVIRONMENT.matcher(t).replaceAll("");
      t = BLACKBOARD.matcher(t).replaceAll(m -> Matcher.quoteReplacement(blackboard(m.group(1))));
      t = WRAPPER.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
      t = VEC.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + "⃗"));
      t = HAT.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + "̂"));
      t = BAR.matcher(t).replaceAll(m -> Matcher.quoteReplacement(overline(m.group(1))));
      // Brüche und Wurzeln von innen nach außen auflösen.
      for (int i = 0; i < 6; i++) {
         String before = t;
         t = FRAC.matcher(t).replaceAll(m -> Matcher.quoteReplacement(fraction(m.group(1), m.group(2))));
         t = SQRT_N.matcher(t).replaceAll(m -> Matcher.quoteReplacement(superscript(m.group(1)) + "√(" + m.group(2).trim() + ")"));
         t = SQRT.matcher(t).replaceAll(m -> Matcher.quoteReplacement(root(m.group(1))));
         t = WRAPPER.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
         if (t.equals(before)) {
            break;
         }
      }
      t = SUP_GROUP.matcher(t).replaceAll(m -> Matcher.quoteReplacement(superscriptOrCaret(m.group(1))));
      t = SUP_ONE.matcher(t).replaceAll(m -> Matcher.quoteReplacement(superscriptOrCaret(m.group(1))));
      t = SUB_GROUP.matcher(t).replaceAll(m -> Matcher.quoteReplacement(subscriptOrUnderscore(m.group(1))));
      t = SUB_ONE.matcher(t).replaceAll(m -> Matcher.quoteReplacement(subscriptOrUnderscore(m.group(1))));
      t = COMMAND.matcher(t).replaceAll(m -> {
         String rep = SYMBOLS.get(m.group(1));
         return Matcher.quoteReplacement(rep != null ? rep : m.group(1));
      });
      // Übrig gebliebene Gruppierungsklammern von LaTeX weglassen, echte Mengenklammern bleiben (mit Inhalt und Komma).
      t = t.replaceAll("\\{([^{},]{1,3})\\}", "$1");
      t = t.replaceAll("[ \\t]{3,}", "  ");
      return t.strip();
   }

   private static String fraction(String a, String b) {
      a = a.trim();
      b = b.trim();
      return wrap(a) + "/" + wrap(b);
   }

   private static String wrap(String x) {
      return x.matches("[A-Za-z0-9.αβγδεπ²³]+") || x.matches("[A-Za-z0-9.]*\\([^()]*\\)") ? x : "(" + x + ")";
   }

   private static String root(String x) {
      x = x.trim();
      return x.matches("[A-Za-z0-9.]+") ? "√" + x : "√(" + x + ")";
   }

   private static String overline(String x) {
      StringBuilder b = new StringBuilder();
      for (char c : x.toCharArray()) {
         b.append(c).append('̅');
      }
      return b.toString();
   }

   private static String blackboard(String letter) {
      return switch (letter) {
         case "R" -> "ℝ";
         case "N" -> "ℕ";
         case "Z" -> "ℤ";
         case "Q" -> "ℚ";
         case "C" -> "ℂ";
         case "P" -> "ℙ";
         case "K" -> "𝕂";
         case "F" -> "𝔽";
         case "E" -> "𝔼";
         default -> letter;
      };
   }

   /** Hochgestellt, wenn alle Zeichen das können, sonst ^(…). */
   private static String superscriptOrCaret(String x) {
      String s = superscript(x);
      return s != null ? s : "^(" + x.trim() + ")";
   }

   private static String superscript(String x) {
      return map(x.trim(), SUP_FROM, SUP_TO);
   }

   private static String subscriptOrUnderscore(String x) {
      String s = map(x.trim(), SUB_FROM, SUB_TO);
      return s != null ? s : "_(" + x.trim() + ")";
   }

   private static String map(String x, String from, String to) {
      if (x.isEmpty()) {
         return null;
      }
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < x.length(); i++) {
         int k = from.indexOf(x.charAt(i));
         if (k < 0) {
            return null;
         }
         b.append(to.charAt(k));
      }
      return b.toString();
   }
}
