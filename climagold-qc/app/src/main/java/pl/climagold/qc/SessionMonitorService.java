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

  static boolean close(Context context, String reason) {
    var prefs = context.getSharedPreferences(PREFS, 0);
    if (!prefs.contains("serial")) return false;
    if (prefs.getLong("ended_ms", 0) > 0) return true;
    return prefs
        .edit()
        .putLong("ended_ms", SessionWindow.close(0, System.currentTimeMillis()))
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
            .setContentText("Powrót do aplikacji lub blokada ekranu kończy serię.")
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
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
              close(context, "Zablokowano lub wygaszono ekran");
              stopSelf();
            }
          }
        };
    ContextCompat.registerReceiver(
        this,
        receiver,
        new IntentFilter(Intent.ACTION_SCREEN_OFF),
        ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    var prefs = getSharedPreferences(PREFS, 0);
    if (locked(this)) close(this, "Zablokowano lub wygaszono ekran");
    if (!prefs.contains("serial") || prefs.getLong("ended_ms", 0) > 0) {
      stopSelf();
      return START_NOT_STICKY;
    }
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    if (receiver != null) unregisterReceiver(receiver);
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
