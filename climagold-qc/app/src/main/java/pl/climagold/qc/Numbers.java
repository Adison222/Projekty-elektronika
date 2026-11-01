package pl.climagold.qc;

import java.util.*;
import java.util.regex.*;

public final class Numbers {
  public static List<String> parse(String text) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (text == null) return new ArrayList<>(out);
    Matcher m =
        Pattern.compile(
                "(?<![A-Z0-9])((?:RIEW|IEW)[ \t]*[/:]?[ \t]*[0-9]+(?:[/.][A-Z0-9]+)*|[ZR][ "
                    + "\t]*[/:]?[ \t]*[0-9]+|[CK][ \t]*[0-9]+)(?![A-Z0-9])(?:[ \t]*[-–][ "
                    + "\t]*([0-9]+)(?![A-Z0-9]))?",
                Pattern.CASE_INSENSITIVE)
            .matcher(text.toUpperCase(Locale.ROOT).replace((char) 92, (char) 47));
    while (m.find()) {
      String first = m.group(1).replaceAll("\\s", "");
      if (first.length() > 80) continue;
      first = normalizeSerial(first);
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
        for (long i = a; ; i++) {
          out.add(n.group(1) + String.format(Locale.ROOT, "%0" + digits.length() + "d", i));
          if (i == b) break;
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return new ArrayList<>(out);
  }

  /** Suggestions only: never apply guessed substitutions to manually entered identifiers. */
  public static List<String> ocrSuggestions(String text) {
    LinkedHashSet<String> out = new LinkedHashSet<>(parse(text));
    if (text == null) return new ArrayList<>(out);
    Matcher m =
        Pattern.compile(
                "(?<![A-Z0-9])((?:R[ \t]*)?[I1L|][ \t]*E[ \t]*W|[CKZR])[ \t]*[/:]?[ "
                    + "\t]*([0-9OIL|SBZ]+(?:[/.][0-9OIL|SBZ]+)*(?:[ \t]*[-–][ "
                    + "\t]*[0-9OIL|SBZ]+)?)(?![A-Z0-9])")
            .matcher(text.toUpperCase(Locale.ROOT).replace('\\', '/'));
    while (m.find()) {
      String digits = m.group(2);
      if (!digits.matches(".*[0-9].*") || digits.length() > 70) continue;
      String prefix = m.group(1).replaceAll("[ \t]", "");
      if (prefix.endsWith("EW")) prefix = prefix.startsWith("R") ? "RIEW" : "IEW";
      digits =
          digits
              .replace('O', '0')
              .replace('I', '1')
              .replace('L', '1')
              .replace('|', '1')
              .replace('S', '5')
              .replace('B', '8')
              .replace('Z', '2');
      out.addAll(parse(prefix + digits));
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
      if (token.length() <= 80 && token.matches(".*[0-9].*")) out.add(normalizeSerial(token));
    }
    return new ArrayList<>(out);
  }

  /** Remove only the separator between a known prefix and its numeric identifier. */
  public static String normalizeSerial(String serial) {
    if (serial == null) return "";
    return serial
        .trim()
        .toUpperCase(Locale.ROOT)
        .replaceFirst("^(RIEW|IEW|[CKZR])[ \t]*[/\\\\∕∖:]?[ \t]*(?=[0-9])", "$1");
  }

  public static String folder(String serial) {
    return legacyFolder(normalizeSerial(serial));
  }

  private static String legacyFolder(String serial) {
    return serial.matches("[CK][0-9]+")
        ? serial.substring(1)
        : serial.replace("/", "∕").replace("\\", "∖");
  }

  /** Read old folders too; all new photos use the first, canonical path. No file migration. */
  public static List<String> folderPaths(String serial) {
    String normalized = normalizeSerial(serial);
    LinkedHashSet<String> paths = new LinkedHashSet<>();
    paths.add("DCIM/" + folder(normalized) + "/");
    paths.add("DCIM/" + legacyFolder(serial) + "/");
    if (normalized.matches("(?:RIEW|IEW|Z|R)[0-9].*")) {
      paths.add("DCIM/" + legacyFolder(normalized.replaceFirst("^(RIEW|IEW|Z|R)", "$1/")) + "/");
      paths.add("DCIM/" + normalized.replaceFirst("^(RIEW|IEW|Z|R)", "$1:") + "/");
    }
    return new ArrayList<>(paths);
  }
}
