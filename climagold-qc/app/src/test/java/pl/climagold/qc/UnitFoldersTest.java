package pl.climagold.qc;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class UnitFoldersTest {
  @Test
  public void onlyListsDirectUnitFolders() {
    for (String p : List.of("DCIM/13932/", "DCIM/IEW123/", "DCIM/RIEW∕123/", "DCIM/R14577/"))
      assertTrue(UnitFolders.isUnitPath(p));
    for (String p :
        List.of("DCIM/Camera/", "DCIM/Camera2/", "Pictures/123/", "DCIM/.123/", "DCIM/123/nested/"))
      assertFalse(UnitFolders.isUnitPath(p));
    assertFalse(UnitFolders.isUnitPath(null));
  }

  @Test
  public void countsAndOrdersByLatestPhotoWithoutGuessingPrefix() {
    var entries =
        UnitFolders.group(
            List.of("DCIM/123/", "DCIM/IEW45/", "DCIM/123/", "DCIM/Camera/"),
            List.of(1L, 10L, 3L, 100L));
    assertEquals(2, entries.size());
    assertEquals("IEW45", entries.get(0).name);
    assertEquals("123", entries.get(1).name);
    assertEquals(2, entries.get(1).count);
  }
}
