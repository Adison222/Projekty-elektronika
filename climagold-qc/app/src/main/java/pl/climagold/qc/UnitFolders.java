package pl.climagold.qc;

import java.util.*;

/** Folder names are displayed literally: numeric folders cannot distinguish C from K. */
public final class UnitFolders {
  public static final class Entry {
    public final String path, name;
    public int count;
    public long newest;

    Entry(String path) {
      this.path = path;
      this.name = path.substring(5, path.length() - 1);
    }
  }

  public static boolean isUnitPath(String path) {
    if (path == null || !path.matches("DCIM/[^/]+/")) return false;
    String name = path.substring(5, path.length() - 1);
    return !name.startsWith(".")
        && name.matches(".*[0-9].*")
        && !name.toLowerCase(Locale.ROOT).startsWith("camera");
  }

  public static List<Entry> group(List<String> paths, List<Long> dates) {
    LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      if (!isUnitPath(path)) continue;
      Entry e = entries.computeIfAbsent(path, Entry::new);
      e.count++;
      e.newest = Math.max(e.newest, dates.get(i));
    }
    ArrayList<Entry> out = new ArrayList<>(entries.values());
    out.sort(Comparator.comparingLong((Entry e) -> e.newest).reversed().thenComparing(e -> e.name));
    return out;
  }
}
