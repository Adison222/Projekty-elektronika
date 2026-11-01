import java.util.*;
import pl.climagold.qc.Numbers;

public class NumbersTest {
  static int checks;

  static void check(boolean ok) {
    checks++;
    if (!ok) throw new AssertionError("Check " + checks);
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
    check(
        Numbers.parse("C 123 K 0042 IEW123 RIEW 567")
            .equals(Arrays.asList("C123", "K0042", "IEW/123", "RIEW/567")));
    check(Numbers.parse("IEW:123 IEW/123 IEW 123").equals(Arrays.asList("IEW/123")));
    check(Numbers.parse("RIEW 00123").equals(Arrays.asList("RIEW/00123")));
    check(Numbers.parse("C\n123").isEmpty());
    check(Numbers.parse("RIEW zamówienie 123").isEmpty());
    check(Numbers.parse("ABC123 PRIEW123").isEmpty());
    check(Numbers.parse("C12O3 K4S67").isEmpty());
    check(Numbers.parse("C K IEW RIEW").isEmpty());
    check(Numbers.parse("12345 230 V 50 Hz").isEmpty());
    check(Numbers.parse(null).isEmpty());
    check(
        Numbers.parse("K9223372036854775807-9223372036854775807")
            .equals(Arrays.asList("K9223372036854775807")));
    check(Numbers.ocrSuggestions("C12O3 K4S67").equals(Arrays.asList("C1203", "K4567")));
    check(Numbers.ocrSuggestions("R1EW 12B3").equals(Arrays.asList("RIEW/1283")));
    check(Numbers.ocrSuggestions("I E W 00I2").equals(Arrays.asList("IEW/0012")));
    check(Numbers.ocrSuggestions("K I2Z3").equals(Arrays.asList("K1223")));
    check(Numbers.ocrSuggestions("COST 123 BOSS 123").isEmpty());
    check(Numbers.ocrSuggestions("C OOO").isEmpty());
    check(Numbers.ocrSuggestions("K\n123").isEmpty());
    check(Numbers.ocrSuggestions("230 V 50 Hz").isEmpty());
    check(Numbers.ocrSuggestions("C123 C123").equals(Arrays.asList("C123")));
    check(Numbers.ocrSuggestions(null).isEmpty());
    check(Numbers.folder(Numbers.parse("RIEW 00123").get(0)).equals("RIEW∕00123"));
    check(Numbers.ocrSuggestions("C14O15-17").equals(Arrays.asList("C14015", "C14016", "C14017")));
    check(Numbers.ocrSuggestions("C14015-99999").isEmpty());
    System.out.println(checks + " parser assertions passed");
  }
}
