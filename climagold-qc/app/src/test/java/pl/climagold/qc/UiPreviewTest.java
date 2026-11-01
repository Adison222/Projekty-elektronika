package pl.climagold.qc;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiPreviewTest {
  @Test public void renderPhoneScreens() throws Exception {
    var controller=Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
    MainActivity activity=controller.get();
    snapshot(activity,"home");activity.numberEntry();snapshot(activity,"number");
    assertNotNull(activity.input);
    controller.pause().stop().destroy();
  }
  private void snapshot(MainActivity activity,String name) throws Exception {
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    View decor=activity.getWindow().getDecorView();
    decor.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1702,View.MeasureSpec.EXACTLY));
    decor.layout(0,0,786,1702);activity.root.animate().cancel();activity.root.setAlpha(1);activity.root.setTranslationY(0);
    Bitmap bitmap=Bitmap.createBitmap(786,1702,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));
    File directory=new File("build/previews");directory.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }
}
