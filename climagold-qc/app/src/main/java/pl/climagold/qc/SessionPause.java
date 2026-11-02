package pl.climagold.qc;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

/** Durable ten-minute lock pause. Elapsed time is authoritative within the same boot. */
final class SessionPause {
  static final long LIMIT_MS = 10 * 60 * 1000L;

  static long elapsed(long wall, long monotonic, int boot, long now, long uptime, int currentBoot) {
    return Math.max(
        0, boot == currentBoot && uptime >= monotonic ? uptime - monotonic : now - wall);
  }

  static long refresh(Context context) {
    var p = context.getSharedPreferences(SessionMonitorService.PREFS, 0);
    if (!p.contains("serial") || p.getLong("ended_ms", 0) > 0) return 0;
    long now = System.currentTimeMillis(), uptime = SystemClock.elapsedRealtime();
    int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    long since = p.getLong("locked_at", 0);
    boolean locked = SessionMonitorService.locked(context);
    if (since == 0) {
      if (!locked) return 0;
      p.edit()
          .putLong("locked_at", now)
          .putLong("locked_uptime", uptime)
          .putInt("locked_boot", boot)
          .remove("unlock_grace_until")
          .commit();
      return LIMIT_MS;
    }
    long elapsed =
        elapsed(
            since, p.getLong("locked_uptime", 0), p.getInt("locked_boot", -2), now, uptime, boot);
    if (elapsed >= LIMIT_MS) {
      SessionMonitorService.closeAt(context, "Minęło 10 minut blokady ekranu", since + LIMIT_MS);
      return 0;
    }
    if (!locked) {
      p.edit()
          .remove("locked_at")
          .remove("locked_uptime")
          .remove("locked_boot")
          .putLong("unlock_grace_until", uptime + 2500)
          .commit();
      return 0;
    }
    return LIMIT_MS - elapsed;
  }
}
