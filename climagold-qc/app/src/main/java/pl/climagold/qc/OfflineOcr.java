package pl.climagold.qc;

import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/** Two local OCR passes. This improves input contrast; it is not a handwriting-trained model. */
final class OfflineOcr {
  interface Callback {
    void success(String original, String contrast);

    void failure();
  }

  static void read(Bitmap source, int rotation, Callback callback) {
    Handler main = new Handler(Looper.getMainLooper());
    new Thread(
            () -> {
              Bitmap scaled = null;
              Bitmap contrast = null;
              try {
                int max = Math.max(source.getWidth(), source.getHeight());
                float scale = Math.min(1f, 2400f / max);
                scaled =
                    Bitmap.createScaledBitmap(
                        source,
                        Math.max(1, Math.round(source.getWidth() * scale)),
                        Math.max(1, Math.round(source.getHeight() * scale)),
                        true);
                contrast =
                    Bitmap.createBitmap(
                        scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);
                ColorMatrix gray = new ColorMatrix();
                gray.setSaturation(0);
                float gain = 1.6f, offset = 128 * (1 - gain);
                ColorMatrix boost =
                    new ColorMatrix(
                        new float[] {
                          gain, 0, 0, 0, offset, 0, gain, 0, 0, offset, 0, 0, gain, 0, offset, 0, 0,
                          0, 1, 0
                        });
                gray.postConcat(boost);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                paint.setColorFilter(new ColorMatrixColorFilter(gray));
                Canvas canvas = new Canvas(contrast);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(scaled, 0, 0, paint);
                Bitmap first = scaled, second = contrast;
                main.post(() -> recognize(source, first, second, rotation, callback));
              } catch (Exception | OutOfMemoryError e) {
                if (scaled != null && scaled != source) scaled.recycle();
                if (contrast != null) contrast.recycle();
                main.post(callback::failure);
              }
            },
            "offline-ocr-preparation")
        .start();
  }

  private static void recognize(
      Bitmap source, Bitmap first, Bitmap second, int rotation, Callback callback) {
    TextRecognizer recognizer;
    try {
      recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    } catch (Exception e) {
      finish(source, first, second, null, callback, false, "", "");
      return;
    }
    try {
      recognizer
          .process(InputImage.fromBitmap(first, rotation))
          .addOnCompleteListener(
              original -> {
                String text = original.isSuccessful() ? original.getResult().getText() : "";
                try {
                  recognizer
                      .process(InputImage.fromBitmap(second, rotation))
                      .addOnCompleteListener(
                          enhanced -> {
                            finish(
                                source,
                                first,
                                second,
                                recognizer,
                                callback,
                                original.isSuccessful() || enhanced.isSuccessful(),
                                text,
                                enhanced.isSuccessful() ? enhanced.getResult().getText() : "");
                          });
                } catch (Exception e) {
                  finish(
                      source,
                      first,
                      second,
                      recognizer,
                      callback,
                      original.isSuccessful(),
                      text,
                      "");
                }
              });
    } catch (Exception e) {
      finish(source, first, second, recognizer, callback, false, "", "");
    }
  }

  private static void finish(
      Bitmap source,
      Bitmap first,
      Bitmap second,
      TextRecognizer recognizer,
      Callback callback,
      boolean success,
      String original,
      String contrast) {
    if (recognizer != null) recognizer.close();
    if (first != source) first.recycle();
    second.recycle();
    if (success) callback.success(original, contrast);
    else callback.failure();
  }
}
