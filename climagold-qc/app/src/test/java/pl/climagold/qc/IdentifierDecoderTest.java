package pl.climagold.qc;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class IdentifierDecoderTest {
  static float[][] scores(String text) {
    float[][] p = new float[text.length()][IdentifierDecoder.LABELS.length()];
    for (int i = 0; i < text.length(); i++)
      p[i][IdentifierDecoder.LABELS.indexOf(text.charAt(i))] = .99f;
    return p;
  }

  @Test
  public void recognizesOnlyKnownPrefixAndDigits() {
    for (String text : new String[] {"C00123", "K4567", "IEW123", "RIEW123", "R14577", "Z123"})
      assertEquals(text, IdentifierDecoder.decode(scores(text)).get(0).text);
  }

  @Test
  public void usesPrefixContextForIAndDigitContextForOne() {
    float[][] p = scores("IEW123");
    p[0][IdentifierDecoder.LABELS.indexOf('I')] = .1f;
    p[0][1] = .9f;
    assertEquals("IEW123", IdentifierDecoder.decode(p).get(0).text);
  }

  @Test
  public void keepsLeadingZerosAndAlternatives() {
    float[][] p = scores("C00123");
    p[4][2] = .55f;
    p[4][7] = .4f;
    var result = IdentifierDecoder.decode(p);
    assertEquals("C00123", result.get(0).text);
    assertTrue(result.stream().anyMatch(c -> c.text.equals("C00173")));
  }

  @Test
  public void rejectsUnknownOrMissingPrefix() {
    assertTrue(IdentifierDecoder.decode(scores("?123")).isEmpty());
    assertTrue(IdentifierDecoder.decode(scores("1234")).isEmpty());
    assertTrue(IdentifierDecoder.decode(scores("C")).isEmpty());
    assertTrue(IdentifierDecoder.decode(scores("CEK")).isEmpty());
  }

  @Test
  public void rejectsInvalidScoresAndExcessLength() {
    float[][] p = scores("C123");
    p[1][1] = Float.NaN;
    assertTrue(IdentifierDecoder.decode(p).isEmpty());
    assertTrue(IdentifierDecoder.decode(scores("C" + "1".repeat(20))).isEmpty());
  }

  @Test
  public void segmentsSeparateShapesAndRejectsBlank() {
    int[] pixels = new int[100 * 40];
    Arrays.fill(pixels, 0xffffffff);
    for (int y = 10; y < 30; y++) for (int x = 10; x < 20; x++) pixels[y * 100 + x] = 0xff000000;
    for (int y = 10; y < 30; y++) for (int x = 50; x < 60; x++) pixels[y * 100 + x] = 0xff000000;
    var glyphs = GlyphSegmenter.segment(pixels, 100, 40);
    assertEquals(2, glyphs.size());
    assertEquals(784, glyphs.get(0).length);
    Arrays.fill(pixels, 0xffffffff);
    assertThrows(IllegalArgumentException.class, () -> GlyphSegmenter.segment(pixels, 100, 40));
  }
}
