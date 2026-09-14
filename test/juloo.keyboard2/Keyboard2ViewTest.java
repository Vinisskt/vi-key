package com.vinisskt.vikey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** [Keyboard2View.compute_quick_tap_symbols]: builds the
    main-character -> sublabel map for the quick double-tap feature. */
public class Keyboard2ViewTest
{
  static final int F_LOC_AT_SLOT_3 = KeyboardData.Key.F_LOC << 3;

  static KeyValue charKv(char c)
  {
    return KeyValue.makeCharKey(c);
  }

  static KeyboardData.Key quickKey(KeyValue main, KeyValue sub, int slot3flags)
  {
    KeyValue[] ks = new KeyValue[9];
    ks[0] = main;
    ks[3] = sub;
    return new KeyboardData.Key(ks, null, slot3flags, 1.f, 0.f, null,
        KeyboardData.Key.Role.Normal);
  }

  static KeyboardData.Row rowOf(KeyboardData.Key... keys)
  {
    List<KeyboardData.Key> ks = new ArrayList<KeyboardData.Key>();
    for (KeyboardData.Key k : keys)
      ks.add(k);
    return new KeyboardData.Row(ks, 1.f, 0.f);
  }

  static KeyboardData keyboard(boolean quick_tap, KeyboardData.Row... rows)
  {
    List<KeyboardData.Row> rs = new ArrayList<KeyboardData.Row>();
    for (KeyboardData.Row r : rows)
      rs.add(r);
    return new KeyboardData(rs, 1.f, null, "s", "s", "n", true, false, true,
        quick_tap);
  }

  @Test
  public void layout_without_quick_tap_yields_empty_map()
  {
    Map<Character, Character> map = Keyboard2View.compute_quick_tap_symbols(
        keyboard(false, rowOf(
            quickKey(charKv('a'), charKv('a'), 0))));
    assertTrue(map.isEmpty());
  }

  @Test
  public void maps_main_char_to_sublabel()
  {
    Map<Character, Character> map = Keyboard2View.compute_quick_tap_symbols(
        keyboard(true, rowOf(
            quickKey(charKv('a'), charKv('x'), 0),
            quickKey(charKv('b'), charKv('y'), 0))));
    assertEquals(2, map.size());
    assertEquals(Character.valueOf('x'), map.get('a'));
    assertEquals(Character.valueOf('y'), map.get('b'));
  }

  @Test
  public void skips_loc_sublabel_key()
  {
    Map<Character, Character> map = Keyboard2View.compute_quick_tap_symbols(
        keyboard(true, rowOf(
            quickKey(charKv('a'), charKv('x'), 0),
            quickKey(charKv('b'), charKv('c'), F_LOC_AT_SLOT_3))));
    assertEquals(1, map.size());
    assertEquals(Character.valueOf('x'), map.get('a'));
    assertFalse(map.containsKey('b'));
  }

  @Test
  public void skips_keys_without_sublabel()
  {
    Map<Character, Character> map = Keyboard2View.compute_quick_tap_symbols(
        keyboard(true, rowOf(
            quickKey(charKv('a'), null, 0),
            quickKey(charKv('b'), charKv('y'), 0))));
    assertEquals(1, map.size());
    assertFalse(map.containsKey('a'));
    assertEquals(Character.valueOf('y'), map.get('b'));
  }

  @Test
  public void skips_non_char_keys()
  {
    Map<Character, Character> map = Keyboard2View.compute_quick_tap_symbols(
        keyboard(true, rowOf(
            quickKey(KeyValue.SHIFT, charKv('x'), 0),
            quickKey(charKv('a'), charKv('y'), 0))));
    assertEquals(1, map.size());
    assertFalse(map.containsKey('x'));
    assertEquals(Character.valueOf('y'), map.get('a'));
  }
}