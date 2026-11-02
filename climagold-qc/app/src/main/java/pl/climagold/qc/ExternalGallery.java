package pl.climagold.qc;

import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.*;

/** REVIEW_SECURE has a documented explicit-media-list contract; plain VIEW does not. */
final class ExternalGallery {
  static final String PACKAGE = "com.sec.android.gallery3d";

  /** Gallery2-compatible album request. OEM support must be checked on the device. */
  static Intent album(List<String> buckets, List<String> volumes) {
    if (buckets.isEmpty() || buckets.size() != volumes.size()) return null;
    String bucket = buckets.get(0), volume = volumes.get(0);
    if (bucket == null || !bucket.matches("-?[0-9]+") || volume == null) return null;
    for (int n = 0; n < buckets.size(); n++)
      if (!bucket.equals(buckets.get(n)) || !volume.equals(volumes.get(n))) return null;
    Uri uri =
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            .buildUpon()
            .appendQueryParameter("bucketId", bucket)
            .build();
    return new Intent(Intent.ACTION_VIEW)
        .setPackage(PACKAGE)
        .setDataAndType(uri, MediaStore.Images.Media.CONTENT_TYPE);
  }

  static final class Result {
    final boolean opened;
    final String detail;

    Result(boolean opened, String detail) {
      this.opened = opened;
      this.detail = detail;
    }
  }

  static Result open(Context context, Intent album, Intent subset) {
    List<String> failures = new ArrayList<>();
    Intent[] choices = {album, subset};
    String[] names = {"Album", "Zestaw zdjęć"};
    for (int n = 0; n < choices.length; n++) {
      if (choices[n] == null) continue;
      try {
        context.startActivity(choices[n]);
        return new Result(true, names[n]);
      } catch (ActivityNotFoundException e) {
        failures.add(names[n] + ": brak obsługi w Galerii Samsung");
      } catch (SecurityException e) {
        failures.add(names[n] + ": Android odmówił dostępu");
      }
    }
    return new Result(
        false,
        failures.isEmpty()
            ? "Brak pojedynczego albumu lub zestaw przekracza limit podglądu."
            : String.join("\n", failures));
  }

  static Intent samsung(List<Uri> photos, int index) {
    if (photos.isEmpty() || photos.size() > 500) return null;
    Intent intent = new Intent(MediaStore.ACTION_REVIEW_SECURE);
    intent.setPackage(PACKAGE);
    intent.setDataAndType(photos.get(Math.max(0, Math.min(index, photos.size() - 1))), "image/*");
    ClipData clip =
        new ClipData(
            "Zdjęcia centrali", new String[] {"image/*"}, new ClipData.Item(photos.get(0)));
    for (int i = 1; i < photos.size(); i++) clip.addItem(new ClipData.Item(photos.get(i)));
    intent.setClipData(clip);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return intent;
  }
}
