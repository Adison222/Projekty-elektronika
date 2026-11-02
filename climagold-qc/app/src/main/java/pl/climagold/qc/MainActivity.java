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
  BroadcastReceiver unlockReceiver;
  Runnable backAction;
  ScanAttachment scanAttachment;
  boolean storageSettingsVisible;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getPreferences(0).edit().remove("ocrEndpoint").apply();
    nativeSession = new NativeSession(this);
    scanAttachment = new ScanAttachment(this);
    unlockReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            nativeSession.unlocked();
          }
        };
    ContextCompat.registerReceiver(
        this,
        unlockReceiver,
        new IntentFilter(Intent.ACTION_USER_PRESENT),
        ContextCompat.RECEIVER_NOT_EXPORTED);
    generalNumbers = getPreferences(0).getBoolean("generalNumbers", false);
    photoPicker =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
              if (uri != null) importPhoto(uri);
            });
    getWindow().setStatusBarColor(Color.rgb(17, 25, 32));
    getWindow().setNavigationBarColor(Color.rgb(17, 25, 32));
    serial = Numbers.normalizeSerial(getPreferences(0).getString("lastSerial", ""));
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
      serial = Numbers.normalizeSerial(b.getString("serial", ""));
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
                if (backAction != null) backAction.run();
                else home();
              }
            });
    draft = b == null ? serial : b.getString("draft", serial);
    home();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (nativeSession != null) nativeSession.resumed();
    if (storageSettingsVisible && !nativeSession.active()) storageSettings();
  }

  @Override
  protected void onPause() {
    if (nativeSession != null) nativeSession.paused();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (unlockReceiver != null) unregisterReceiver(unlockReceiver);
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    nativeSession.permissionResult(request);
    if (request == 44) {
      if (hasPhotoRead()
          && checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
              == android.content.pm.PackageManager.PERMISSION_GRANTED)
        openMediaManagementSettings();
      else {
        storageSettings();
        message("Pozostaje standardowe potwierdzanie zapisu serii.");
      }
      return;
    }
    if (request == 43) {
      if (grants.length > 0 && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
        browseUnits();
      else {
        message("Do listy central potrzebny jest dostęp do zdjęć.");
        home();
      }
    }
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
    storageSettingsVisible = false;
    backAction = this::home;
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
    backAction = this::finish;
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.climagold);
    logo.setContentDescription("Climagold");
    logo.setScaleType(ImageView.ScaleType.FIT_START);
    root.addView(logo, new LinearLayout.LayoutParams(-1, dp(38)));
    hint("DOKUMENTACJA ZDJĘCIOWA");
    label("Co fotografujemy?");
    hint("Wybierz centralę. Rób zdjęcia aparatem telefonu. Wróć tutaj, żeby zapisać serię.");
    action(
        "+  Nowa seria zdjęć",
        () -> {
          scanAttachment.discard();
          numberEntry();
        },
        true);
    if (scanAttachment.ready()) button("Dokończ numer ze zdjęcia", this::numberEntry);
    button("Centrale i zdjęcia", this::browseUnits);
    nativeSession.lastSessionCard();
    button("Ustawienia zapisu", this::storageSettings);
    hint("Zdjęcia i rozpoznawanie pozostają na telefonie.");
    scrollable();
  }

  boolean hasPhotoRead() {
    return checkSelfPermission(
            android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE)
        == android.content.pm.PackageManager.PERMISSION_GRANTED;
  }

  boolean fastMediaAccess() {
    return android.os.Build.VERSION.SDK_INT >= 31
        && hasPhotoRead()
        && MediaStore.canManageMedia(this)
        && checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
  }

  void storageSettings() {
    layout();
    storageSettingsVisible = true;
    label("Zapis zdjęć");
    if (android.os.Build.VERSION.SDK_INT < 31) {
      hint(
          "Na tym Androidzie pozostaje standardowy zapis. Zapis bez każdorazowego pytania wymaga"
              + " Androida 12 lub nowszego.");
    } else {
      hint(
          fastMediaAccess()
              ? "Szybki zapis włączony. Android może zatwierdzać przypisanie serii bez dodatkowych"
                  + " pytań."
              : "Zachowaj aparat Samsunga i włącz jednorazowo zarządzanie multimediami, aby Android"
                  + " nie pytał o każdą serię.");
      hint(
          "Potrzebny jest dostęp do wszystkich zdjęć oraz metadanych lokalizacji zdjęć. Android"
              + " wymaga obu do zapisu bez okna zgody. Aplikacja nie pobiera pozycji telefonu ani"
              + " nie wysyła zdjęć.");
      action(
          fastMediaAccess() ? "Otwórz uprawnienie systemowe" : "Włącz szybki zapis",
          () -> {
            if (!hasPhotoRead()
                || checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
              requestPermissions(
                  new String[] {
                    android.os.Build.VERSION.SDK_INT >= 33
                        ? Manifest.permission.READ_MEDIA_IMAGES
                        : Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_MEDIA_LOCATION
                  },
                  44);
            } else openMediaManagementSettings();
          },
          true);
    }
    hint(
        "Zdjęcia aparatu Samsunga są przypisywane po zakończeniu serii. Zdjęcie odczytanego numeru"
            + " zapisujemy już po zatwierdzeniu centrali.");
    button("Wróć", this::home);
    scrollable();
  }

  void openMediaManagementSettings() {
    if (android.os.Build.VERSION.SDK_INT < 31) return;
    try {
      startActivity(
          new Intent(
              android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA,
              Uri.parse("package:" + getPackageName())));
    } catch (ActivityNotFoundException | SecurityException e) {
      message("Telefon nie udostępnia tego ustawienia. Pozostaje standardowa zgoda na zapis.");
    }
  }

  void numberEntry() {
    layout();
    hint("1  NUMER   ·   2  ZDJĘCIA   ·   3  ZAPIS");
    label("Wybierz numer");
    hint("Zeskanuj oznaczenie lub wpisz je poniżej.");
    action(
        "Skanuj numer aparatem",
        () -> {
          saveDraft();
          openCamera(true);
        },
        true);
    button(
        "Wybierz zdjęcie z galerii",
        () -> {
          saveDraft();
          photoPicker.launch("image/*");
        });
    label("Wpisz oznaczenie");
    input = new EditText(this);
    input.setTextColor(Color.WHITE);
    input.setHintTextColor(MUTED);
    input.setTextSize(20);
    input.setPadding(dp(16), dp(14), dp(16), dp(14));
    input.setBackground(surface(SURFACE, 16));
    input.setHint("np. C13932, KS303, R14577");
    input.setContentDescription("Oznaczenie centrali");
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    input.setSingleLine(true);
    input.setText(draft);
    root.addView(input, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout proposals = new LinearLayout(this);
    proposals.setOrientation(LinearLayout.VERTICAL);
    root.addView(proposals);
    Runnable refresh = () -> renderSuggestions(proposals, input, Collections.emptyList(), false);
    input.addTextChangedListener(watcher(refresh));
    refresh.run();
    action(
        "Dalej · rozpocznij serię",
        () -> {
          saveDraft();
          choose(draft);
        },
        true);
    button(
        "Inny format numeru",
        () -> {
          new android.app.AlertDialog.Builder(this)
              .setTitle("Format oznaczenia")
              .setSingleChoiceItems(
                  new String[] {
                    "Prefiks + numer: C, K, IEW, RIEW, R, Z", "Dowolne oznaczenie z cyframi"
                  },
                  generalNumbers ? 1 : 0,
                  (d, w) -> {
                    generalNumbers = w == 1;
                    getPreferences(0).edit().putBoolean("generalNumbers", generalNumbers).apply();
                    d.dismiss();
                  })
              .setNegativeButton("Wróć", null)
              .show();
        });
    button("Wróć", this::home);
    scrollable();
  }

  android.text.TextWatcher watcher(Runnable changed) {
    return new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      public void onTextChanged(CharSequence s, int start, int before, int count) {
        changed.run();
      }

      public void afterTextChanged(android.text.Editable s) {}
    };
  }

  void renderSuggestions(LinearLayout list, EditText field, List<String> extra, boolean ocr) {
    list.removeAllViews();
    LinkedHashSet<String> values =
        new LinkedHashSet<>(
            ocr
                ? Numbers.ocrSuggestions(field.getText().toString())
                : (generalNumbers
                    ? Numbers.parseGeneral(field.getText().toString())
                    : Numbers.parse(field.getText().toString())));
    values.addAll(extra);
    if (values.isEmpty()) return;
    TextView title = new TextView(this);
    title.setText("Propozycje — sprawdź z oznaczeniem");
    title.setTextColor(GOLD);
    title.setTextSize(14);
    title.setPadding(0, dp(14), 0, dp(4));
    list.addView(title);
    int count = 0;
    for (String value : values) {
      if (count++ >= 12) break;
      Button choice = new Button(this);
      choice.setText(value);
      choice.setAllCaps(false);
      choice.setTextSize(19);
      choice.setTextColor(Color.WHITE);
      choice.setMinHeight(dp(48));
      choice.setBackground(
          new android.graphics.drawable.RippleDrawable(
              android.content.res.ColorStateList.valueOf(0x33FFFFFF), surface(SURFACE, 12), null));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = dp(6);
      list.addView(choice, lp);
      choice.setOnClickListener(
          v -> {
            field.setText(value);
            field.setSelection(field.length());
          });
    }
    if (values.size() > 12) {
      TextView more = new TextView(this);
      more.setText("Więcej numerów: wybierz Dalej.");
      more.setTextColor(MUTED);
      list.addView(more);
    }
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
    if (options.size() == 1) {
      confirm(options.get(0));
      return;
    }
    new android.app.AlertDialog.Builder(this)
        .setTitle("Wybierz numer")
        .setItems(options.toArray(new String[0]), (d, w) -> confirm(options.get(w)))
        .show();
  }

  void confirm(String rawValue) {
    String value = Numbers.normalizeSerial(rawValue);
    if (!nativeSession.readBeforeConfirm(value)) return;
    String path = "DCIM/" + Numbers.folder(value) + "/";
    List<String> paths = Numbers.folderPaths(value);
    boolean exists = false;
    try (android.database.Cursor c =
        getContentResolver()
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {"_id"},
                "relative_path IN ("
                    + String.join(",", Collections.nCopies(paths.size(), "?"))
                    + ")",
                paths.toArray(new String[0]),
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
                    ? "Ten numer ma już zdjęcia. Nowe zapiszę w folderze poniżej. Galeria obejmuje"
                        + " też wcześniejsze foldery tego oznaczenia.\n"
                    : "Nowa seria zdjęć.\n")
                + path
                + (scanAttachment.ready()
                    ? "\nZdjęcie odczytanego numeru też trafi do tego folderu."
                    : ""))
        .setPositiveButton(
            "Rób zdjęcia",
            (d, w) -> {
              serial = value;
              draft = value;
              getPreferences(0).edit().putString("lastSerial", value).apply();
              count = 0;
              saveScanAndStart();
            })
        .setNegativeButton("Popraw", (d, w) -> numberEntry())
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
    label("Numer centrali");
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
    button("Wpisz numer ręcznie", this::numberEntry);
    button(
        "Anuluj odczyt",
        () -> {
          scanAttachment.discard();
          home();
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

  void saveScanAndStart() {
    if (!scanAttachment.ready()) {
      openCamera(false);
      return;
    }
    busy = true;
    layout();
    label("Zapisuję zdjęcie numeru…");
    final String target = serial;
    new Thread(
            () -> {
              try {
                scanAttachment.save(target);
                runOnUiThread(
                    () -> {
                      busy = false;
                      if (!isDestroyed()) openCamera(false);
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      busy = false;
                      if (isDestroyed()) return;
                      numberEntry();
                      message(e.getMessage() + ". Zdjęcie czeka na ponowienie.");
                    });
              }
            },
            "save-number-photo")
        .start();
  }

  void shoot() {
    if (capture == null || busy) return;
    busy = true;
    status.setText("Zapis zdjęcia i odczyt numeru…");
    capture.setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());
    final java.io.File file;
    try {
      file = java.io.File.createTempFile("number-", ".jpg", getCacheDir());
    } catch (java.io.IOException e) {
      busy = false;
      message("Nie przygotowano zdjęcia");
      return;
    }
    capture.takePicture(
        new ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(this),
        new ImageCapture.OnImageSavedCallback() {
          public void onImageSaved(ImageCapture.OutputFileResults result) {
            new Thread(
                    () -> {
                      try {
                        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                          scanAttachment.stage(in, "image/jpeg", true);
                        }
                        android.graphics.Bitmap bitmap =
                            decodeForOcr(android.graphics.ImageDecoder.createSource(file));
                        runOnUiThread(
                            () -> {
                              if (isDestroyed()) {
                                bitmap.recycle();
                                busy = false;
                                return;
                              }
                              readOffline(bitmap, 0, true);
                            });
                      } catch (Exception | OutOfMemoryError e) {
                        runOnUiThread(
                            () -> {
                              busy = false;
                              if (!isDestroyed())
                                status.setText(
                                    "Nie odczytano zdjęcia. Spróbuj ponownie lub wpisz numer.");
                            });
                      } finally {
                        file.delete();
                      }
                    },
                    "number-capture")
                .start();
          }

          public void onError(ImageCaptureException e) {
            file.delete();
            busy = false;
            if (!isDestroyed()) status.setText("Nie wykonano zdjęcia numeru. Spróbuj ponownie.");
          }
        });
  }

  static android.graphics.Bitmap decodeForOcr(android.graphics.ImageDecoder.Source source)
      throws java.io.IOException {
    return android.graphics.ImageDecoder.decodeBitmap(
        source,
        (decoder, info, src) -> {
          decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
          int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
          if (max > 2400) {
            double scale = 2400.0 / max;
            decoder.setTargetSize(
                Math.max(1, (int) (info.getSize().getWidth() * scale)),
                Math.max(1, (int) (info.getSize().getHeight() * scale)));
          }
        });
  }

  void reviewText(String text) {
    reviewText(text, "");
  }

  void reviewText(String text, String contrastText) {
    reviewText(text, Numbers.ocrSuggestions(contrastText));
  }

  void reviewText(String text, List<String> candidates) {
    EditText correction = new EditText(this);
    correction.setText(text);
    correction.setHint("Wpisz lub popraw oznaczenie");
    correction.setTextSize(20);
    correction.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    correction.setMinLines(1);
    correction.setMaxLines(4);
    LinearLayout review = new LinearLayout(this);
    review.setOrientation(LinearLayout.VERTICAL);
    review.setPadding(dp(20), dp(8), dp(20), dp(12));
    LinearLayout proposals = new LinearLayout(this);
    proposals.setOrientation(LinearLayout.VERTICAL);
    review.addView(proposals);
    TextView label = new TextView(this);
    label.setText("Numer do zatwierdzenia");
    label.setTextColor(MUTED);
    label.setPadding(0, dp(16), 0, 0);
    review.addView(label);
    review.addView(correction);
    Runnable refresh = () -> renderSuggestions(proposals, correction, candidates, true);
    correction.addTextChangedListener(watcher(refresh));
    refresh.run();
    ScrollView scroll = new ScrollView(this);
    scroll.addView(review);
    new android.app.AlertDialog.Builder(this)
        .setTitle("Sprawdź numer")
        .setMessage(
            "Wybierz propozycję lub popraw znaki. To podpowiedzi — sprawdź prefiks i każdą cyfrę.")
        .setView(scroll)
        .setPositiveButton(
            "Dalej",
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
    button("Wybierz inne zdjęcie", () -> photoPicker.launch("image/*"));
    button("Wróć", this::home);
    importedPhoto = null;
    busy = true;
    new Thread(
            () -> {
              try {
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                  scanAttachment.stage(in, getContentResolver().getType(uri), false);
                }
                android.graphics.Bitmap bitmap =
                    decodeForOcr(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), uri));
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

  void recognizeImported() {
    if (importedPhoto == null || busy) return;
    try {
      scanAttachment.arm();
    } catch (java.io.IOException e) {
      message(e.getMessage());
      return;
    }
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

  void browseUnits() {
    String permission =
        android.os.Build.VERSION.SDK_INT >= 33
            ? Manifest.permission.READ_MEDIA_IMAGES
            : Manifest.permission.READ_EXTERNAL_STORAGE;
    if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {permission}, 43);
      return;
    }
    if (provider != null) provider.unbindAll();
    capture = null;
    layout();
    label("Centrale");
    hint("Wybierz centralę, aby otworzyć jej folder w menedżerze plików telefonu.");
    EditText search = new EditText(this);
    search.setHint("Szukaj numeru lub folderu");
    search.setTextColor(Color.WHITE);
    search.setHintTextColor(MUTED);
    search.setSingleLine(true);
    search.setPadding(dp(16), dp(12), dp(16), dp(12));
    search.setBackground(surface(SURFACE, 14));
    root.addView(search, new LinearLayout.LayoutParams(-1, -2));
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView loading = new TextView(this);
    loading.setText("Wczytywanie folderów…");
    loading.setTextColor(MUTED);
    loading.setPadding(0, dp(20), 0, 0);
    list.addView(loading);
    button(nativeSession.active() ? "Wróć do serii" : "Wróć", this::home);
    final int generation = screenGeneration;
    new Thread(
            () -> {
              try {
                List<String> paths = new ArrayList<>();
                List<Long> dates = new ArrayList<>();
                try (android.database.Cursor c =
                    getContentResolver()
                        .query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[] {
                              MediaStore.Images.Media.RELATIVE_PATH,
                              MediaStore.Images.Media.DATE_ADDED
                            },
                            "is_pending=0"
                                + (android.os.Build.VERSION.SDK_INT >= 30
                                    ? " AND is_trashed=0"
                                    : ""),
                            null,
                            null)) {
                  if (c == null) throw new java.io.IOException("Nie odczytano folderów");
                  while (c.moveToNext()) {
                    paths.add(c.getString(0));
                    dates.add(c.getLong(1));
                  }
                }
                List<UnitFolders.Entry> entries = UnitFolders.group(paths, dates);
                runOnUiThread(
                    () -> {
                      if (isDestroyed() || generation != screenGeneration) return;
                      Runnable render =
                          () -> {
                            list.removeAllViews();
                            String query =
                                search.getText().toString().trim().toLowerCase(Locale.ROOT);
                            int count = 0;
                            for (UnitFolders.Entry entry : entries) {
                              if (!entry.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                              count++;
                              Button row = new Button(this);
                              row.setAllCaps(false);
                              row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                              row.setText(
                                  entry.name + "   ›\n" + entry.count + " zdjęć  ·  " + entry.path);
                              row.setTextColor(Color.WHITE);
                              row.setTextSize(17);
                              row.setPadding(dp(18), dp(16), dp(18), dp(16));
                              row.setBackground(
                                  new android.graphics.drawable.RippleDrawable(
                                      android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                                      surface(SURFACE, 18),
                                      null));
                              LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                              lp.topMargin = dp(12);
                              list.addView(row, lp);
                              row.setOnClickListener(v -> openUnitFolder(entry.path));
                            }
                            if (count == 0) {
                              TextView empty = new TextView(this);
                              empty.setText(
                                  entries.isEmpty()
                                      ? "Nie ma jeszcze folderów central ze zdjęciami. Rozpocznij"
                                          + " pierwszą serię."
                                      : "Brak folderu o takim numerze.");
                              empty.setTextColor(MUTED);
                              empty.setPadding(0, dp(20), 0, 0);
                              list.addView(empty);
                            }
                          };
                      search.addTextChangedListener(watcher(render));
                      render.run();
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      if (generation == screenGeneration)
                        loading.setText("Nie udało się wczytać folderów. Wróć i spróbuj ponownie.");
                    });
              }
            },
            "unit-folders")
        .start();
  }

  void openUnitFolder(String path) {
    final int generation = screenGeneration;
    if (busy) return;
    busy = true;
    new Thread(
            () -> {
              try {
                LinkedHashSet<String> volumes = new LinkedHashSet<>();
                try (android.database.Cursor c =
                    getContentResolver()
                        .query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[] {MediaStore.Images.Media.VOLUME_NAME},
                            "relative_path=? AND is_pending=0"
                                + (android.os.Build.VERSION.SDK_INT >= 30
                                    ? " AND is_trashed=0"
                                    : ""),
                            new String[] {path},
                            null)) {
                  if (c == null) throw new java.io.IOException("Nie odczytano lokalizacji folderu");
                  while (c.moveToNext()) if (!c.isNull(0)) volumes.add(c.getString(0));
                }
                runOnUiThread(
                    () -> {
                      busy = false;
                      if (isDestroyed() || generation != screenGeneration) return;
                      if (volumes.isEmpty()) {
                        message("W tym folderze nie ma jeszcze zapisanych zdjęć.");
                        return;
                      }
                      List<String> choices = new ArrayList<>(volumes);
                      if (choices.size() == 1) launchFolder(path, choices.get(0));
                      else
                        new android.app.AlertDialog.Builder(this)
                            .setTitle("Wybierz nośnik folderu")
                            .setItems(
                                choices.stream()
                                    .map(
                                        v ->
                                            MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(v)
                                                ? "Pamięć telefonu"
                                                : "Nośnik " + v)
                                    .toArray(String[]::new),
                                (d, which) -> launchFolder(path, choices.get(which)))
                            .setNegativeButton("Wróć", null)
                            .show();
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      busy = false;
                      if (!isDestroyed() && generation == screenGeneration)
                        message("Nie otwarto folderu: " + e.getMessage());
                    });
              }
            },
            "open-unit-folder")
        .start();
  }

  void launchFolder(String path, String volume) {
    String reason =
        "Telefon nie udostępnia aplikacji obsługującej otwieranie folderów. Włącz menedżer plików i"
            + " spróbuj ponownie.";
    try {
      Intent intent = FolderViewer.directory(path, FolderViewer.documentVolume(this, volume));
      if (FolderViewer.open(this, intent)) return;
    } catch (IllegalArgumentException e) {
      reason = e.getMessage();
    }
    new android.app.AlertDialog.Builder(this)
        .setTitle("Nie otwarto folderu")
        .setMessage(reason + "\n\n" + path)
        .setPositiveButton("OK", null)
        .setNeutralButton(
            "Kopiuj ścieżkę",
            (d, w) -> {
              getSystemService(android.content.ClipboardManager.class)
                  .setPrimaryClip(ClipData.newPlainText("Folder centrali", path));
            })
        .show();
  }

  void message(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}
