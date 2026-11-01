package pl.climagold.qc;

import java.util.*;

/** Grammar-constrained proposals; low scores are rejected, nothing is auto-confirmed. */
public final class IdentifierDecoder {
  public static final String LABELS = "0123456789CEIKRWZ?";
  private static final String[] PREFIXES = {"C", "K", "IEW", "RIEW", "R", "Z"};

  public static final class Candidate {
    public final String text;
    public final double score;

    Candidate(String text, double score) {
      this.text = text;
      this.score = score;
    }
  }

  public static List<Candidate> decode(float[][] scores) {
    ArrayList<Candidate> all = new ArrayList<>();
    if (scores.length < 2 || scores.length > 20) return all;
    for (float[] row : scores) {
      if (row.length != LABELS.length()) return all;
      for (float v : row) if (!Float.isFinite(v) || v < 0 || v > 1) return all;
    }
    for (String prefix : PREFIXES) {
      int digits = scores.length - prefix.length();
      if (digits < 1 || digits > 16) continue;
      double log = 0;
      boolean allowed = true;
      for (int i = 0; i < prefix.length(); i++) {
        float raw = scores[i][LABELS.indexOf(prefix.charAt(i))];
        float letters = 0;
        for (int j = 10; j < 18; j++) letters += scores[i][j];
        float p = letters > 0 ? raw / letters : 0;
        if (raw < .025f || letters < .06f || p < .25f || scores[i][17] > .85f) {
          allowed = false;
          break;
        }
        log += Math.log(Math.max(p, 1e-8)) + .1 * Math.log(letters);
      }
      if (!allowed) continue;
      ArrayList<Candidate> beam = new ArrayList<>();
      beam.add(new Candidate(prefix, log));
      for (int i = prefix.length(); i < scores.length; i++) {
        ArrayList<Candidate> next = new ArrayList<>();
        float best = 0, mass = 0;
        for (int d = 0; d < 10; d++) {
          best = Math.max(best, scores[i][d]);
          mass += scores[i][d];
        }
        if (mass >= .1f && scores[i][17] <= .85f)
          for (Candidate c : beam)
            for (int d = 0; d < 10; d++)
              if (scores[i][d] >= .02f
                  && scores[i][d] / mass >= .15f
                  && scores[i][d] >= best * .35f)
                next.add(
                    new Candidate(
                        c.text + d, c.score + Math.log(scores[i][d] / mass) + .1 * Math.log(mass)));
        next.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        beam = new ArrayList<>(next.subList(0, Math.min(3, next.size())));
        if (beam.isEmpty()) break;
      }
      for (Candidate c : beam) {
        double score = Math.exp(c.score / scores.length);
        if (c.text.length() == scores.length && score >= .40) all.add(new Candidate(c.text, score));
      }
    }
    all.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
    return new ArrayList<>(all.subList(0, Math.min(5, all.size())));
  }
}
