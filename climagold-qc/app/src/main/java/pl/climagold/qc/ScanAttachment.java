package pl.climagold.qc;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.util.UUID;

/** Private, durable original scan. Published only after the user confirms its destination. */
final class ScanAttachment {
  private final Context context;
  private final SharedPreferences state;

  ScanAttachment(Context context) {
    this.context = context.getApplicationContext();
    state = context.getSharedPreferences("scan_attachment", 0);
  }

  private static void copyBytes(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[131072];
    int size;
    while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
  }

  synchronized boolean ready() {
    return state.getBoolean("armed", false) && file().isFile();
  }

  private File file() {
    return new File(context.getFilesDir(), state.getString("file", "no-scan"));
  }

  synchronized void discard() {
    File old = file();
    state.edit().clear().commit();
    old.delete();
  }

  synchronized void stage(InputStream input, String mime, boolean armed) throws IOException {
    if (input == null) throw new IOException("Nie otwarto zdjęcia numeru");
    String id = UUID.randomUUID().toString();
    File next = new File(context.getFilesDir(), "scan-" + id);
    try (FileOutputStream out = new FileOutputStream(next)) {
      copyBytes(input, out);
      out.getFD().sync();
      if (next.length() == 0) throw new IOException("Zdjęcie numeru jest puste");
    } catch (IOException e) {
      next.delete();
      throw e;
    }
    File old = file();
    if (!state
        .edit()
        .clear()
        .putString("file", next.getName())
        .putString("id", id)
        .putString("mime", mime == null ? "image/jpeg" : mime)
        .putLong("taken", System.currentTimeMillis())
        .putBoolean("armed", armed)
        .commit()) {
      next.delete();
      throw new IOException("Nie zapamiętano zdjęcia numeru");
    }
    old.delete();
  }

  synchronized void arm() throws IOException {
    if (!file().isFile() || !state.edit().putBoolean("armed", true).commit())
      throw new IOException("Wczytaj ponownie zdjęcie numeru");
  }

  synchronized Uri save(String serial) throws IOException {
    if (!ready()) return null;
    ContentResolver resolver = context.getContentResolver();
    String path = "DCIM/" + Numbers.folder(serial) + "/";
    String mime = state.getString("mime", "image/jpeg");
    String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
    String name = "QC_NUMER_" + state.getString("id", "") + "." + (ext == null ? "jpg" : ext);
    Uri dest = null;
    // A process death after publishing but before clearing the draft must not duplicate the image.
    try (Cursor c =
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            new String[] {MediaStore.Images.Media._ID, MediaStore.Images.Media.IS_PENDING},
            "relative_path=? AND _display_name=?",
            new String[] {path, name},
            null)) {
      if (c == null) throw new IOException("Nie sprawdzono zdjęcia numeru");
      if (c.moveToFirst()) {
        dest =
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0));
        if (c.getInt(1) == 0) {
          discard();
          return dest;
        }
      }
    }
    if (dest == null) {
      ContentValues v = new ContentValues();
      v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
      v.put(MediaStore.Images.Media.MIME_TYPE, mime);
      v.put(MediaStore.Images.Media.RELATIVE_PATH, path);
      v.put(MediaStore.Images.Media.DATE_TAKEN, state.getLong("taken", 0));
      v.put(MediaStore.Images.Media.IS_PENDING, 1);
      dest = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
      if (dest == null) throw new IOException("Nie utworzono zdjęcia numeru");
    }
    try {
      try (InputStream in = new FileInputStream(file());
          OutputStream out = resolver.openOutputStream(dest, "wt")) {
        if (out == null) throw new IOException("Nie otwarto folderu centrali");
        copyBytes(in, out);
      }
      ContentValues publish = new ContentValues();
      publish.put(MediaStore.Images.Media.IS_PENDING, 0);
      if (resolver.update(dest, publish, null, null) != 1)
        throw new IOException("Nie ukończono zapisu numeru");
      discard();
      return dest;
    } catch (Exception e) {
      // Retain the private original for retry. Never discard the scan on write failure.
      try {
        resolver.delete(dest, null, null);
      } catch (Exception ignored) {
      }
      throw new IOException("Nie zapisano zdjęcia numeru: " + e.getMessage(), e);
    }
  }
}
