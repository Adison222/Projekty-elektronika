package pl.climagold.qc;

import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.List;

/** REVIEW_SECURE has a documented explicit-media-list contract; plain VIEW does not. */
final class ExternalGallery {
  static Intent samsung(List<Uri> photos, int index) {
    if (photos.isEmpty() || photos.size() > 500) return null;
    Intent intent = new Intent(MediaStore.ACTION_REVIEW_SECURE);
    intent.setPackage("com.sec.android.gallery3d");
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
