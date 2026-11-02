package pl.climagold.qc;

import android.content.*;
import android.net.Uri;
import android.os.storage.*;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

/** Open an actual directory in the phone's file manager, never an image or gallery album. */
final class FolderViewer {
  static final String SAMSUNG_FILES = "com.sec.android.app.myfiles";

  static Intent directory(String relativePath, String documentVolume) {
    if (relativePath == null
        || !relativePath.matches("DCIM/[^/]+/")
        || relativePath.equals("DCIM/../")
        || relativePath.equals("DCIM/./")
        || documentVolume == null
        || !documentVolume.matches("[A-Za-z0-9_-]+"))
      throw new IllegalArgumentException("Nieprawidłowy folder centrali");
    String id = documentVolume + ":" + relativePath.substring(0, relativePath.length() - 1);
    Uri uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", id);
    // The system file manager has its own provider access. Do not grant a URI we do not own.
    return new Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
  }

  static String documentVolume(Context context, String mediaVolume) {
    if (MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(mediaVolume)) return "primary";
    if (mediaVolume != null) {
      for (StorageVolume volume :
          context.getSystemService(StorageManager.class).getStorageVolumes()) {
        if (volume.getUuid() != null && volume.getUuid().equalsIgnoreCase(mediaVolume))
          return volume.getUuid();
      }
    }
    throw new IllegalArgumentException("Nie znaleziono nośnika z folderem centrali");
  }

  static boolean open(Context context, Intent directory) {
    try {
      context.startActivity(new Intent(directory).setPackage(SAMSUNG_FILES));
      return true;
    } catch (ActivityNotFoundException | SecurityException ignored) {
    }
    try {
      context.startActivity(directory);
      return true;
    } catch (ActivityNotFoundException | SecurityException ignored) {
      return false;
    }
  }
}
