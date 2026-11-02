package pl.climagold.qc;

import static org.junit.Assert.*;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScanAttachmentTest {
  Context context;
  Store provider;
  ScanAttachment attachment;
  byte[] original = new byte[] {(byte) 255, (byte) 216, 1, 2, 3, 4, 5};

  @Before
  public void prepare() throws Exception {
    context = RuntimeEnvironment.getApplication();
    provider = new Store();
    provider.dir = Files.createTempDirectory("scan-test").toFile();
    android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
    info.authority = "media";
    provider.attachInfo(context, info);
    ShadowContentResolver.registerProviderInternal("media", provider);
    attachment = new ScanAttachment(context);
    attachment.discard();
  }

  @Test
  public void originalIsSavedOnlyAfterNumberConfirmationAndOnlyOnce() throws Exception {
    attachment.stage(new ByteArrayInputStream(original), "image/jpeg", true);
    assertTrue(attachment.ready());
    assertEquals(0, provider.inserts);
    Uri image = attachment.save("R14577");
    assertEquals("DCIM/R14577/", provider.rows.get(image).getAsString("relative_path"));
    assertEquals(Integer.valueOf(0), provider.rows.get(image).getAsInteger("is_pending"));
    assertArrayEquals(original, Files.readAllBytes(provider.files.get(image).toPath()));
    assertNull(attachment.save("R14577"));
    assertEquals(1, provider.inserts);
    assertFalse(attachment.ready());
  }

  @Test
  public void importMustBeReadBeforeItBecomesAnAttachment() throws Exception {
    attachment.stage(new ByteArrayInputStream(original), "image/png", false);
    assertNull(attachment.save("C13932"));
    assertEquals(0, provider.inserts);
    attachment.arm();
    Uri image = attachment.save("C13932");
    assertEquals("DCIM/13932/", provider.rows.get(image).getAsString("relative_path"));
    assertEquals("image/png", provider.rows.get(image).getAsString("mime_type"));
  }

  @Test
  public void failedPublicationRetainsOriginalForRetryAfterRecreation() throws Exception {
    attachment.stage(new ByteArrayInputStream(original), "image/jpeg", true);
    provider.failPublish = true;
    assertThrows(IOException.class, () -> attachment.save("IEW123"));
    assertTrue(attachment.ready());
    assertTrue(provider.rows.isEmpty());
    provider.failPublish = false;
    Uri image = new ScanAttachment(context).save("IEW123");
    assertArrayEquals(original, Files.readAllBytes(provider.files.get(image).toPath()));
  }

  @Test
  public void discardedOrReplacedScanCannotLeakIntoNextCentral() throws Exception {
    attachment.stage(new ByteArrayInputStream(original), "image/jpeg", true);
    attachment.stage(new ByteArrayInputStream(new byte[] {6, 7}), "image/jpeg", true);
    Uri image = attachment.save("K42");
    assertArrayEquals(new byte[] {6, 7}, Files.readAllBytes(provider.files.get(image).toPath()));
    attachment.stage(new ByteArrayInputStream(original), "image/jpeg", true);
    attachment.discard();
    assertNull(attachment.save("Z123"));
    assertEquals(1, provider.inserts);
  }

  static class Store extends ContentProvider {
    File dir;
    int inserts;
    boolean failPublish;
    Map<Uri, ContentValues> rows = new LinkedHashMap<>();
    Map<Uri, File> files = new HashMap<>();

    public boolean onCreate() {
      return true;
    }

    public String getType(Uri u) {
      return "image/jpeg";
    }

    public Cursor query(Uri u, String[] columns, String selection, String[] args, String sort) {
      MatrixCursor c = new MatrixCursor(columns);
      for (var e : rows.entrySet())
        if (e.getValue().getAsString("relative_path").equals(args[0])
            && e.getValue().getAsString("_display_name").equals(args[1]))
          c.addRow(
              new Object[] {
                Long.parseLong(e.getKey().getLastPathSegment()),
                e.getValue().getAsInteger("is_pending")
              });
      return c;
    }

    public Uri insert(Uri u, ContentValues v) {
      Uri dest = ContentUris.withAppendedId(u, ++inserts);
      rows.put(dest, new ContentValues(v));
      files.put(dest, new File(dir, "image-" + inserts));
      return dest;
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
      if (failPublish) return 0;
      rows.get(u).putAll(v);
      return 1;
    }

    public int delete(Uri u, String s, String[] a) {
      rows.remove(u);
      File f = files.remove(u);
      if (f != null) f.delete();
      return 1;
    }

    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
      return ParcelFileDescriptor.open(files.get(u), ParcelFileDescriptor.parseMode(mode));
    }
  }
}
