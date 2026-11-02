package pl.climagold.qc;

import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.util.*;

/**
 * Rename through MediaStore in small batches; never re-encode or delete source pictures. API 30+.
 */
@androidx.annotation.RequiresApi(30)
final class PhotoMover {
  static Set<String> move(
      ContentResolver resolver, List<String> uris, String folder, List<String> errors) {
    Set<String> moved = new HashSet<>();
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
    for (String value : uris)
      operations.add(
          ContentProviderOperation.newUpdate(Uri.parse(value))
              .withValue(MediaStore.Images.Media.RELATIVE_PATH, folder)
              .withExpectedCount(1)
              .withExceptionAllowed(true)
              .build());
    try {
      ContentProviderResult[] results = resolver.applyBatch(MediaStore.AUTHORITY, operations);
      for (int i = 0; i < uris.size(); i++) {
        if (i < results.length
            && results[i].exception == null
            && Integer.valueOf(1).equals(results[i].count)) moved.add(uris.get(i));
        else errors.add("Nie zmieniono folderu jednego ze zdjęć");
      }
    } catch (Exception batchFailure) {
      // Some OEM providers do not support batched mutations. Renaming to the same folder is safe.
      for (String value : uris) {
        try {
          ContentValues update = new ContentValues();
          update.put(MediaStore.Images.Media.RELATIVE_PATH, folder);
          if (resolver.update(Uri.parse(value), update, null, null) != 1)
            throw new IOException("Nie zmieniono folderu");
          moved.add(value);
        } catch (Exception e) {
          errors.add(e.getMessage() == null ? "Błąd przenoszenia" : e.getMessage());
        }
      }
    }
    return moved;
  }
}
