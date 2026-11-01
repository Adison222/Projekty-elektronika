package pl.climagold.qc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Durable capture window, followed by automatic assignment with Android's required consent. */
final class NativeSession {
  private static final int READ_REQUEST = 40, WRITE_REQUEST = 41, NOTIFY_REQUEST = 42;
  private final MainActivity activity;
  private final SharedPreferences prefs, history;
  private boolean working, visible, permissionVisible;

  NativeSession(MainActivity activity) {
    this.activity = activity;
    prefs = activity.getSharedPreferences(SessionMonitorService.PREFS, 0);
    history = activity.getSharedPreferences("completed_photo_session", 0);
    for (SharedPreferences saved : new SharedPreferences[] {prefs, history}) {
      if (saved.contains("serial"))
        saved
            .edit()
            .putString("serial", Numbers.normalizeSerial(saved.getString("serial", "")))
            .apply();
    }
  }

  boolean active() {
    return prefs.contains("serial");
  }

  boolean busy() {
    return working;
  }

  private boolean ended() {
    return prefs.getLong("ended_ms", 0) > 0;
  }

  private String permission() {
    return Build.VERSION.SDK_INT >= 33
        ? Manifest.permission.READ_MEDIA_IMAGES
        : Manifest.permission.READ_EXTERNAL_STORAGE;
  }

  private boolean canRead() {
    return activity.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
  }

  void resumed() {
    visible = true;
    if (!active() || working || permissionVisible) return;
    boolean locked = SessionMonitorService.locked(activity);
    if (locked) {
      SessionMonitorService.close(activity, "Zablokowano lub wygaszono ekran");
      return;
    }
    if (SessionWindow.shouldFinish(
        active(),
        prefs.getBoolean("camera_away", false),
        prefs.getLong("ended_ms", 0),
        prefs.getBoolean("finish_attempted", false),
        working,
        visible,
        locked)) {
      finish("Powrót z aparatu do aplikacji");
    } else {
      if (!ended()) {
        try {
          androidx.core.content.ContextCompat.startForegroundService(
              activity, new Intent(activity, SessionMonitorService.class));
        } catch (Exception e) {
          activity.message("Nie uruchomiono monitorowania blokady: " + e.getMessage());
        }
      }
      panel();
    }
  }

  void unlocked() {
    if (visible) resumed();
  }

  void paused() {
    visible = false;
    if (active() && SessionMonitorService.locked(activity))
      SessionMonitorService.close(activity, "Zablokowano lub wygaszono ekran");
  }

  boolean readBeforeConfirm(String serial) {
    if (canRead()) return true;
    prefs.edit().putString("confirm_number", serial).apply();
    new AlertDialog.Builder(activity)
        .setTitle("Dostęp do zdjęć")
        .setMessage(
            "Zezwól na wszystkie zdjęcia, aby wykrywać serię z aparatu telefonu. Powrót z aparatu"
                + " lub blokada ekranu zakończy serię; następnie Android może poprosić o zgodę na"
                + " przeniesienie.")
        .setPositiveButton(
            "Kontynuuj",
            (d, w) -> activity.requestPermissions(new String[] {permission()}, READ_REQUEST))
        .setNegativeButton("Anuluj", (d, w) -> prefs.edit().remove("confirm_number").apply())
        .show();
    return false;
  }

  void start() {
    if (active()) {
      panel();
      return;
    }
    if (!canRead()) {
      readBeforeConfirm(activity.serial);
      return;
    }
    if (working) return;
    if (Build.VERSION.SDK_INT >= 33
        && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        && !activity.getPreferences(0).getBoolean("askedSessionNotifications", false)) {
      activity.getPreferences(0).edit().putBoolean("askedSessionNotifications", true).apply();
      activity.requestPermissions(
          new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFY_REQUEST);
      return;
    }
    working = true;
    new Thread(
            () -> {
              try {
                long high = 0;
                try (Cursor c =
                    activity
                        .getContentResolver()
                        .query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[] {"_id"},
                            null,
                            null,
                            "_id DESC")) {
                  if (c == null) throw new IOException("Brak dostępu do listy zdjęć");
                  if (c.moveToFirst()) high = c.getLong(0);
                }
                if (!prefs
                    .edit()
                    .clear()
                    .putString("serial", activity.serial)
                    .putLong("after_id", high)
                    .putLong("started", System.currentTimeMillis() / 1000)
                    .commit()) throw new IOException("Nie zapisano sesji");
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      if (activity.isDestroyed()
                          || !visible
                          || SessionMonitorService.locked(activity)) {
                        SessionMonitorService.close(
                            activity, "Aplikacja została opuszczona przed otwarciem aparatu");
                        return;
                      }
                      launch();
                    });
              } catch (Exception e) {
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      activity.message("Nie można rozpocząć serii: " + e.getMessage());
                    });
              }
            },
            "session-start")
        .start();
  }

  void permissionResult(int request) {
    if (request == NOTIFY_REQUEST) {
      start();
      return;
    }
    if (request != READ_REQUEST) return;
    if (!canRead()) {
      activity.message(
          "Do automatycznego wykrywania serii potrzebny jest dostęp do wszystkich zdjęć.");
      return;
    }
    String number = prefs.getString("confirm_number", null);
    prefs.edit().remove("confirm_number").apply();
    if (active() && ended()) finish("Zakończenie serii");
    else if (active()) panel();
    else if (number != null) activity.confirm(number);
  }

  void launch() {
    if (!active() || ended() || working) {
      panel();
      return;
    }
    if (activity.provider != null) activity.provider.unbindAll();
    activity.capture = null;
    try {
      // Start while this activity is visible, before handing control to the OEM camera.
      androidx.core.content.ContextCompat.startForegroundService(
          activity, new Intent(activity, SessionMonitorService.class));
      if (!prefs.edit().putBoolean("camera_away", true).commit())
        throw new IOException("Nie zapisano wyjścia do aparatu");
      activity.startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    } catch (Exception e) {
      prefs.edit().putBoolean("camera_away", false).apply();
      activity.stopService(new Intent(activity, SessionMonitorService.class));
      activity.message("Nie otwarto aparatu: " + e.getMessage());
      panel();
    }
  }

  void finish(String reason) {
    if (!active() || working || permissionVisible) return;
    if (!SessionMonitorService.close(activity, reason)) {
      activity.message("Nie zapisano końca serii. Spróbuj ponownie.");
      return;
    }
    activity.stopService(new Intent(activity, SessionMonitorService.class));
    if (!visible || SessionMonitorService.locked(activity)) return;
    if (!canRead()) {
      prefs.edit().putBoolean("finish_attempted", true).apply();
      activity.requestPermissions(new String[] {permission()}, READ_REQUEST);
      return;
    }
    prefs.edit().putBoolean("finish_attempted", true).apply();
    collect();
  }

  void panel() {
    if (activity.isDestroyed() || !active() || working || permissionVisible) return;
    if (activity.provider != null) activity.provider.unbindAll();
    activity.capture = null;
    activity.serial = prefs.getString("serial", "");
    activity.layout();
    activity.label((ended() ? "Dokończ zapis · " : "Seria · ") + activity.serial);
    if (ended()) {
      activity.hint(
          prefs.getString("end_reason", "Zakończenie serii")
              + ". Zdjęcia oczekują na przypisanie do DCIM/"
              + Numbers.folder(activity.serial)
              + "/.");
      activity.action("Dokończ przypisywanie zdjęć", () -> finish("Ponowienie przypisania"), true);
    } else {
      activity.hint(
          "Powrót z aparatu lub blokada ekranu kończy serię. Po odblokowaniu zaakceptuj systemową"
              + " zgodę na przeniesienie, jeśli się pojawi.");
      activity.button("Otwórz aparat", this::launch);
      activity.action("Zakończ i przypisz zdjęcia", () -> finish("Zakończono przyciskiem"), true);
    }
    activity.button("Zdjęcia tej centrali", () -> activity.gallery(0));
    activity.button("Wszystkie centrale", activity::browseUnits);
    activity.button(
        "Zakończ bez przypisywania",
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("Pozostawić zdjęcia w aparacie?")
                .setMessage("Pozostałe zdjęcia nie zostaną przeniesione ani usunięte.")
                .setPositiveButton("Zakończ", (d, w) -> complete())
                .setNegativeButton("Wróć", null)
                .show());
    activity.scrollable();
  }

  void lastSessionCard() {
    String number = history.getString("serial", "");
    if (number.isEmpty()) return;
    activity.label("Ostatnia seria · " + number);
    String when =
        android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", history.getLong("finished", 0))
            .toString();
    activity.hint(
        "Zakończona " + when + ". Możesz dołączyć kolejne zdjęcia do tego samego folderu.");
    activity.action(
        "Dołącz do ostatniej sesji",
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("Wznowić sesję " + number + "?")
                .setMessage(
                    "Nowe zdjęcia trafią do DCIM/"
                        + Numbers.folder(number)
                        + "/. Dotychczasowe pozostaną bez zmian. Zdjęcia zrobione pomiędzy sesjami"
                        + " nie zostaną dołączone.")
                .setPositiveButton(
                    "Dołącz i otwórz aparat",
                    (d, w) -> {
                      activity.serial = number;
                      activity.draft = number;
                      activity.getPreferences(0).edit().putString("lastSerial", number).apply();
                      start();
                    })
                .setNegativeButton("Anuluj", null)
                .show(),
        true);
  }

  private static final class Scan {
    final LinkedHashSet<String> uris = new LinkedHashSet<>();
    boolean pending;
  }

  private Scan scan() throws IOException {
    Scan found = new Scan();
    List<String> targets = Numbers.folderPaths(prefs.getString("serial", ""));
    Set<String> done = prefs.getStringSet("copied", Collections.emptySet());
    try (Cursor c =
        activity
            .getContentResolver()
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                  "_id",
                  "relative_path",
                  MediaStore.Images.Media.DATE_TAKEN,
                  "date_added",
                  "is_pending"
                },
                "_id > ?" + (Build.VERSION.SDK_INT >= 30 ? " AND is_trashed = 0" : ""),
                new String[] {Long.toString(prefs.getLong("after_id", 0))},
                "_id ASC")) {
      if (c == null) throw new IOException("Nie odczytano listy zdjęć");
      while (c.moveToNext()) {
        long id = c.getLong(0);
        String path = c.getString(1);
        if (path == null
            || !path.startsWith("DCIM/")
            || targets.contains(path)
            || done.contains(Long.toString(id))) continue;
        if (!SessionWindow.includes(
            prefs.getLong("after_id", 0),
            id,
            prefs.getLong("started", 0),
            prefs.getLong("ended_ms", 0),
            c.getLong(2),
            c.getLong(3))) continue;
        if (c.getInt(4) != 0) {
          found.pending = true;
          continue;
        }
        found.uris.add(
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                .toString());
      }
    }
    return found;
  }

  private void collect() {
    working = true;
    activity.layout();
    activity.label("Kończenie serii…");
    activity.hint(
        "Czekam na zapis ostatnich zdjęć przez aparat. Następnie przypiszę serię do folderu.");
    new Thread(
            () -> {
              try {
                // Give the camera time to publish its last image, including delayed MediaStore
                // writes.
                long wait =
                    Math.max(0, 5000 - (System.currentTimeMillis() - prefs.getLong("ended_ms", 0)));
                if (wait > 0) Thread.sleep(Math.min(wait, 5000));
                Scan found = scan();
                for (int attempt = 0; found.pending && attempt < 5; attempt++) {
                  Thread.sleep(2000);
                  found = scan();
                }
                if (found.pending)
                  throw new IOException(
                      "Aparat nadal zapisuje zdjęcia. Ponów przypisanie za chwilę.");
                if (!prefs.edit().putStringSet("pending", found.uris).commit())
                  throw new IOException("Nie zapisano listy zdjęć");
                Scan ready = found;
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      if (activity.isDestroyed()
                          || !visible
                          || SessionMonitorService.locked(activity)) {
                        prefs.edit().putBoolean("finish_attempted", false).apply();
                        return;
                      }
                      if (ready.uris.isEmpty()) {
                        // Keep the closed capture window for retry: an OEM may publish photos much
                        // later.
                        panel();
                        new AlertDialog.Builder(activity)
                            .setTitle("Seria zakończona — brak nowych zdjęć")
                            .setMessage(
                                "Jeśli aparat jeszcze zapisuje pliki, ponów wyszukiwanie. Możesz"
                                    + " też zamknąć pustą serię.")
                            .setPositiveButton(
                                "Szukaj ponownie", (d, w) -> finish("Ponowne wyszukiwanie"))
                            .setNeutralButton("Zamknij pustą serię", (d, w) -> complete())
                            .setNegativeButton("Później", null)
                            .show();
                      } else requestWrite();
                    });
              } catch (Exception e) {
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      panel();
                      activity.message("Nie przypisano serii: " + e.getMessage());
                    });
              }
            },
            "session-collect")
        .start();
  }

  private void requestWrite() {
    if (Build.VERSION.SDK_INT < 30) {
      transfer();
      return;
    }
    try {
      ArrayList<Uri> list = new ArrayList<>();
      // Android limits each consent request to 2000 URIs. Continue with the next batch afterwards.
      for (String u : prefs.getStringSet("pending", Collections.emptySet())) {
        if (list.size() == 2000) break;
        list.add(Uri.parse(u));
      }
      if (list.isEmpty()) {
        complete();
        return;
      }
      HashSet<String> batch = new HashSet<>();
      for (Uri uri : list) batch.add(uri.toString());
      if (!prefs.edit().putStringSet("write_batch", batch).commit())
        throw new IOException("Nie zapisano partii zdjęć");
      permissionVisible = true;
      activity.startIntentSenderForResult(
          MediaStore.createWriteRequest(activity.getContentResolver(), list).getIntentSender(),
          WRITE_REQUEST,
          null,
          0,
          0,
          0);
    } catch (Exception e) {
      permissionVisible = false;
      panel();
      activity.message("Nie uzyskano zgody na przeniesienie: " + e.getMessage());
    }
  }

  void result(int request, int result) {
    if (request != WRITE_REQUEST) return;
    permissionVisible = false;
    if (!active()) return;
    if (result == Activity.RESULT_OK) transfer();
    else {
      panel();
      activity.message(
          "Seria jest zakończona. Zdjęcia czekają na zgodę — wybierz Dokończ przypisywanie zdjęć.");
    }
  }

  private void complete() {
    if (!active()) return;
    String number = prefs.getString("serial", "");
    if (!history
        .edit()
        .putString("serial", number)
        .putLong("finished", prefs.getLong("ended_ms", System.currentTimeMillis()))
        .putInt("count", prefs.getInt("saved_count", 0))
        .commit()) {
      activity.message("Nie zapisano historii sesji");
      return;
    }
    if (!prefs.edit().clear().commit()) {
      activity.message("Nie zapisano zakończenia sesji");
      return;
    }
    activity.stopService(new Intent(activity, SessionMonitorService.class));
    if (!activity.isDestroyed()) activity.home();
  }

  private void transfer() {
    if (working) return;
    working = true;
    activity.layout();
    activity.label("Przypisywanie zdjęć…");
    activity.hint("Nie zamykaj aplikacji podczas zapisu.");
    final String target = "DCIM/" + Numbers.folder(prefs.getString("serial", "")) + "/";
    final Set<String> uris =
        new HashSet<>(
            prefs.getStringSet(
                Build.VERSION.SDK_INT >= 30 ? "write_batch" : "pending", Collections.emptySet()));
    new Thread(
            () -> {
              ArrayList<String> errors = new ArrayList<>();
              for (String value : uris) {
                Uri uri = Uri.parse(value);
                try {
                  if (Build.VERSION.SDK_INT >= 30) {
                    ContentValues update = new ContentValues();
                    update.put(MediaStore.Images.Media.RELATIVE_PATH, target);
                    if (activity.getContentResolver().update(uri, update, null, null) != 1)
                      throw new IOException("Nie zmieniono folderu");
                  } else copy(uri, target);
                  HashSet<String> pending =
                      new HashSet<>(prefs.getStringSet("pending", Collections.emptySet()));
                  pending.remove(value);
                  if (!prefs
                      .edit()
                      .putStringSet("pending", pending)
                      .putInt("saved_count", prefs.getInt("saved_count", 0) + 1)
                      .commit()) throw new IOException("Nie zapisano postępu");
                } catch (Exception e) {
                  errors.add(e.getMessage() == null ? "Błąd dostępu" : e.getMessage());
                }
              }
              activity.runOnUiThread(
                  () -> {
                    working = false;
                    if (!errors.isEmpty()) {
                      panel();
                      activity.message(
                          "Nie przypisano części zdjęć. Możesz ponowić: " + errors.get(0));
                    } else if (!prefs.getStringSet("pending", Collections.emptySet()).isEmpty()) {
                      if (visible && !SessionMonitorService.locked(activity)) requestWrite();
                      else prefs.edit().putBoolean("finish_attempted", false).apply();
                    } else {
                      int saved = prefs.getInt("saved_count", 0);
                      complete();
                      activity.message(
                          "Seria zakończona. Przypisano " + saved + " zdjęć do " + target);
                    }
                  });
            },
            "session-transfer")
        .start();
  }

  private void copy(Uri source, String path) throws IOException {
    ContentValues values = new ContentValues();
    try (Cursor c =
        activity
            .getContentResolver()
            .query(
                source,
                new String[] {"_display_name", "mime_type", MediaStore.Images.Media.DATE_TAKEN},
                null,
                null,
                null)) {
      if (c == null || !c.moveToFirst()) throw new IOException("Zdjęcie nie jest dostępne");
      values.put(
          MediaStore.Images.Media.DISPLAY_NAME, "QC_" + UUID.randomUUID() + "_" + c.getString(0));
      values.put(MediaStore.Images.Media.MIME_TYPE, c.getString(1));
      values.put(MediaStore.Images.Media.DATE_TAKEN, c.getLong(2));
    }
    values.put(MediaStore.Images.Media.RELATIVE_PATH, path);
    values.put(MediaStore.Images.Media.IS_PENDING, 1);
    Uri dest =
        activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    if (dest == null) throw new IOException("Nie utworzono pliku docelowego");
    try {
      try (InputStream in = activity.getContentResolver().openInputStream(source);
          OutputStream out = activity.getContentResolver().openOutputStream(dest)) {
        if (in == null || out == null) throw new IOException("Nie otwarto pliku");
        byte[] buffer = new byte[131072];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      }
      ContentValues finish = new ContentValues();
      finish.put(MediaStore.Images.Media.IS_PENDING, 0);
      if (activity.getContentResolver().update(dest, finish, null, null) != 1)
        throw new IOException("Nie ukończono zapisu");
      HashSet<String> done = new HashSet<>(prefs.getStringSet("copied", Collections.emptySet()));
      done.add(source.getLastPathSegment());
      prefs.edit().putStringSet("copied", done).commit();
    } catch (Exception e) {
      activity.getContentResolver().delete(dest, null, null);
      throw new IOException(e);
    }
  }
}
