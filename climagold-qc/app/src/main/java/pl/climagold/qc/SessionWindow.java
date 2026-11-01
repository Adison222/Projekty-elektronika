package pl.climagold.qc;

/** Pure rules shared by the lifecycle and MediaStore scan. Times are epoch milliseconds. */
public final class SessionWindow {
  public static long close(long previousEnd, long now) {
    return previousEnd > 0 ? previousEnd : now;
  }

  public static boolean includes(
      long baselineId,
      long id,
      long startedSeconds,
      long endedMillis,
      long takenMillis,
      long addedSeconds) {
    if (id <= baselineId || endedMillis <= 0) return false;
    long captured = takenMillis > 0 ? takenMillis : addedSeconds * 1000;
    return captured >= startedSeconds * 1000 && captured <= endedMillis;
  }

  public static boolean shouldFinish(
      boolean active,
      boolean cameraAway,
      long endedMillis,
      boolean attempted,
      boolean working,
      boolean visible,
      boolean locked) {
    return active
        && !working
        && visible
        && !locked
        && (cameraAway || (endedMillis > 0 && !attempted));
  }
}
