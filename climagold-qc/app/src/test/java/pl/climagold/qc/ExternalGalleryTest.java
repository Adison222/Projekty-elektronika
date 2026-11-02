package pl.climagold.qc;

import static org.junit.Assert.*;

import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ExternalGalleryTest {
  @Test
  public void samsungReceivesOnlyTheSelectedPhotosAndReadAccess() {
    List<Uri> photos =
        List.of(
            Uri.parse("content://media/external/images/media/4"),
            Uri.parse("content://media/external/images/media/9"));
    Intent i = ExternalGallery.samsung(photos, 1);
    assertEquals(MediaStore.ACTION_REVIEW_SECURE, i.getAction());
    assertEquals("com.sec.android.gallery3d", i.getPackage());
    assertEquals(photos.get(1), i.getData());
    assertEquals(2, i.getClipData().getItemCount());
    for (int n = 0; n < 2; n++) assertEquals(photos.get(n), i.getClipData().getItemAt(n).getUri());
    assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, i.getFlags());
  }

  @Test
  public void emptyOrOversizeFolderIsNotSilentlyTruncatedOrOpenedUnfiltered() {
    assertNull(ExternalGallery.samsung(Collections.emptyList(), 0));
    assertNull(
        ExternalGallery.samsung(
            Collections.nCopies(501, Uri.parse("content://media/external/images/media/1")), 0));
  }
}
