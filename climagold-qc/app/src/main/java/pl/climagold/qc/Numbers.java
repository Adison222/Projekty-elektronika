package pl.climagold.qc;

import java.util.*;
import java.util.regex.*;

public final class Numbers {
  public static List<String> parse(String text) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    Matcher m =
        Pattern.compile(
                "(?<![A-Z0-9])((?:RIEW|IEW)[/:][A-Z0-9/.]+|Z[0-9]+|[CK]\\s*[0-9]{4,})(?:\\s*[-–]\\s*([0-9]+))?",
                Pattern.CASE_INSENSITIVE)
            .matcher(text.toUpperCase(Locale.ROOT).replace((char) 92, (char) 47));
    while (m.find()) {
      String first = m.group(1).replaceAll("\\s", "");
      String end = m.group(2);
      if (end == null) {
        out.add(first);
        continue;
      }
      Matcher n = Pattern.compile("^(.*?)([0-9]+)$").matcher(first);
      if (!n.matches()) continue;
      String digits = n.group(2);
      String last =
          end.length() < digits.length()
              ? digits.substring(0, digits.length() - end.length()) + end
              : end;
      try {
        long a = Long.parseLong(digits), b = Long.parseLong(last);
        if (b < a || b - a > 500) continue;
        for (long i = a; i <= b; i++)
          out.add(n.group(1) + String.format(Locale.ROOT, "%0" + digits.length() + "d", i));
      } catch (NumberFormatException ignored) {
      }
    }
    return new ArrayList<>(out);
  }

  /** General identifiers are literal: no range expansion or guessed OCR substitutions. */
  public static List<String> parseGeneral(String text) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (text == null) return new ArrayList<>(out);
    Matcher tokens =
        Pattern.compile("[A-Z0-9]+(?:[-/._][A-Z0-9]+)*").matcher(text.toUpperCase(Locale.ROOT));
    while (tokens.find()) {
      String token = tokens.group();
      // Limit path component size and reject fragments of oversized identifiers.
      if (token.length() <= 80 && token.matches(".*[0-9].*")) out.add(token);
    }
    return new ArrayList<>(out);
  }

  public static String folder(String serial) {
    return serial.matches("[CK][0-9]+")
        ? serial.substring(1)
        : serial.replace("/", "∕").replace("\\", "∖");
  }
}
