package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayoutLandscapeModifierTest
{
  static final KeyValue KV_MID = KeyValue.getKeyByName("m");
  static final KeyValue KV_A = KeyValue.getKeyByName("a");
  static final KeyValue KV_B = KeyValue.getKeyByName("b");

  @Before public void setMidColumn()
  {
    LayoutModifier.split_middle_column = row(oneKey(KV_MID, 1.f, 0.f));
  }

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
    assertSame(r, LayoutLandscapeModifier.split_row(r, 0));
  }

  @Test public void split_row_inserts_middle_key_and_shifts_right_half()
  {
    KeyboardData.Row r = fourKeysRow(); // keysWidth == 4, split at key 2
    KeyboardData.Row out = LayoutLandscapeModifier.split_row(r, 0);
    assertEquals(5, out.keys.size()); // 4 keys + the middle column
    assertEquals(KV_MID, out.keys.get(2).keys[0]);
    // Right half first key is nudged after the middle column:
    // shift = (ADDED_WIDTH - middle.width) / 2 == 2 for a width-1 middle key.
    assertEquals(2.f, out.keys.get(3).shift, 1e-9f);
  }

  @Test public void duplicate_row_middle_wide_key()
  {
    KeyboardData.Row r = row(oneKey(KV_A, 2.f, 0.f), oneKey(KV_B, 1.f, 0.f));
    KeyboardData.Row out = LayoutLandscapeModifier.split_row(r, 0);
    assertEquals(4, out.keys.size()); // copy + middle column + last key
    assertEquals(KV_MID, out.keys.get(1).keys[0]);
  }

  @Test public void split_row_skips_middle_key_when_row_index_absent()
  {
    KeyboardData.Row out = LayoutLandscapeModifier.split_row(fourKeysRow(), 50);
    assertEquals(4, out.keys.size());
  }

  // ---- transform_* -------------------------------------------------------

  @Test public void transform_number_row_splits_without_middle_column()
  {
    KeyboardData.Row out = LayoutLandscapeModifier.transform_number_row(fourKeysRow());
    assertNotNull(out);
    assertEquals(4, out.keys.size());
  }

  @Test public void transform_to_landscape_matches_rows_to_mid_column()
  {
    KeyboardData in = keyboard(fourKeysRow(), fourKeysRow());
    KeyboardData out = LayoutLandscapeModifier.transform_to_landscape(in);
    assertEquals(2, out.rows.size());
    // The bottom row (last in the list, row_index 0) gets the middle column key.
    assertEquals(4, out.rows.get(0).keys.size());
    assertEquals(5, out.rows.get(1).keys.size());
  }
}