package pl.climagold.qc;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import androidx.core.content.ContextCompat;

/** Keeps the screen-off receiver alive while the external camera is in front. */
public final class SessionMonitorService extends Service {
  static final String PREFS = "native_camera_session";
  private BroadcastReceiver receiver;
  private final Handler timer = new Handler(Looper.getMainLooper());
  private final Runnable checkPause = () -> updatePause();

  private void updatePause() {
    timer.removeCallbacks(checkPause);
    long remaining = SessionPause.refresh(this);
    var p = getSharedPreferences(PREFS, 0);
    if (!p.contains("serial") || p.getLong("ended_ms", 0) > 0) stopSelf();
    else if (remaining > 0) timer.postDelayed(checkPause, remaining);
  }

  static boolean close(Context context, String reason) {
    return closeAt(context, reason, System.currentTimeMillis());
  }

  static boolean closeAt(Context context, String reason, long end) {
    var prefs = context.getSharedPreferences(PREFS, 0);
    if (!prefs.contains("serial")) return false;
    if (prefs.getLong("ended_ms", 0) > 0) return true;
    return prefs
        .edit()
        .putLong("ended_ms", SessionWindow.close(0, end))
        .putString("end_reason", reason)
        .putBoolean("camera_away", false)
        .putBoolean("finish_attempted", false)
        .commit();
  }

  static boolean locked(Context context) {
    PowerManager power = context.getSystemService(PowerManager.class);
    KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
    return !power.isInteractive() || keyguard.isKeyguardLocked();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "photo_session", "Seria zdjęć", NotificationManager.IMPORTANCE_LOW));
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification notification =
        new Notification.Builder(this, "photo_session")
            .setSmallIcon(R.drawable.ic_qc)
            .setContentTitle("Trwa seria zdjęć")
            .setContentText("Blokada do 10 minut to przerwa. Wróć do aplikacji, aby zapisać serię.")
            .setContentIntent(open)
            .setOngoing(true)
            .build();
    if (Build.VERSION.SDK_INT >= 34)
      startForeground(73, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    else startForeground(73, notification);
    receiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            updatePause();
          }
        };
    IntentFilter events = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    events.addAction(Intent.ACTION_USER_PRESENT);
    events.addAction(Intent.ACTION_SCREEN_ON);
    ContextCompat.registerReceiver(this, receiver, events, ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    var prefs = getSharedPreferences(PREFS, 0);
    updatePause();
    if (!prefs.contains("serial") || prefs.getLong("ended_ms", 0) > 0) {
      stopSelf();
      return START_NOT_STICKY;
    }
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    timer.removeCallbacks(checkPause);
    if (receiver != null) unregisterReceiver(receiver);
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
