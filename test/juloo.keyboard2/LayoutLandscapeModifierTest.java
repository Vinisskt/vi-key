package com.vinisskt.vikey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayoutLandscapeModifierTest
{
  static final KeyValue KV_A = KeyValue.getKeyByName("a");
  static final KeyValue KV_B = KeyValue.getKeyByName("b");

  static KeyboardData.Key oneKey(KeyValue center, float width, float shift)
  {
    return new KeyboardData.Key(new KeyValue[]{ center, null, null, null, null,
        null, null, null, null }, null, 0, width, shift, null,
        KeyboardData.Key.Role.Normal);
  }

  static KeyboardData.Row row(KeyboardData.Key... keys)
  {
    return new KeyboardData.Row(Arrays.asList(keys), 1.f, 0.f);
  }

  static KeyboardData.Row fourKeysRow()
  {
    return row(oneKey(KV_A, 1.f, 0.f), oneKey(KV_A, 1.f, 0.f),
        oneKey(KV_A, 1.f, 0.f), oneKey(KV_A, 1.f, 0.f));
  }

  static KeyboardData keyboard(KeyboardData.Row... rows)
  {
    return new KeyboardData(Arrays.asList(rows), 4.f, null, "s", "s", "n",
        true, false, true, false);
  }

  // ---- split_row ---------------------------------------------------------

  @Test public void single_key_row_returns_same_row()
  {
    KeyboardData.Row r = row(oneKey(KV_A, 1.f, 0.f));
    assertSame(r, LayoutLandscapeModifier.split_row(r));
  }

  @Test public void split_row_inserts_gap_and_shifts_right_half()
  {
    KeyboardData.Row r = fourKeysRow(); // keysWidth == 4, split at key 2
    KeyboardData.Row out = LayoutLandscapeModifier.split_row(r);
    // A gap is inserted but no middle column key is added anymore.
    assertEquals(4, out.keys.size());
    assertEquals(LayoutLandscapeModifier.ADDED_WIDTH, out.keys.get(2).shift, 1e-9f);
  }

  @Test public void duplicate_row_middle_wide_key()
  {
    KeyboardData.Row r = row(oneKey(KV_A, 2.f, 0.f), oneKey(KV_B, 1.f, 0.f));
    KeyboardData.Row out = LayoutLandscapeModifier.split_row(r);
    assertEquals(3, out.keys.size()); // copy + last key, no middle column
  }

  // ---- transform_* -------------------------------------------------------

  @Test public void transform_number_row_splits_without_middle_column()
  {
    KeyboardData.Row out = LayoutLandscapeModifier.transform_number_row(fourKeysRow());
    assertNotNull(out);
    assertEquals(4, out.keys.size());
  }

  @Test public void transform_to_landscape_keeps_row_key_count()
  {
    KeyboardData in = keyboard(fourKeysRow(), fourKeysRow());
    KeyboardData out = LayoutLandscapeModifier.transform_to_landscape(in);
    assertEquals(2, out.rows.size());
    assertEquals(4, out.rows.get(0).keys.size());
    assertEquals(4, out.rows.get(1).keys.size());
  }
}