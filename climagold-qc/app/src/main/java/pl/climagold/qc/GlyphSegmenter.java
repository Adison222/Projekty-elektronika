package pl.climagold.qc;

import java.util.*;

/** Segments a tightly cropped single line of separate dark handwritten glyphs. */
public final class GlyphSegmenter {
  public static List<float[]> segment(int[] pixels, int width, int height) {
    if (width < 8 || height < 8 || pixels.length != width * height || pixels.length > 2_000_000)
      throw new IllegalArgumentException("Zaznacz jeden wiersz z prefiksem i numerem.");
    int[] gray = new int[pixels.length], hist = new int[256], labels = new int[pixels.length];
    for (int i = 0; i < pixels.length; i++) {
      int p = pixels[i];
      gray[i] = (299 * ((p >> 16) & 255) + 587 * ((p >> 8) & 255) + 114 * (p & 255)) / 1000;
      hist[gray[i]]++;
    }
    int threshold = otsu(hist, pixels.length);
    int dark = 0;
    for (int g : gray) if (g <= threshold) dark++;
    if (dark < 8 || dark > pixels.length * .45)
      throw new IllegalArgumentException(
          "Zaznacz ciemne znaki na jasnym tle, bez cienia i obramowania.");
    int[] queue = new int[pixels.length];
    ArrayList<Integer> areas = new ArrayList<>();
    areas.add(0);
    int largest = 0;
    for (int i = 0; i < pixels.length; i++) {
      if (gray[i] > threshold || labels[i] != 0) continue;
      int id = areas.size(), head = 0, tail = 1;
      queue[0] = i;
      labels[i] = id;
      while (head < tail) {
        int p = queue[head++], x = p % width, y = p / width;
        for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
          for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
            int q = yy * width + xx;
            if (labels[q] == 0 && gray[q] <= threshold) {
              labels[q] = id;
              queue[tail++] = q;
            }
          }
      }
      areas.add(tail);
      largest = Math.max(largest, tail);
    }
    int minArea = Math.max(3, largest / 100);
    int[] columns = new int[width];
    int top = height, bottom = -1;
    for (int i = 0; i < labels.length; i++) {
      if (areas.get(labels[i]) < minArea) {
        labels[i] = 0;
        continue;
      }
      columns[i % width]++;
      top = Math.min(top, i / width);
      bottom = Math.max(bottom, i / width);
    }
    if (bottom < top) throw new IllegalArgumentException("Nie znaleziono czytelnych znaków.");
    int bridge = Math.max(1, (bottom - top + 1) / 60);
    ArrayList<float[]> result = new ArrayList<>();
    int start = -1, last = -1;
    for (int x = 0; x <= width + bridge; x++) {
      if (x < width && columns[x] > 0) {
        if (start < 0) start = x;
        last = x;
      } else if (start >= 0 && x - last > bridge) {
        int y0 = height, y1 = -1;
        for (int xx = start; xx <= last; xx++)
          for (int y = top; y <= bottom; y++)
            if (labels[y * width + xx] != 0) {
              y0 = Math.min(y0, y);
              y1 = Math.max(y1, y);
            }
        int gw = last - start + 1, gh = y1 - y0 + 1;
        if (gh < (bottom - top + 1) * .25 || gw > gh * 1.5)
          throw new IllegalArgumentException(
              "Znaki stykają się lub w kadrze jest więcej niż jeden wiersz. Zaznacz ciaśniej.");
        result.add(normalize(gray, labels, width, start, y0, gw, gh));
        start = -1;
      }
    }
    if (result.size() < 2 || result.size() > 20)
      throw new IllegalArgumentException(
          "Potrzebny jest prefiks i numer, zapisane osobnymi znakami w jednym wierszu.");
    return result;
  }

  private static int otsu(int[] histogram, int total) {
    long sum = 0, partial = 0;
    int count = 0, chosen = 127;
    double best = -1;
    for (int i = 0; i < 256; i++) sum += (long) i * histogram[i];
    for (int i = 0; i < 255; i++) {
      count += histogram[i];
      partial += (long) i * histogram[i];
      if (count == 0 || count == total) continue;
      double difference = (double) partial / count - (double) (sum - partial) / (total - count);
      double variance = (double) count * (total - count) * difference * difference;
      if (variance > best) {
        best = variance;
        chosen = i;
      }
    }
    return Math.min(220, Math.max(10, chosen));
  }

  private static float[] normalize(
      int[] gray, int[] labels, int stride, int x, int y, int w, int h) {
    float scale = 20f / Math.max(w, h);
    int nw = Math.max(1, Math.round(w * scale)), nh = Math.max(1, Math.round(h * scale));
    int ox = (28 - nw) / 2, oy = (28 - nh) / 2;
    float[] out = new float[784];
    for (int yy = 0; yy < nh; yy++)
      for (int xx = 0; xx < nw; xx++) {
        float sx = Math.max(0, Math.min(w - 1, (xx + .5f) * w / nw - .5f));
        float sy = Math.max(0, Math.min(h - 1, (yy + .5f) * h / nh - .5f));
        int x0 = (int) sx,
            y0 = (int) sy,
            x1 = Math.min(w - 1, x0 + 1),
            y1 = Math.min(h - 1, y0 + 1);
        float dx = sx - x0, dy = sy - y0;
        out[(oy + yy) * 28 + ox + xx] =
            ink(gray, labels, (y + y0) * stride + x + x0) * (1 - dx) * (1 - dy)
                + ink(gray, labels, (y + y0) * stride + x + x1) * dx * (1 - dy)
                + ink(gray, labels, (y + y1) * stride + x + x0) * (1 - dx) * dy
                + ink(gray, labels, (y + y1) * stride + x + x1) * dx * dy;
      }
    return out;
  }

  private static float ink(int[] gray, int[] labels, int p) {
    return labels[p] == 0 ? 0 : (255 - gray[p]) / 255f;
  }
}
