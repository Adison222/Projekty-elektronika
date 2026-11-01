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
  static final int INK = Color.rgb(14, 23, 38), SURFACE = Color.rgb(26, 39, 58);
  static final int GOLD = Color.rgb(242, 202, 122), MUTED = Color.rgb(169, 186, 207);
  boolean generalNumbers;
  String draft = "";
  int screenGeneration;
  android.graphics.Bitmap importedPhoto;
  androidx.activity.result.ActivityResultLauncher<String> photoPicker;

  androidx.camera.core.Camera camera;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    nativeSession = new NativeSession(this);
    generalNumbers = getPreferences(0).getBoolean("generalNumbers", false);
    photoPicker =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
              if (uri != null) importPhoto(uri);
            });
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
                saveDraft();
                home();
              }
            });
    draft = b == null ? serial : b.getString("draft", serial);
    home();
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
    if (request == 1) {
      if (grants.length > 0 && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
        openCamera(true);
      else
        message("Brak dostępu do aparatu. Możesz wczytać zdjęcie lub nadać dostęp w ustawieniach.");
    }
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
    b.putString("draft", input == null ? draft : input.getText().toString());
  }

  int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  android.graphics.drawable.GradientDrawable surface(int color, int radius) {
    var shape = new android.graphics.drawable.GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(dp(radius));
    return shape;
  }

  void layout() {
    screenGeneration++;
    input = null;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(22), dp(24), dp(22), dp(24));
    root.setBackground(
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[] {INK, Color.rgb(22, 38, 55), INK}));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              dp(22),
              insets.getSystemWindowInsetTop() + dp(20),
              dp(22),
              insets.getSystemWindowInsetBottom() + dp(20));
          return insets;
        });
    root.requestApplyInsets();
    if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
      root.setAlpha(0f);
      root.setTranslationY(dp(12));
      root.animate()
          .alpha(1f)
          .translationY(0)
          .setDuration(240)
          .setInterpolator(new android.view.animation.DecelerateInterpolator())
          .start();
    }
  }

  void scrollable() {
    android.view.ViewGroup parent = (android.view.ViewGroup) root.getParent();
    if (parent != null) parent.removeView(root);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(INK);
    scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
    setContentView(scroll);
    root.requestApplyInsets();
  }

  void label(String s) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(Color.WHITE);
    t.setTextSize(24);
    t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    t.setPadding(0, dp(14), 0, dp(12));
    root.addView(t);
  }

  void hint(String text) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextColor(MUTED);
    t.setTextSize(14);
    t.setLineSpacing(dp(3), 1);
    t.setPadding(0, dp(4), 0, dp(16));
    root.addView(t);
  }

  void button(String s, Runnable r) {
    action(s, r, false);
  }

  void action(String s, Runnable r, boolean primary) {
    Button b = new Button(this);
    b.setText(s);
    b.setMinHeight(dp(56));
    b.setPadding(dp(16), dp(12), dp(16), dp(12));
    b.setTextColor(primary ? INK : Color.WHITE);
    b.setTextSize(16);
    b.setAllCaps(false);
    b.setGravity(Gravity.CENTER);
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF),
            surface(primary ? GOLD : SURFACE, 18),
            null));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(10), 0, 0);
    root.addView(b, lp);
    b.setOnClickListener(
        v -> {
          if (busy || nativeSession.busy()) {
            message("Poczekaj na zakończenie operacji");
            return;
          }
          r.run();
        });
  }

  void home() {
    if (nativeSession.active()) {
      nativeSession.panel();
      return;
    }
    if (provider != null) provider.unbindAll();
    capture = null;
    importedPhoto = null;
    layout();
    ImageView logo = new ImageView(this);
    logo.setImageResource(pl.climagold.qc.R.drawable.climagold);
    logo.setContentDescription("Climagold");
    logo.setScaleType(ImageView.ScaleType.FIT_START);
    root.addView(logo, new LinearLayout.LayoutParams(-1, dp(42)));
    hint("KONTROLA JAKOŚCI  /  01 — WYBÓR NUMERU");
    label("Dobra seria zaczyna się\nod właściwego numeru.");
    hint(
        "Zeskanuj oznaczenie, wczytaj zdjęcie lub wpisz numer. Potem zrób całą serię aparatem"
            + " telefonu.");
    action(
        "Skanuj numer aparatem",
        () -> {
          saveDraft();
          openCamera(true);
        },
        true);
    button(
        "Wczytaj zdjęcie z numerem",
        () -> {
          saveDraft();
          photoPicker.launch("image/*");
        });
    label("Lub wpisz numer");
    input = new EditText(this);
    input.setTextColor(Color.WHITE);
    input.setHintTextColor(MUTED);
    input.setTextSize(18);
    input.setPadding(dp(16), dp(14), dp(16), dp(14));
    input.setBackground(surface(SURFACE, 16));
    input.setHint("np. C 14015, K 4567, IEW 123");
    input.setContentDescription("Numer centrali lub oznaczenie");
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    input.setText(draft);
    root.addView(input, new LinearLayout.LayoutParams(-1, -2));
    Switch mode = new Switch(this);
    mode.setText("Numery ogólne (także bez prefiksu)");
    mode.setTextColor(Color.WHITE);
    mode.setTextSize(14);
    mode.setPadding(0, dp(16), 0, dp(8));
    mode.setMinHeight(dp(56));
    mode.setChecked(generalNumbers);
    mode.setOnCheckedChangeListener(
        (v, checked) -> {
          generalNumbers = checked;
          getPreferences(0).edit().putBoolean("generalNumbers", checked).apply();
        });
    root.addView(mode);
    hint(
        "C — centrala, K — kompakt, IEW — inne zamówienie, RIEW — elementy automatyki. Prefiks i"
            + " numer obok tworzą oznaczenie. Czytelne ręczne znaki też możesz spróbować odczytać"
            + " offline.");
    action(
        "Dalej · sprawdź numer",
        () -> {
          saveDraft();
          choose(draft);
        },
        true);
    if (!serial.isEmpty()) {
      label("Ostatnia centrala · " + serial);
      button("Otwórz galerię", () -> gallery(0));
      button("Nowa seria dla " + serial, () -> confirm(serial));
    }
    hint("Zdjęcia w pełnej jakości • domyślnie OCR offline");
    scrollable();
  }

  void saveDraft() {
    if (input != null) draft = input.getText().toString();
  }

  void choose(String text) {
    List<String> options = generalNumbers ? Numbers.parseGeneral(text) : Numbers.parse(text);
    if (options.isEmpty()) {
      new android.app.AlertDialog.Builder(this)
          .setTitle("Nie rozpoznano numeru")
          .setMessage(
              "Popraw odczyt lub włącz na ekranie głównym tryb numerów ogólnych.\n\n" + text)
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
              draft = value;
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
      requestPermissions(new String[] {Manifest.permission.CAMERA}, 1);
      return;
    }
    layout();
    label("Zeskanuj oznaczenie");
    hint("Wypełnij kadr numerem. Stuknij, aby ustawić ostrość; rozsuń palce, aby przybliżyć.");
    final int generation = screenGeneration;
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
    action("Odczytaj numer", this::shoot, true);
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
            if (generation != screenGeneration || isDestroyed()) return;
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
                status.setText("Nie pobrano obrazu. Spróbuj ponownie.");
                return;
              }
              try {
                android.graphics.Bitmap bitmap = image.toBitmap();
                int rotation = image.getImageInfo().getRotationDegrees();
                image.close();
                readOffline(bitmap, rotation, true);
              } catch (Exception | OutOfMemoryError e) {
                image.close();
                busy = false;
                status.setText("Nie przygotowano obrazu do OCR. Spróbuj ponownie.");
              }
            }

            public void onError(ImageCaptureException e) {
              busy = false;
              status.setText("Nie wykonano skanu. Spróbuj ponownie.");
              message(e.toString());
            }
          });
      return;
    }
  }

  void reviewText(String text) {
    reviewText(text, "");
  }

  void reviewText(String text, String contrastText) {
    EditText correction = new EditText(this);
    correction.setText(text);
    correction.setHint("Wpisz lub popraw odczytany numer");
    correction.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    correction.setMinLines(2);
    correction.setMaxLines(6);
    correction.setPadding(dp(20), dp(16), dp(20), dp(16));
    LinearLayout review = new LinearLayout(this);
    review.setOrientation(LinearLayout.VERTICAL);
    review.setPadding(dp(20), 0, dp(20), dp(12));
    review.addView(correction);
    TextView types = new TextView(this);
    types.setText("C — centrala · K — kompakt\nIEW — inne zamówienie\nRIEW — elementy automatyki");
    types.setTextColor(MUTED);
    types.setPadding(0, dp(8), 0, dp(8));
    review.addView(types);
    Button suggestions = new Button(this);
    suggestions.setText("Podpowiedz oznaczenie C / K / IEW / RIEW");
    suggestions.setAllCaps(false);
    suggestions.setOnClickListener(
        v -> {
          LinkedHashSet<String> candidates =
              new LinkedHashSet<>(Numbers.ocrSuggestions(correction.getText().toString()));
          candidates.addAll(Numbers.ocrSuggestions(contrastText));
          if (candidates.isEmpty()) {
            message(
                "Nie znaleziono prefiksu z numerem obok. Popraw tekst lub zrób wyraźniejsze"
                    + " zdjęcie.");
            return;
          }
          String[] values = candidates.toArray(new String[0]);
          new android.app.AlertDialog.Builder(this)
              .setTitle("Sugestie — porównaj ze zdjęciem")
              .setItems(
                  values,
                  (d, which) -> {
                    correction.setText(values[which]);
                    correction.setSelection(correction.length());
                  })
              .setNegativeButton("Wróć", null)
              .show();
        });
    review.addView(suggestions);
    Switch mode = new Switch(this);
    mode.setText("Numery ogólne, bez prefiksu");
    mode.setMinHeight(dp(56));
    mode.setChecked(generalNumbers);
    mode.setOnCheckedChangeListener(
        (v, checked) -> {
          generalNumbers = checked;
          getPreferences(0).edit().putBoolean("generalNumbers", checked).apply();
        });
    review.addView(mode);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(review);
    new android.app.AlertDialog.Builder(this)
        .setTitle("Sprawdź odczyt")
        .setMessage(
            "Sprawdź prefiks i numer obok niego. Podpowiedzi mogą zamieniać O/0, I/1, S/5, B/8 i"
                + " Z/2. Nie są pewnym odczytem — porównaj każdy znak z oryginałem.")
        .setView(scroll)
        .setPositiveButton(
            "Wybierz numer",
            (d, w) -> {
              draft = correction.getText().toString();
              choose(draft);
            })
        .setNegativeButton("Wróć", null)
        .show();
  }

  void importPhoto(Uri uri) {
    if (provider != null) provider.unbindAll();
    capture = null;
    layout();
    label("Numer ze zdjęcia");
    hint("01 — WCZYTAJ  /  02 — ODCZYTAJ  /  03 — SPRAWDŹ");
    ImageView preview = new ImageView(this);
    preview.setContentDescription("Wybrane zdjęcie do rozpoznania numeru");
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    status = new TextView(this);
    status.setTextColor(MUTED);
    status.setText("Wczytywanie zdjęcia…");
    root.addView(status);
    action("Odczytaj numer ze zdjęcia", this::recognizeImported, true);
    button("Pismo ręczne · OCR w chmurze", this::handwritingDialog);
    button("Wybierz inne zdjęcie", () -> photoPicker.launch("image/*"));
    button("Wróć", this::home);
    importedPhoto = null;
    busy = true;
    new Thread(
            () -> {
              try {
                android.graphics.Bitmap bitmap =
                    android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> {
                          decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                          int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                          if (max > 2400) {
                            double scale = 2400.0 / max;
                            decoder.setTargetSize(
                                Math.max(1, (int) (info.getSize().getWidth() * scale)),
                                Math.max(1, (int) (info.getSize().getHeight() * scale)));
                          }
                        });
                runOnUiThread(
                    () -> {
                      busy = false;
                      if (isDestroyed()) {
                        bitmap.recycle();
                        return;
                      }
                      importedPhoto = bitmap;
                      preview.setImageBitmap(bitmap);
                      status.setText("Zdjęcie gotowe. Odczytaj numer, a następnie sprawdź wynik.");
                    });
              } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(
                    () -> {
                      busy = false;
                      status.setText(
                          "Nie udało się wczytać zdjęcia. Wybierz plik JPG, PNG lub HEIC.");
                    });
              }
            },
            "photo-import")
        .start();
  }

  void handwritingDialog() {
    if (importedPhoto == null) {
      message("Najpierw wczytaj zdjęcie");
      return;
    }
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(dp(24), dp(8), dp(24), 0);
    EditText endpoint = new EditText(this);
    endpoint.setHint("Adres usługi Azure AI Vision (HTTPS)");
    endpoint.setContentDescription("Adres usługi Azure AI Vision");
    endpoint.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    endpoint.setText(getPreferences(0).getString("ocrEndpoint", ""));
    fields.addView(endpoint);
    EditText key = new EditText(this);
    key.setHint("Klucz usługi (nie jest zapisywany)");
    key.setContentDescription("Klucz usługi Azure AI Vision");
    key.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    fields.addView(key);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(fields);
    var dialog =
        new android.app.AlertDialog.Builder(this)
            .setTitle("Odczyt pisma ręcznego")
            .setMessage(
                "Opcjonalna usługa Azure AI Vision Read rozpoznaje także pismo ręczne. Wymaga"
                    + " własnego zasobu Azure i może być płatna. Wyślesz całe wybrane zdjęcie do"
                    + " tej usługi. Wynik zawsze sprawdź przed zatwierdzeniem.")
            .setView(scroll)
            .setPositiveButton("Wyślij i odczytaj", null)
            .setNegativeButton("Anuluj", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      java.net.URI address;
                      try {
                        address = HandwritingOcr.endpoint(endpoint.getText().toString());
                      } catch (Exception e) {
                        endpoint.setError("Wpisz https://NAZWA.cognitiveservices.azure.com/");
                        return;
                      }
                      String secret = key.getText().toString().trim();
                      if (secret.isEmpty() || secret.contains("\n") || secret.contains("\r")) {
                        key.setError("Wpisz poprawny klucz usługi");
                        return;
                      }
                      getPreferences(0).edit().putString("ocrEndpoint", address.toString()).apply();
                      key.setText("");
                      dialog.dismiss();
                      busy = true;
                      status.setText("Odczyt pisma ręcznego w chmurze…");
                      android.graphics.Bitmap photo = importedPhoto;
                      new Thread(
                              () -> {
                                try {
                                  String text = HandwritingOcr.read(photo, address, secret);
                                  runOnUiThread(
                                      () -> {
                                        busy = false;
                                        if (isDestroyed()) return;
                                        status.setText(
                                            "Odczyt zakończony. Sprawdź znaki i wybierz numer.");
                                        reviewText(text);
                                      });
                                } catch (Exception e) {
                                  runOnUiThread(
                                      () -> {
                                        busy = false;
                                        if (!isDestroyed())
                                          status.setText(
                                              "Nie udało się odczytać: " + e.getMessage());
                                      });
                                }
                              },
                              "handwriting-ocr")
                          .start();
                    }));
    dialog.show();
  }

  void recognizeImported() {
    if (importedPhoto == null || busy) return;
    busy = true;
    status.setText("Rozpoznawanie numeru na urządzeniu…");
    readOffline(importedPhoto, 0, false);
  }

  void readOffline(android.graphics.Bitmap bitmap, int rotation, boolean release) {
    status.setText("Odczyt offline: oryginał i poprawiony kontrast…");
    OfflineOcr.read(
        bitmap,
        rotation,
        new OfflineOcr.Callback() {
          public void success(String original, String contrast) {
            if (release) bitmap.recycle();
            busy = false;
            if (isDestroyed()) return;
            status.setText("Sprawdź prefiks i identyfikator. Podpowiedzi wymagają potwierdzenia.");
            reviewText(original.isEmpty() ? contrast : original, contrast);
          }

          public void failure() {
            if (release) bitmap.recycle();
            busy = false;
            if (!isDestroyed())
              status.setText("Nie odczytano zdjęcia. Spróbuj ponownie lub wpisz oznaczenie.");
          }
        });
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
