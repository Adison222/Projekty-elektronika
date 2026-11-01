package pl.climagold.qc;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class InterfaceTest {
  @Test public void homeHasClearEntryAndBrowserAndManualSuggestionsAreAutomatic() {
    var controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
    MainActivity a=controller.get();
    assertNotNull(button(a.root,"+  Nowa seria zdjęć"));
    assertNotNull(button(a.root,"Centrale i zdjęcia"));
    button(a.root,"+  Nowa seria zdjęć").performClick();
    a.input.setText("R14577");
    assertNotNull(button(a.root,"R14577"));
    controller.pause().stop().destroy();
  }
  @Test public void ocrSuggestionsAppearInsideResultWithoutExtraAction() {
    var controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
    MainActivity a=controller.get();a.reviewText("C13932", "C13932");
    AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(button(d.getWindow().getDecorView(),"C13932"));
    assertNull(button(d.getWindow().getDecorView(),"Podpowiedz oznaczenie C / K / IEW / RIEW / R / Z"));
    d.dismiss();controller.pause().stop().destroy();
  }
  static Button button(View v,String text) {
    if(v instanceof Button && text.contentEquals(((Button)v).getText()))return (Button)v;
    if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=button(((ViewGroup)v).getChildAt(i),text);if(b!=null)return b;}
    return null;
  }
}
