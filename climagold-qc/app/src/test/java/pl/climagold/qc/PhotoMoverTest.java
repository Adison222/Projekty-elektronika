package pl.climagold.qc;

import static org.junit.Assert.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class PhotoMoverTest {
  @Test public void failedPhotoDoesNotPreventOtherMovesAndBatchIsUsed() {
    Store store = install(false);
    List<String> input=List.of("content://media/external/images/media/1", "content://media/external/images/media/2", "content://media/external/images/media/3");
    List<String> errors = new ArrayList<>();
    var done = PhotoMover.move(RuntimeEnvironment.getApplication().getContentResolver(), input, "DCIM/123/", errors);
    assertEquals(Set.of(input.get(0), input.get(2)), done);
    assertEquals(1, errors.size()); assertEquals(1, store.batches); assertEquals(3,store.updates);
    assertEquals("DCIM/123/",store.folder);
  }
  @Test public void oemWithoutBatchSupportFallsBackToIndividualRenames() {
    Store store = install(true);
    var errors = new ArrayList<String>();
    var done = PhotoMover.move(RuntimeEnvironment.getApplication().getContentResolver(), List.of("content://media/external/images/media/1"), "DCIM/R14577/", errors);
    assertEquals(1,done.size()); assertTrue(errors.isEmpty()); assertEquals(1,store.batches); assertEquals(1,store.updates);
  }
  Store install(boolean reject) {
    Store store=new Store();store.reject=reject;
    var info=new android.content.pm.ProviderInfo();info.authority="media";
    store.attachInfo(RuntimeEnvironment.getApplication(),info);
    ShadowContentResolver.registerProviderInternal("media",store);return store;
  }
  static class Store extends ContentProvider {
    int batches,updates; boolean reject; String folder;
    public boolean onCreate(){return true;}
    public String getType(Uri u){return "image/jpeg";}
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new AssertionError("Move must not read image bytes");}
    public Uri insert(Uri u,ContentValues v){throw new AssertionError("Move must not create a copy");}
    public int delete(Uri u,String s,String[] a){throw new AssertionError("Move must not delete images");}
    public int update(Uri u,ContentValues v,String s,String[] a){updates++;folder=v.getAsString("relative_path");return u.getLastPathSegment().equals("2")?0:1;}
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> ops) throws OperationApplicationException {
      batches++; if(reject)throw new UnsupportedOperationException();return super.applyBatch(ops);
    }
  }
}
