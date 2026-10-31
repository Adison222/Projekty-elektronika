package pl.climagold.qc;

import android.Manifest;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;

public class MainActivity extends ComponentActivity {
  NativeSession nativeSession;
  ProcessCameraProvider provider;
  LinearLayout root;
  TextView status;
  EditText input;
  ImageCapture capture;
  String serial = "";
  boolean busy = false;
  int count = 0;
  androidx.camera.core.Camera camera;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    nativeSession = new NativeSession(this);
    getWindow().setStatusBarColor(Color.rgb(17, 25, 32));
    getWindow().setNavigationBarColor(Color.rgb(17, 25, 32));
    serial = getPreferences(0).getString("lastSerial", "");
    android.view.Display display = getWindowManager().getDefaultDisplay();
    for (android.view.Display.Mode mode : display.getSupportedModes()) {
      if (mode.getPhysicalWidth() == display.getMode().getPhysicalWidth()
          && mode.getPhysicalHeight() == display.getMode().getPhysicalHeight()
          && mode.getRefreshRate() >= 119
          && mode.getRefreshRate() <= 121) {
        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredDisplayModeId = mode.getModeId();
        getWindow().setAttributes(params);
        break;
      }
    }
    if (b != null) {
      serial = b.getString("serial", "");
      count = b.getInt("count");
    }
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new androidx.activity.OnBackPressedCallback(true) {
              public void handleOnBackPressed() {
                if (busy || nativeSession.busy()) {
                  message("Poczekaj na zapis zdjęcia");
                  return;
                }
                home();
              }
            });
    home();
    if (checkSelfPermission(Manifest.permission.CAMERA)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.CAMERA}, 1);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (nativeSession != null && nativeSession.active()) nativeSession.panel();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    nativeSession.permissionResult(request);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    nativeSession.result(request, result);
  }

  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("serial", serial);
    b.putInt("count", count);
  }

  void layout() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 40, 24, 32);
    root.setBackgroundColor(Color.rgb(17, 25, 32));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              24,
              insets.getSystemWindowInsetTop() + 16,
              24,
              insets.getSystemWindowInsetBottom() + 16);
          return insets;
        });
    root.requestApplyInsets();
  }

  void label(String s) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(Color.WHITE);
    t.setTextSize(22);
    t.setPadding(0, 16, 0, 16);
    root.addView(t);
  }

  void button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setMinHeight(56);
    b.setTextColor(Color.rgb(17, 25, 32));
    b.setTextSize(16);
    b.setAllCaps(false);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(Color.rgb(222, 183, 94));
    bg.setCornerRadius(28);
    b.setBackground(bg);
    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(-1, (int) (56 * getResources().getDisplayMetrics().density));
    lp.setMargins(0, 8, 0, 0);
    root.addView(b, lp);
    b.setOnClickListener(v -> r.run());
  }

  void home() {
    if (nativeSession.active()) {
      nativeSession.panel();
      return;
    }
    if (provider != null) provider.unbindAll();
    capture = null;
    layout();
    ImageView logo = new ImageView(this);
    logo.setImageResource(pl.climagold.qc.R.drawable.climagold);
    logo.setAdjustViewBounds(true);
    root.addView(logo, new LinearLayout.LayoutParams(-1, 100));
    label("Kontrola jakości • v1.1 preview");
    label("1. Wybierz centralę");
    input = new EditText(this);
    input.setTextColor(Color.WHITE);
    input.setHintTextColor(Color.LTGRAY);
    input.setHint("C14015-23, K4567, IEW…");
    input.setText(serial);
    root.addView(input);
    button("Skanuj kartę / naklejkę", () -> openCamera(true));
    button("Odczytaj wpisany numer", () -> choose(input.getText().toString()));
    if (!serial.isEmpty()) button("Galeria centrali", () -> gallery(0));
    if (!serial.isEmpty()) button("Wróć do serii: " + serial, () -> openCamera(false));
  }

  void choose(String text) {
    List<String> options = Numbers.parse(text);
    if (options.isEmpty()) {
      new android.app.AlertDialog.Builder(this)
          .setTitle("Nie rozpoznano numeru")
          .setMessage("Zbliż kartę lub wpisz numer z prefiksem C, K, IEW, RIEW albo Z.\n\n" + text)
          .setPositiveButton("Popraw", (d, w) -> home())
          .show();
      return;
    }
    new android.app.AlertDialog.Builder(this)
        .setTitle("Wybierz i zatwierdź numer")
        .setItems(options.toArray(new String[0]), (d, w) -> confirm(options.get(w)))
        .show();
  }

  void confirm(String value) {
    if (!nativeSession.readBeforeConfirm(value)) return;
    String path = "DCIM/" + Numbers.folder(value) + "/";
    boolean exists = false;
    try (android.database.Cursor c =
        getContentResolver()
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {"_id"},
                "relative_path=?",
                new String[] {path},
                null)) {
      exists = c != null && c.moveToFirst();
    } catch (Exception e) {
      message("Nie udało się sprawdzić folderu: " + e.getMessage());
      return;
    }
    new android.app.AlertDialog.Builder(this)
        .setTitle(value)
        .setMessage(
            (exists
                    ? "Ten numer ma już zdjęcia. Dopiszę kolejne do tego samego folderu.\n"
                    : "Nowa seria zdjęć.\n")
                + path)
        .setPositiveButton(
            "Rozpocznij",
            (d, w) -> {
              serial = value;
              getPreferences(0).edit().putString("lastSerial", value).apply();
              count = 0;
              openCamera(false);
            })
        .setNegativeButton("Popraw", (d, w) -> home())
        .show();
  }

  void openCamera(boolean scan) {
    if (!scan) {
      nativeSession.start();
      return;
    }
    if (checkSelfPermission(Manifest.permission.CAMERA)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      message("Zezwól aplikacji na dostęp do aparatu w ustawieniach telefonu.");
      return;
    }
    layout();
    label("Skaner karty lub naklejki");
    PreviewView preview = new PreviewView(this);
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    status = new TextView(this);
    status.setTextColor(Color.WHITE);
    root.addView(status);
    button(
        "Lampa: auto / włączona / wyłączona",
        () -> {
          if (capture != null) {
            int mode = (capture.getFlashMode() + 1) % 3;
            capture.setFlashMode(mode);
            message(
                mode == 0
                    ? "Lampa automatyczna"
                    : mode == 1 ? "Lampa włączona" : "Lampa wyłączona");
          }
        });
    button("Odczytaj numer", () -> shoot());
    button(
        "OK • zakończ",
        () -> {
          if (!busy) home();
        });
    var future = ProcessCameraProvider.getInstance(this);
    future.addListener(
        () -> {
          try {
            provider = future.get();
            provider.unbindAll();
            Preview p = new Preview.Builder().build();
            p.setSurfaceProvider(preview.getSurfaceProvider());
            capture =
                new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .setResolutionSelector(
                        new androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy
                                    .HIGHEST_AVAILABLE_STRATEGY)
                            .build())
                    .build();
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, capture);
            ScaleGestureDetector zoom =
                new ScaleGestureDetector(
                    this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                      public boolean onScale(ScaleGestureDetector d) {
                        var z = camera.getCameraInfo().getZoomState().getValue();
                        if (z != null)
                          camera
                              .getCameraControl()
                              .setZoomRatio(
                                  Math.max(
                                      z.getMinZoomRatio(),
                                      Math.min(
                                          z.getMaxZoomRatio(),
                                          z.getZoomRatio() * d.getScaleFactor())));
                        return true;
                      }
                    });
            preview.setOnTouchListener(
                (v, e) -> {
                  if (e.getAction() == MotionEvent.ACTION_UP) v.performClick();
                  zoom.onTouchEvent(e);
                  if (e.getAction() == MotionEvent.ACTION_UP && !zoom.isInProgress())
                    camera
                        .getCameraControl()
                        .startFocusAndMetering(
                            new FocusMeteringAction.Builder(
                                    preview
                                        .getMeteringPointFactory()
                                        .createPoint(e.getX(), e.getY()))
                                .build());
                  return true;
                });
          } catch (Exception e) {
            message(e.toString());
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  @androidx.annotation.OptIn(markerClass = ExperimentalGetImage.class)
  void shoot() {
    if (capture == null || busy) return;
    busy = true;
    status.setText("Przetwarzanie…");
    capture.setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());
    {
      capture.takePicture(
          ContextCompat.getMainExecutor(this),
          new ImageCapture.OnImageCapturedCallback() {
            public void onCaptureSuccess(ImageProxy image) {
              if (image.getImage() == null) {
                image.close();
                busy = false;
                return;
              }
              var recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
              recognizer
                  .process(
                      InputImage.fromMediaImage(
                          image.getImage(), image.getImageInfo().getRotationDegrees()))
                  .addOnSuccessListener(t -> choose(t.getText()))
                  .addOnFailureListener(e -> message(e.toString()))
                  .addOnCompleteListener(
                      t -> {
                        image.close();
                        recognizer.close();
                        busy = false;
                      });
            }

            public void onError(ImageCaptureException e) {
              busy = false;
              message(e.toString());
            }
          });
      return;
    }
  }

  void gallery(int requested) {
    if (provider != null) provider.unbindAll();
    capture = null;
    ArrayList<Uri> photos = new ArrayList<>();
    try (android.database.Cursor c =
        getContentResolver()
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {"_id"},
                "relative_path=?",
                new String[] {"DCIM/" + Numbers.folder(serial) + "/"},
                "date_added DESC")) {
      if (c != null)
        while (c.moveToNext())
          photos.add(
              ContentUris.withAppendedId(
                  MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)));
    } catch (Exception e) {
      message(e.toString());
      return;
    }
    if (photos.isEmpty()) {
      message("Brak zdjęć tej centrali");
      return;
    }
    int index = Math.max(0, Math.min(requested, photos.size() - 1));
    layout();
    label(serial + " • " + (index + 1) + " / " + photos.size());
    PhotoView photo = new PhotoView(this);
    root.addView(photo, new LinearLayout.LayoutParams(-1, 0, 1));
    new Thread(
            () -> {
              try {
                android.graphics.Bitmap bitmap =
                    android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(
                            getContentResolver(), photos.get(index)),
                        (decoder, info, source) -> {
                          int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                          decoder.setTargetSampleSize(Math.max(1, max / 2048));
                        });
                runOnUiThread(
                    () -> {
                      photo.setImageBitmap(bitmap);
                      photo.post(photo::fit);
                    });
              } catch (Exception e) {
                runOnUiThread(() -> message("Nie można otworzyć zdjęcia"));
              }
            })
        .start();
    button("Poprzednie", () -> gallery(index - 1));
    button("Następne", () -> gallery(index + 1));
    button("Wróć do aparatu", () -> openCamera(false));
    button("Zakończ", () -> home());
  }

  void message(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}
