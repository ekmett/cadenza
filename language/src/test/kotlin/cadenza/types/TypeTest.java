package cadenza.types;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TypeTest {
  @Test public void equalityWorks() {
    assertEquals(Type.Companion.getBool(), Type.Companion.getBool());
    assertEquals(Type.Companion.getNat(), Type.Companion.getNat());
    assertNotEquals(Type.Companion.getBool(), Type.Companion.getNat());
    assertEquals(Type.Companion.arr(Type.Companion.getBool(), Type.Companion.getNat()), Type.Companion.arr(Type.Companion.getBool(), Type.Companion.getNat()));
    assertNotEquals(Type.Companion.arr(Type.Companion.getBool(), Type.Companion.getNat()), Type.Companion.arr(Type.Companion.getBool(), Type.Companion.getBool()));
  }
}