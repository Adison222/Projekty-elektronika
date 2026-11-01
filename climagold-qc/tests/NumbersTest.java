import java.util.*;
import pl.climagold.qc.Numbers;

public class NumbersTest {
  static void check(boolean ok) {
    if (!ok) throw new AssertionError();
  }

  public static void main(String[] args) {
    check(
        Numbers.parse("C14015-23")
            .equals(
                Arrays.asList(
                    "C14015", "C14016", "C14017", "C14018", "C14019", "C14020", "C14021", "C14022",
                    "C14023")));
    check(Numbers.parse("C14568 c14578").equals(Arrays.asList("C14568", "C14578")));
    check(Numbers.parse("K4567").equals(Arrays.asList("K4567")));
    check(Numbers.folder("K4567").equals("4567"));
    check(Numbers.folder("C12679").equals("12679"));
    check(Numbers.parse("RIEW\\123-125").size() == 3);
    check(Numbers.parse("IEW/12-14").size() == 3);
    check(Numbers.parse("Z123").equals(Arrays.asList("Z123")));
    check(Numbers.parse("C14568 C14568").size() == 1);
    check(Numbers.parse("230 V 50 Hz").isEmpty());
    check(Numbers.parseGeneral("Numer: 001234").equals(Arrays.asList("001234")));
    check(Numbers.parseGeneral("AB-12/26 42 AB-12/26").equals(Arrays.asList("AB-12/26", "42")));
    check(Numbers.parseGeneral("123-125").equals(Arrays.asList("123-125")));
    check(Numbers.parseGeneral("Sam tekst").isEmpty());
    check(Numbers.parseGeneral(null).isEmpty());
    check(Numbers.parseGeneral("9".repeat(81)).isEmpty());
    check(Numbers.folder("AB/001").equals("AB∕001"));
    check(Numbers.parse("C14877/26").equals(Arrays.asList("C14877")));
    check(Numbers.parse("C14015-99999").isEmpty());
    System.out.println("19 parser assertions passed");
  }
}
