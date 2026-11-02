package pl.climagold.qc;

import static org.junit.Assert.*;

import android.content.*;
import android.provider.DocumentsContract;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FolderViewerTest {
  @Test
  public void opensActualKsDirectoryNotImageAlbumOrPicker() {
    Intent i = FolderViewer.directory("DCIM/KS303/", "primary");
    assertEquals(Intent.ACTION_VIEW, i.getAction());
    assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, i.getType());
    assertEquals("primary:DCIM/KS303", DocumentsContract.getDocumentId(i.getData()));
    assertEquals("com.android.externalstorage.documents", i.getData().getAuthority());
    assertNull(i.getClipData());
    assertEquals(0, i.getFlags());
  }

  @Test
  public void keepsExactVolumeAndUnicodeFolderName() {
    Intent i = FolderViewer.directory("DCIM/IEW∕123/", "ABCD-1234");
    assertEquals("ABCD-1234:DCIM/IEW∕123", DocumentsContract.getDocumentId(i.getData()));
    assertEquals(
        "primary",
        FolderViewer.documentVolume(RuntimeEnvironment.getApplication(), "external_primary"));
    assertThrows(
        IllegalArgumentException.class, () -> FolderViewer.directory("DCIM/../", "primary"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FolderViewer.directory("DCIM/KS303/other/", "primary"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FolderViewer.documentVolume(RuntimeEnvironment.getApplication(), "missing-volume"));
  }

  @Test
  public void samsungFailureFallsBackOnlyToFolderViewer() {
    List<Intent> attempted = new ArrayList<>();
    Context ctx =
        new ContextWrapper(RuntimeEnvironment.getApplication()) {
          @Override
          public void startActivity(Intent i) {
            attempted.add(i);
            if (i.getPackage() != null) throw new ActivityNotFoundException();
          }
        };
    assertTrue(FolderViewer.open(ctx, FolderViewer.directory("DCIM/KS303/", "primary")));
    assertEquals(2, attempted.size());
    assertEquals(FolderViewer.SAMSUNG_FILES, attempted.get(0).getPackage());
    assertNull(attempted.get(1).getPackage());
    for (Intent i : attempted) assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, i.getType());
    Context denied =
        new ContextWrapper(ctx) {
          @Override
          public void startActivity(Intent i) {
            throw new SecurityException();
          }
        };
    assertFalse(FolderViewer.open(denied, FolderViewer.directory("DCIM/KS303/", "primary")));
  }
}
