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

  public static String folder(String serial) {
    return serial.matches("[CK][0-9]+")
        ? serial.substring(1)
        : serial.replace("/", "∕").replace("\\", "∖");
  }
}
