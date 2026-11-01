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
import android.widget.*;
import java.io.*;
import java.util.*;

/** The OEM camera owns capture; this controller only assigns a confirmed batch afterwards. */
final class NativeSession {
  private static final int READ_REQUEST = 40, WRITE_REQUEST = 41;
  private final MainActivity activity;
  private final SharedPreferences prefs;
  private boolean working;

  NativeSession(MainActivity activity) {
    this.activity = activity;
    prefs = activity.getSharedPreferences("native_camera_session", 0);
  }

  boolean active() {
    return prefs.contains("serial");
  }

  boolean busy() {
    return working;
  }

  private String permission() {
    return Build.VERSION.SDK_INT >= 33
        ? Manifest.permission.READ_MEDIA_IMAGES
        : Manifest.permission.READ_EXTERNAL_STORAGE;
  }

  private boolean canRead() {
    return activity.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
  }

  boolean readBeforeConfirm(String serial) {
    if (canRead()) return true;
    prefs.edit().putString("confirm_number", serial).apply();
    new AlertDialog.Builder(activity)
        .setTitle("Dostęp do zdjęć")
        .setMessage(
            "Potrzebny jest dostęp do wszystkich zdjęć, aby wykrywać wcześniejsze foldery i nowe"
                + " zdjęcia z aparatu telefonu. Zdjęcia pozostają na telefonie.")
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
      new AlertDialog.Builder(activity)
          .setTitle("Zdjęcia z fabrycznego aparatu")
          .setMessage(
              "Aby znaleźć całą serię wykonaną w osobnej aplikacji aparatu, zezwól na dostęp do"
                  + " wszystkich zdjęć. Po powrocie zatwierdzisz zdjęcia do przypisania centrali.")
          .setPositiveButton(
              "Zezwól",
              (d, w) -> activity.requestPermissions(new String[] {permission()}, READ_REQUEST))
          .setNegativeButton("Anuluj", null)
          .show();
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
                  if (c != null && c.moveToFirst()) high = c.getLong(0);
                }
                if (!prefs
                    .edit()
                    .putString("serial", activity.serial)
                    .putLong("after_id", high)
                    .putLong("started", System.currentTimeMillis() / 1000)
                    .commit()) throw new IOException("Nie zapisano sesji");
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      launch();
                    });
              } catch (Exception e) {
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      activity.message("Nie można rozpocząć serii: " + e.getMessage());
                    });
              }
            })
        .start();
  }

  void permissionResult(int request) {
    if (request != READ_REQUEST) return;
    if (canRead()) {
      String number = prefs.getString("confirm_number", null);
      prefs.edit().remove("confirm_number").apply();
      if (active()) panel();
      else if (number != null) activity.confirm(number);
      else start();
    } else
      activity.message(
          "Potrzebny jest dostęp do wszystkich zdjęć. Dostęp tylko do wybranych nie pozwala wykryć"
              + " nowej serii.");
  }

  void launch() {
    if (activity.provider != null) activity.provider.unbindAll();
    activity.capture = null;
    try {
      activity.startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    } catch (ActivityNotFoundException e) {
      activity.message("Brak aplikacji aparatu obsługującej ten tryb.");
      panel();
    }
  }

  void panel() {
    if (!active() || working) return;
    if (activity.provider != null) activity.provider.unbindAll();
    activity.capture = null;
    activity.serial = prefs.getString("serial", "");
    activity.layout();
    activity.hint("02 — ZDJĘCIA  /  03 — PRZYPISANIE");
    activity.label("Seria · " + activity.serial);
    activity.hint(
        "Po zdjęciach wróć tutaj przyciskiem Wstecz lub przełącznikiem aplikacji. Aparat zachowuje"
            + " własny wygląd i funkcje.");
    activity.button("Otwórz ponownie aparat", this::launch);
    activity.action("Wybierz i przypisz zdjęcia", this::review, true);
    activity.button("Galeria centrali", () -> activity.gallery(0));
    activity.button(
        "Zakończ bez przypisywania",
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("Zakończyć serię?")
                .setMessage(
                    "Zdjęcia pozostaną tam, gdzie zapisał je aparat. Nic nie zostanie usunięte.")
                .setPositiveButton(
                    "Zakończ",
                    (d, w) -> {
                      prefs.edit().clear().apply();
                      activity.home();
                    })
                .setNegativeButton("Wróć", null)
                .show());
    activity.scrollable();
  }

  private static final class Photo {
    final Uri uri;
    final String name;

    Photo(long id, String name) {
      uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
      this.name = name;
    }
  }

  void review() {
    if (working) return;
    if (!canRead()) {
      activity.requestPermissions(new String[] {permission()}, READ_REQUEST);
      return;
    }
    working = true;
    new Thread(
            () -> {
              ArrayList<Photo> found = new ArrayList<>();
              try {
                String target = "DCIM/" + Numbers.folder(prefs.getString("serial", "")) + "/";
                Set<String> done = prefs.getStringSet("copied", Collections.emptySet());
                try (Cursor c =
                    activity
                        .getContentResolver()
                        .query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[] {"_id", "_display_name", "relative_path"},
                            "_id > ? AND date_added >= ? AND is_pending = 0"
                                + (Build.VERSION.SDK_INT >= 30 ? " AND is_trashed = 0" : ""),
                            new String[] {
                              Long.toString(prefs.getLong("after_id", 0)),
                              Long.toString(prefs.getLong("started", 0))
                            },
                            "_id ASC")) {
                  if (c != null)
                    while (c.moveToNext()) {
                      String path = c.getString(2);
                      long id = c.getLong(0);
                      if (path != null
                          && path.startsWith("DCIM/")
                          && !path.equals(target)
                          && !done.contains(Long.toString(id)))
                        found.add(new Photo(id, c.getString(1)));
                    }
                }
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      showSelection(found);
                    });
              } catch (Exception e) {
                activity.runOnUiThread(
                    () -> {
                      working = false;
                      activity.message("Nie odczytano zdjęć: " + e.getMessage());
                    });
              }
            })
        .start();
  }

  private void showSelection(ArrayList<Photo> photos) {
    if (photos.isEmpty()) {
      activity.message(
          "Nie znaleziono nowych zdjęć w DCIM. Poczekaj, aż aparat skończy zapis, i spróbuj"
              + " ponownie.");
      return;
    }
    boolean[] selected = new boolean[photos.size()];
    Arrays.fill(selected, true);
    String[] names = new String[photos.size()];
    for (int i = 0; i < names.length; i++) names[i] = photos.get(i).name;
    new AlertDialog.Builder(activity)
        .setTitle("Zdjęcia dla " + prefs.getString("serial", "") + " • " + photos.size())
        .setMultiChoiceItems(names, selected, (d, i, checked) -> selected[i] = checked)
        .setNeutralButton("Wróć", null)
        .setPositiveButton(
            Build.VERSION.SDK_INT >= 30 ? "Przenieś serię" : "Kopiuj serię",
            (d, w) -> {
              HashSet<String> uris = new HashSet<>();
              for (int i = 0; i < selected.length; i++)
                if (selected[i]) uris.add(photos.get(i).uri.toString());
              if (uris.isEmpty()) return;
              prefs.edit().putStringSet("pending", uris).commit();
              if (Build.VERSION.SDK_INT >= 30) {
                try {
                  ArrayList<Uri> list = new ArrayList<>();
                  for (String u : uris) list.add(Uri.parse(u));
                  activity.startIntentSenderForResult(
                      MediaStore.createWriteRequest(activity.getContentResolver(), list)
                          .getIntentSender(),
                      WRITE_REQUEST,
                      null,
                      0,
                      0,
                      0);
                } catch (Exception e) {
                  activity.message("Nie uzyskano zgody na przeniesienie: " + e.getMessage());
                }
              } else transfer();
            })
        .show();
  }

  void result(int request, int result) {
    if (request != WRITE_REQUEST) return;
    if (result == Activity.RESULT_OK) transfer();
    else activity.message("Nie przeniesiono zdjęć. Seria jest nadal otwarta.");
  }

  private void transfer() {
    if (working) return;
    working = true;
    activity.layout();
    activity.label("Przypisywanie zdjęć… Nie zamykaj aplikacji.");
    final String target = "DCIM/" + Numbers.folder(prefs.getString("serial", "")) + "/";
    final Set<String> uris = new HashSet<>(prefs.getStringSet("pending", Collections.emptySet()));
    new Thread(
            () -> {
              int success = 0;
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
                  success++;
                } catch (Exception e) {
                  errors.add(e.getMessage() == null ? "Błąd dostępu" : e.getMessage());
                }
              }
              final int saved = success;
              if (errors.isEmpty()) prefs.edit().clear().commit();
              activity.runOnUiThread(
                  () -> {
                    working = false;
                    if (errors.isEmpty()) {
                      activity.home();
                      activity.message("Zapisano " + saved + " zdjęć w " + target);
                    } else {
                      panel();
                      new AlertDialog.Builder(activity)
                          .setTitle("Przypisano " + saved + " z " + uris.size())
                          .setMessage(
                              "Pozostałe zdjęcia są w oryginalnym folderze. Możesz ponowić wybór"
                                  + " serii.\n"
                                  + errors.get(0))
                          .setPositiveButton("OK", null)
                          .show();
                    }
                  });
            })
        .start();
  }

  private void copy(Uri source, String path) throws IOException {
    ContentValues values = new ContentValues();
    try (Cursor c =
        activity
            .getContentResolver()
            .query(
                source,
                new String[] {"_display_name", "mime_type", "date_taken"},
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
