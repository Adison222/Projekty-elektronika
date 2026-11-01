import pl.climagold.qc.SessionWindow;

public class SessionWindowTest {
  static int checks;

  static void check(boolean value) {
    checks++;
    if (!value) throw new AssertionError("Session check " + checks);
  }

  public static void main(String[] args) {
    check(SessionWindow.close(0, 5000) == 5000);
    check(
        SessionWindow.close(5000, 9000) == 5000); // Unlock must not extend a closed capture window.
    check(
        SessionWindow.shouldFinish(
            true, true, 0, false, false, true, false)); // Return from OEM camera.
    check(
        !SessionWindow.shouldFinish(true, true, 0, false, false, false, false)); // Still in camera.
    check(
        !SessionWindow.shouldFinish(
            true, true, 0, false, false, true, true)); // Locked: defer consent.
    check(
        SessionWindow.shouldFinish(
            true, false, 5000, false, false, true, false)); // Unlock pending session.
    check(
        !SessionWindow.shouldFinish(
            true, false, 5000, true, false, true, false)); // Denial: no permission loop.
    check(
        !SessionWindow.shouldFinish(true, true, 0, false, true, true, false)); // No duplicate work.
    check(
        !SessionWindow.shouldFinish(
            false, true, 0, false, false, true, false)); // Finished session.
    check(
        !SessionWindow.shouldFinish(true, false, 0, false, false, true, false)); // In-app gallery.
    check(SessionWindow.includes(100, 101, 10, 15000, 12000, 12));
    check(!SessionWindow.includes(100, 100, 10, 15000, 12000, 12)); // Baseline snapshot excluded.
    check(
        !SessionWindow.includes(
            100, 101, 10, 15000, 15001, 15)); // New captures after lock excluded.
    check(
        SessionWindow.includes(
            100, 101, 10, 15000, 14000, 25)); // Late publish, captured before lock.
    check(!SessionWindow.includes(100, 101, 10, 15000, 9000, 12)); // Imported old photo excluded.
    check(SessionWindow.includes(100, 101, 10, 15000, 0, 12)); // Missing capture time fallback.
    check(!SessionWindow.includes(100, 101, 10, 15000, 0, 16));
    check(!SessionWindow.includes(100, 101, 10, 0, 12000, 12)); // Can't scan an open window.
    check(SessionWindow.includes(100, 101, 10, 15000, 10000, 10)); // Start inclusive.
    check(SessionWindow.includes(100, 101, 10, 15000, 15000, 15)); // End inclusive.
    check(
        !SessionWindow.includes(
            110, 105, 20, 25000, 18000, 18)); // Rejoin excludes between-session photos.
    check(
        SessionWindow.includes(
            110, 111, 20, 25000, 21000, 21)); // Rejoin accepts only fresh photos.
    System.out.println(checks + " session assertions passed");
  }
}
