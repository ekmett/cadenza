package cadenza.types;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TypeTest {
  @Test public void equalityWorks() {
    assertEquals(Type.bool, Type.bool);
    assertEquals(Type.nat, Type.nat);
    assertNotEquals(Type.bool, Type.nat);
    assertEquals(Type.arr(Type.bool,Type.nat),Type.arr(Type.bool, Type.nat));
    assertNotEquals(Type.arr(Type.bool,Type.nat),Type.arr(Type.bool, Type.bool));
  }
}