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
  public void screenOffClosesExternalCameraWindowDurably() {
    prefs.edit().putString("serial", "C12345").putBoolean("camera_away", true).commit();
    var controller =
        Robolectric.buildService(SessionMonitorService.class).create().startCommand(0, 1);
    context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertTrue(prefs.getLong("ended_ms", 0) > 0);
    assertFalse(prefs.getBoolean("camera_away", true));
    assertFalse(prefs.getBoolean("finish_attempted", true));
    assertEquals("C12345", prefs.getString("serial", ""));
    long end = prefs.getLong("ended_ms", 0);
    controller.destroy();
    assertTrue(SessionMonitorService.close(context, "Return after unlock"));
    assertEquals(end, prefs.getLong("ended_ms", 0));
    assertEquals("Zablokowano lub wygaszono ekran", prefs.getString("end_reason", ""));
  }

  @Test
  public void restartingMonitorOnLockedDeviceClosesPendingWindow() {
    prefs.edit().putString("serial", "K42").putBoolean("camera_away", true).commit();
    Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setKeyguardLocked(true);
    var controller =
        Robolectric.buildService(SessionMonitorService.class).create().startCommand(0, 1);
    assertTrue(prefs.getLong("ended_ms", 0) > 0);
    controller.destroy();
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
    assertEquals("RIEW/123", activity.serial);
    assertEquals("RIEW/123", activity.draft);
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
