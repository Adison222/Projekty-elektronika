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

  @Test
  public void albumUsesActualBucketWithoutGrantingTheWholeCollection() {
    Intent i =
        ExternalGallery.album(
            List.of("-12345", "-12345"), List.of("external_primary", "external_primary"));
    assertEquals(Intent.ACTION_VIEW, i.getAction());
    assertEquals(MediaStore.Images.Media.CONTENT_TYPE, i.getType());
    assertEquals("-12345", i.getData().getQueryParameter("bucketId"));
    assertEquals("com.sec.android.gallery3d", i.getPackage());
    assertEquals(0, i.getFlags());
    assertNull(i.getClipData());
    assertNotNull(
        ExternalGallery.album(
            Collections.nCopies(600, "1"), Collections.nCopies(600, "external_primary")));
  }

  @Test
  public void albumCannotDropPhotosFromOtherFoldersOrVolumes() {
    assertNull(
        ExternalGallery.album(List.of("1", "2"), List.of("external_primary", "external_primary")));
    assertNull(ExternalGallery.album(List.of("1", "1"), List.of("external_primary", "abcd-1234")));
    assertNull(ExternalGallery.album(List.of(""), List.of("external_primary")));
    assertNull(ExternalGallery.album(List.of(), List.of()));
  }

  @Test
  public void unsupportedAlbumFallsBackToSubsetAndPreservesFailureReason() {
    Intent album = ExternalGallery.album(List.of("1"), List.of("external_primary"));
    Intent subset =
        ExternalGallery.samsung(List.of(Uri.parse("content://media/external/images/media/4")), 0);
    List<String> attempted = new ArrayList<>();
    android.content.Context context =
        new android.content.ContextWrapper(org.robolectric.RuntimeEnvironment.getApplication()) {
          @Override
          public void startActivity(Intent i) {
            attempted.add(i.getAction());
            if (Intent.ACTION_VIEW.equals(i.getAction()))
              throw new android.content.ActivityNotFoundException();
          }
        };
    assertTrue(ExternalGallery.open(context, album, subset).opened);
    assertEquals(List.of(Intent.ACTION_VIEW, MediaStore.ACTION_REVIEW_SECURE), attempted);
    android.content.Context denied =
        new android.content.ContextWrapper(context) {
          @Override
          public void startActivity(Intent i) {
            throw new SecurityException();
          }
        };
    var failed = ExternalGallery.open(denied, album, subset);
    assertFalse(failed.opened);
    assertTrue(failed.detail.contains("odmówił dostępu"));
  }
}
