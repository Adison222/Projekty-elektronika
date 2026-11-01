package pl.climagold.qc;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.tensorflow.lite.Interpreter;

/** Bundled model, local inference only. Must run on a worker thread. */
final class HandwrittenRecognizer {
  static List<IdentifierDecoder.Candidate> recognize(Context context, Bitmap cropped)
      throws IOException {
    float scale = Math.min(1f, 1400f / Math.max(cropped.getWidth(), cropped.getHeight()));
    Bitmap small =
        Bitmap.createScaledBitmap(
            cropped,
            Math.max(1, Math.round(cropped.getWidth() * scale)),
            Math.max(1, Math.round(cropped.getHeight() * scale)),
            true);
    int[] pixels = new int[small.getWidth() * small.getHeight()];
    small.getPixels(pixels, 0, small.getWidth(), 0, 0, small.getWidth(), small.getHeight());
    List<float[]> glyphs;
    try {
      glyphs = GlyphSegmenter.segment(pixels, small.getWidth(), small.getHeight());
    } finally {
      if (small != cropped) small.recycle();
    }
    byte[] bytes;
    try (InputStream input = context.getAssets().open("handwritten_glyphs.tflite");
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
      bytes = out.toByteArray();
    }
    ByteBuffer model = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
    model.put(bytes).rewind();
    float[][] scores = new float[glyphs.size()][IdentifierDecoder.LABELS.length()];
    try (Interpreter interpreter =
        new Interpreter(model, new Interpreter.Options().setNumThreads(2))) {
      ByteBuffer input = ByteBuffer.allocateDirect(784 * 4).order(ByteOrder.nativeOrder());
      for (int i = 0; i < glyphs.size(); i++) {
        input.rewind();
        for (float v : glyphs.get(i)) input.putFloat(v);
        input.rewind();
        float[][] output = new float[1][IdentifierDecoder.LABELS.length()];
        interpreter.run(input, output);
        scores[i] = output[0];
      }
    }
    return IdentifierDecoder.decode(scores);
  }
}
