package pl.climagold.qc;

import static org.junit.Assert.*;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SessionMonitorServiceTest {
  private Context context;
  private SharedPreferences prefs;

  @Before
  public void prepare() {
    context = RuntimeEnvironment.getApplication();
    prefs = context.getSharedPreferences(SessionMonitorService.PREFS, 0);
    prefs.edit().clear().commit();
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(true);
    Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
  }

  @Test
  public void screenOffStartsDurablePauseWithoutClosingSeries() {
    prefs.edit().putString("serial", "C12345").putBoolean("camera_away", true).commit();
    var controller =
        Robolectric.buildService(SessionMonitorService.class).create().startCommand(0, 1);
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
    context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertTrue(prefs.getLong("locked_at", 0) > 0);
    assertEquals(0, prefs.getLong("ended_ms", 0));
    assertTrue(prefs.getBoolean("camera_away", false));
    long locked = prefs.getLong("locked_at", 0);
    controller.destroy();
    var restarted =
        Robolectric.buildService(SessionMonitorService.class).create().startCommand(0, 2);
    assertEquals(locked, prefs.getLong("locked_at", 0));
    assertEquals(0, prefs.getLong("ended_ms", 0));
    restarted.destroy();
  }

  @Test
  public void shortUnlockInsideAppContinuesInsteadOfTreatingItAsCameraReturn() {
    prefs.edit().putString("serial", "C12345").putBoolean("camera_away", true).commit();
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
    SessionPause.refresh(context);
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(9));
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(true);
    SessionPause.refresh(context); // Service may receive USER_PRESENT before the Activity.
    var activity = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    assertEquals(0, prefs.getLong("ended_ms", 0));
    assertFalse(prefs.contains("locked_at"));
    assertFalse(prefs.getBoolean("camera_away", true));
    assertTrue(activity.get().nativeSession.active());
    activity.pause().stop().destroy();
  }

  @Test
  public void tenMinutesClosesAtDeadlineEvenWhenTimerRunsLate() {
    prefs.edit().putString("serial", "C12345").commit();
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
    SessionPause.refresh(context);
    long start = prefs.getLong("locked_at", 0);
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(12));
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(true);
    SessionPause.refresh(context);
    assertEquals(start + SessionPause.LIMIT_MS, prefs.getLong("ended_ms", 0));
    assertFalse(prefs.getBoolean("finish_attempted", true));
    SessionPause.refresh(context);
    assertEquals(start + SessionPause.LIMIT_MS, prefs.getLong("ended_ms", 0));
  }

  @Test
  public void serviceTimerEndsSeriesAtTenMinutesWhileLocked() {
    prefs.edit().putString("serial", "K42").commit();
    Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
    var service = Robolectric.buildService(SessionMonitorService.class).create().startCommand(0, 1);
    long start = prefs.getLong("locked_at", 0);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(10));
    assertEquals(start + SessionPause.LIMIT_MS, prefs.getLong("ended_ms", 0));
    service.destroy();
  }

  @Test
  public void monotonicPauseIgnoresWallClockChangesAndFallsBackAfterReboot() {
    assertEquals(599999, SessionPause.elapsed(1000, 2000, 1, 99, 601999, 1));
    assertEquals(600000, SessionPause.elapsed(1000, 2000, 1, 99, 602000, 1));
    assertEquals(600000, SessionPause.elapsed(1000, 2000, 1, 601000, 1, 2));
  }

  @Test
  public void noSessionMeansNoPhantomClose() {
    assertFalse(SessionMonitorService.close(context, "Screen off"));
    assertFalse(prefs.contains("ended_ms"));
  }

  @Test
  public void repeatCloseDoesNotResetDeniedConsentOrPendingPhotos() {
    prefs
        .edit()
        .putString("serial", "IEW/123")
        .putLong("ended_ms", 1234)
        .putBoolean("finish_attempted", true)
        .putStringSet("pending", java.util.Set.of("content://media/external/images/media/5"))
        .commit();
    SessionMonitorService.close(context, "Unlock");
    assertEquals(1234, prefs.getLong("ended_ms", 0));
    assertTrue(prefs.getBoolean("finish_attempted", false));
    assertEquals(1, prefs.getStringSet("pending", java.util.Set.of()).size());
  }

  @Test
  public void activityReturnFromCameraClosesWindowAndRequestsMissingReadPermission() {
    prefs.edit().putString("serial", "C12345").putBoolean("camera_away", true).commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(android.Manifest.permission.READ_MEDIA_IMAGES);
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    assertTrue(prefs.getLong("ended_ms", 0) > 0);
    assertFalse(prefs.getBoolean("camera_away", true));
    assertTrue(prefs.getBoolean("finish_attempted", false));
    assertEquals("Powrót z aparatu do aplikacji", prefs.getString("end_reason", ""));
    controller.pause().stop().destroy();
  }

  @Test
  public void returningToInAppSessionPanelDoesNotCloseCaptureWindow() {
    prefs.edit().putString("serial", "C12345").putBoolean("camera_away", false).commit();
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    assertEquals(0, prefs.getLong("ended_ms", 0));
    controller.pause().stop().destroy();
  }

  @Test
  public void rejoiningLastSessionUsesConfirmationAndRestoresItsNumber() {
    context
        .getSharedPreferences("completed_photo_session", 0)
        .edit()
        .putString("serial", "RIEW/123")
        .putLong("finished", 123456789)
        .commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(android.Manifest.permission.READ_MEDIA_IMAGES);
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    MainActivity activity = controller.get();
    android.view.View join = findText(activity.root, "Dołącz do ostatniej sesji");
    assertNotNull(join);
    assertEquals("", activity.serial);
    join.performClick();
    assertEquals("", activity.serial);
    android.app.AlertDialog confirmation =
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    confirmation.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals("RIEW123", activity.serial);
    assertEquals("RIEW123", activity.draft);
    // No new capture window exists until read access and the baseline snapshot are available.
    assertFalse(prefs.contains("serial"));
    controller.pause().stop().destroy();
  }

  @Test
  public void grantedBatchMovesPhotosAndRecordsLastCompletedSession() throws Exception {
    var provider = new RecordingMediaProvider(false);
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider);
    seedPendingBatch();
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    controller.get().nativeSession.result(41, android.app.Activity.RESULT_OK);
    awaitTransfer(controller.get().nativeSession);
    assertEquals(2, provider.moves);
    assertEquals("DCIM/12345/", provider.target);
    assertFalse(prefs.contains("serial"));
    assertEquals(
        "C12345",
        context.getSharedPreferences("completed_photo_session", 0).getString("serial", ""));
    assertNotNull(findText(controller.get().root, "Dołącz do ostatniej sesji"));
    controller.pause().stop().destroy();
  }

  @Test
  public void partialTransferKeepsFailedPhotoForRetry() throws Exception {
    var provider = new RecordingMediaProvider(true);
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider);
    seedPendingBatch();
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    controller.get().nativeSession.result(41, android.app.Activity.RESULT_OK);
    awaitTransfer(controller.get().nativeSession);
    assertTrue(prefs.contains("serial"));
    assertEquals(
        java.util.Set.of("content://media/external/images/media/12"),
        prefs.getStringSet("pending", java.util.Set.of()));
    assertEquals(1, prefs.getInt("saved_count", 0));
    assertFalse(context.getSharedPreferences("completed_photo_session", 0).contains("serial"));
    controller.pause().stop().destroy();
  }

  @Test
  public void deniedBatchStaysClosedAndPendingWithoutMovingPhotos() {
    var provider = new RecordingMediaProvider(false);
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider);
    seedPendingBatch();
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    controller.get().nativeSession.result(41, android.app.Activity.RESULT_CANCELED);
    controller.get().nativeSession.resumed();
    assertEquals(0, provider.moves);
    assertEquals(2, prefs.getStringSet("pending", java.util.Set.of()).size());
    assertTrue(prefs.getBoolean("finish_attempted", false));
    assertEquals(5000, prefs.getLong("ended_ms", 0));
    controller.pause().stop().destroy();
  }

  @Test
  public void scanUsesRealMediaStoreDateColumn() throws Exception {
    final java.util.List<String> columns = new java.util.ArrayList<>();
    android.content.ContentProvider provider =
        new android.content.ContentProvider() {
          public boolean onCreate() {
            return true;
          }

          public String getType(android.net.Uri u) {
            return "image/jpeg";
          }

          public android.database.Cursor query(
              android.net.Uri u,
              String[] projection,
              String selection,
              String[] args,
              String sort) {
            columns.addAll(java.util.Arrays.asList(projection));
            for (String column : projection)
              if (column.equals("date_taken"))
                throw new IllegalArgumentException("Invalid column date_taken");
            android.database.MatrixCursor cursor = new android.database.MatrixCursor(projection);
            cursor.addRow(new Object[] {11L, "DCIM/Camera/", 4000L, 4L, 0});
            return cursor;
          }

          public android.net.Uri insert(android.net.Uri u, android.content.ContentValues v) {
            return null;
          }

          public int delete(android.net.Uri u, String s, String[] a) {
            return 0;
          }

          public int update(
              android.net.Uri u, android.content.ContentValues v, String s, String[] a) {
            return 0;
          }
        };
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider);
    seedPendingBatch();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES);
    var controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
    controller.get().nativeSession.finish("Retry after upgrade");
    awaitTransfer(controller.get().nativeSession);
    assertTrue(columns.contains(android.provider.MediaStore.Images.Media.DATE_TAKEN));
    assertFalse(columns.contains("date_taken"));
    assertTrue(
        prefs
            .getStringSet("pending", java.util.Set.of())
            .contains("content://media/external/images/media/11"));
    controller.pause().stop().destroy();
  }

  private void seedPendingBatch() {
    java.util.Set<String> uris =
        java.util.Set.of(
            "content://media/external/images/media/11", "content://media/external/images/media/12");
    prefs
        .edit()
        .putString("serial", "C12345")
        .putLong("ended_ms", 5000)
        .putBoolean("finish_attempted", true)
        .putStringSet("pending", uris)
        .putStringSet("write_batch", uris)
        .commit();
  }

  private static void awaitTransfer(NativeSession session) throws Exception {
    long until = System.nanoTime() + 5_000_000_000L;
    do {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      if (!session.busy()) return;
      Thread.sleep(10);
    } while (System.nanoTime() < until);
    fail("Transfer did not finish");
  }

  private static final class RecordingMediaProvider extends android.content.ContentProvider {
    final boolean failSecond;
    int moves;
    String target;

    RecordingMediaProvider(boolean failSecond) {
      this.failSecond = failSecond;
    }

    public boolean onCreate() {
      return true;
    }

    public String getType(android.net.Uri uri) {
      return "image/jpeg";
    }

    public android.database.Cursor query(
        android.net.Uri uri, String[] columns, String selection, String[] args, String order) {
      return new android.database.MatrixCursor(columns);
    }

    public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) {
      throw new UnsupportedOperationException();
    }

    public int delete(android.net.Uri uri, String selection, String[] args) {
      throw new AssertionError("Must never delete source photos");
    }

    public int update(
        android.net.Uri uri,
        android.content.ContentValues values,
        String selection,
        String[] args) {
      if (failSecond && "12".equals(uri.getLastPathSegment())) return 0;
      moves++;
      target = values.getAsString(android.provider.MediaStore.Images.Media.RELATIVE_PATH);
      return 1;
    }
  }

  private static android.view.View findText(android.view.View view, String text) {
    if (view instanceof android.widget.TextView
        && text.contentEquals(((android.widget.TextView) view).getText())) return view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View match = findText(group.getChildAt(i), text);
        if (match != null) return match;
      }
    }
    return null;
  }
}
