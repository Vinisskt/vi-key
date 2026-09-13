package com.vinisskt.vikey;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;
import static org.junit.Assert.*;

/** KeyboardData unit tests: the [Key], [Row] and [Modmap] inner classes, the
    placement of extra keys, numpad merging and the XML parsing of layouts.
    The parsing is driven with a fake [Resources] which returns a minimal
    [XmlPullParser] implementation, since the mockable android.jar returns
    [null] parsers under the JVM. */
public class KeyboardDataTest
{
  static final KeyValue KV_A = kv("a");
  static final KeyValue KV_B = kv("b");
  static final KeyValue KV_C = kv("c");

  static KeyValue kv(String name)
  {
    return KeyValue.getKeyByName(name);
  }

  static KeyValue charKv(char c)
  {
    return kv(String.valueOf(c));
  }

  static FakeXmlPullParser parser(String xml) throws Exception
  {
    FakeXmlPullParser p = new FakeXmlPullParser();
    p.setInput(new StringReader(xml));
    return p;
  }

  /** Advance the parser past the XML prolog to the first [START_TAG]. */
  static void toFirstTag(XmlPullParser p) throws Exception
  {
    assertEquals(XmlPullParser.START_TAG, p.next());
  }

  static KeyboardData.Key oneKey(KeyValue center)
  {
    return new KeyboardData.Key(new KeyValue[]{ center, null, null, null, null,
        null, null, null, null }, null, 0, 1.f, 0.f, null,
        KeyboardData.Key.Role.Normal);
  }

  static KeyboardData.Key key(KeyValue center, KeyValue up, KeyValue right,
      KeyValue down, KeyValue left, KeyValue anticircle)
  {
    KeyValue[] ks = new KeyValue[9];
    ks[0] = center;
    ks[1] = left;  ks[2] = right; ks[3] = left;  ks[4] = right;
    ks[5] = left;  ks[6] = right; ks[7] = up;    ks[8] = down;
    return new KeyboardData.Key(ks, anticircle, 0, 1.f, 0.f, null,
        KeyboardData.Key.Role.Normal);
  }

  static KeyboardData.Row row(KeyboardData.Key... keys)
  {
    List<KeyboardData.Key> ks = new ArrayList<KeyboardData.Key>();
    for (KeyboardData.Key k : keys) ks.add(k);
    return new KeyboardData.Row(ks, 1.f, 0.f);
  }

  static KeyboardData keyboard(KeyboardData.Row... rows)
  {
    List<KeyboardData.Row> rs = new ArrayList<KeyboardData.Row>();
    for (KeyboardData.Row r : rows) rs.add(r);
    return newKeyboardWithRows(rs);
  }

  static KeyboardData newKeyboardWithRows(List<KeyboardData.Row> rows)
  {
    float kw = 0.f;
    for (KeyboardData.Row r : rows)
      kw = Math.max(kw, r.keysWidth);
    return new KeyboardData(rows, kw, null, "s", "s", "n", true, false, true,
        false);
  }

  static final class FauxResources extends Resources
  {
    Map<Integer, String> _layouts = new HashMap<Integer, String>();

    FauxResources()
    { super(null, null, null); }

    FauxResources add(int id, String xml)
    {
      _layouts.put(id, xml);
      return this;
    }

    @Override public XmlResourceParser getXml(int id)
    {
      String xml = _layouts.get(id);
      if (xml == null)
        return null;
      try
      {
        return parser(xml);
      }
      catch (Exception e)
      {
        throw new RuntimeException(e);
      }
    }
  }

  static List<Map.Entry<KeyValue, KeyboardData.PreferredPos>> extras(
      KeyValue kv, KeyboardData.PreferredPos pos)
  {
    List<Map.Entry<KeyValue, KeyboardData.PreferredPos>> l =
        new ArrayList<Map.Entry<KeyValue, KeyboardData.PreferredPos>>();
    l.add(new java.util.AbstractMap.SimpleEntry<KeyValue,
        KeyboardData.PreferredPos>(kv, pos));
    return l;
  }

  @Test
  public void key_parse_reads_all_attributes() throws Exception
  {
    FakeXmlPullParser p = parser(
        "<key key0='a' key6='b' width='2.5' shift='0.5' indication='Go' role='action'/>");
    toFirstTag(p);
    KeyboardData.Key k = KeyboardData.Key.parse(p);
    assertTrue(KV_A.equals(k.keys[0]));
    assertTrue(KV_B.equals(k.keys[6]));
    assertEquals(2.5f, k.width, 0.f);
    assertEquals(0.5f, k.shift, 0.f);
    assertEquals("Go", k.indication);
    assertEquals(KeyboardData.Key.Role.Action, k.role);
  }

  @Test
  public void key_parse_defaults_and_compass_synonyms() throws Exception
  {
    FakeXmlPullParser p = parser("<key n='a' s='b' nw='c' se='d'/>");
    toFirstTag(p);
    KeyboardData.Key k = KeyboardData.Key.parse(p);
    assertEquals(1.f, k.width, 0.f);
    assertEquals(0.f, k.shift, 0.f);
    assertEquals(null, k.indication);
    assertEquals(KeyboardData.Key.Role.Normal, k.role);
    assertTrue(kv("a").equals(k.keys[7]));
    assertTrue(kv("b").equals(k.keys[8]));
    assertTrue(kv("c").equals(k.keys[1]));
    assertTrue(kv("d").equals(k.keys[4]));
  }

  @Test
  public void key_parse_loc_prefix_flags_key() throws Exception
  {
    FakeXmlPullParser k1p = parser("<key key0='loc a' key1='b'/>");
    toFirstTag(k1p);
    KeyboardData.Key k1 = KeyboardData.Key.parse(k1p);
    assertTrue(k1.keyHasFlag(0, KeyboardData.Key.F_LOC));
    assertFalse(k1.keyHasFlag(1, KeyboardData.Key.F_LOC));
    FakeXmlPullParser k2p = parser("<key c='loc z'/>");
    toFirstTag(k2p);
    KeyboardData.Key k2 = KeyboardData.Key.parse(k2p);
    assertTrue(k2.keyHasFlag(0, KeyboardData.Key.F_LOC));
    assertTrue(kv("z").equals(k2.keys[0]));
  }

  @Test
  public void key_parse_synonym_conflict_is_an_error() throws Exception
  {
    FakeXmlPullParser p = parser("<key key0='a' c='b'/>");
    toFirstTag(p);
    try
    {
      KeyboardData.Key.parse(p);
      fail("Expected exception for synonym conflict");
    }
    catch (Exception _e) {}
  }

  @Test
  public void key_parse_anticircle_and_null_attrs() throws Exception
  {
    FakeXmlPullParser p = parser("<key key0='a' anticircle='z'/>");
    toFirstTag(p);
    KeyboardData.Key k = KeyboardData.Key.parse(p);
    assertTrue(kv("z").equals(k.anticircle));
    assertTrue(kv("a").equals(k.keys[0]));
    assertNull(k.keys[1]);
  }

  @Test
  public void role_parse_maps_all_roles()
  {
    assertEquals(KeyboardData.Key.Role.Normal, KeyboardData.Key.Role.parse("normal"));
    assertEquals(KeyboardData.Key.Role.Action, KeyboardData.Key.Role.parse("action"));
    assertEquals(KeyboardData.Key.Role.Space_bar, KeyboardData.Key.Role.parse("space_bar"));
    assertEquals(KeyboardData.Key.Role.Suggestion, KeyboardData.Key.Role.parse("suggestion"));
    assertEquals(KeyboardData.Key.Role.Normal, KeyboardData.Key.Role.parse("unknown"));
  }

  @Test
  public void strip_prefix_returns_null_when_missing()
  {
    assertEquals("a", KeyboardData.Key.stripPrefix("loc a", "loc "));
    assertNull(KeyboardData.Key.stripPrefix("b", "loc "));
  }

  @Test
  public void key_methods_update_fields()
  {
    KeyboardData.Key k = oneKey(KV_A);
    KeyboardData.Key k2 = k.withKeyValue(6, KV_B);
    assertTrue(KV_B.equals(k2.getKeyValue(6)));
    assertTrue(KV_A.equals(k.getKeyValue(0)));

    KeyboardData.Key k3 = k.scaleWidth(2.f);
    assertEquals(2.f, k3.width, 0.f);

    KeyboardData.Key k4 = k.withWidth(3.f);
    assertEquals(3.f, k4.width, 0.f);
    assertEquals(0.f, k4.shift, 0.f);

    KeyboardData.Key k5 = k.withShift(1.f);
    assertEquals(1.f, k5.shift, 0.f);
    assertEquals(1.f, k5.width, 0.f);

    KeyboardData.Key k6 = k.withWidthAndShift(4.f, 2.f);
    assertEquals(4.f, k6.width, 0.f);
    assertEquals(2.f, k6.shift, 0.f);

    assertTrue(k.hasValue(KV_A));
    assertFalse(k.hasValue(KV_B));
    assertFalse(KeyboardData.Key.EMPTY.hasValue(KV_A));
    assertEquals(null, KeyboardData.Key.EMPTY.getKeyValue(0));
  }

  @Test
  public void key_negative_widths_are_clamped()
  {
    KeyboardData.Key k = new KeyboardData.Key(new KeyValue[9], null, 0, -1.f,
        -2.f, null, KeyboardData.Key.Role.Normal);
    assertEquals(0.f, k.width, 0.f);
    assertEquals(0.f, k.shift, 0.f);
  }

  @Test
  public void get_keys_populates_positions()
  {
    KeyboardData.Key k = oneKey(KV_A);
    Map<KeyValue, KeyboardData.KeyPos> dst = new HashMap<KeyValue, KeyboardData.KeyPos>();
    k.getKeys(dst, 3, 5);
    assertEquals(1, dst.size());
    KeyboardData.KeyPos p = dst.get(KV_A);
    assertNotNull(p);
    assertEquals(3, p.row);
    assertEquals(5, p.col);
    assertEquals(0, p.dir);
  }

  @Test
  public void row_ctor_computes_width_and_clamps()
  {
    KeyboardData.Key wide = oneKey(KV_A).withWidth(2.f);
    KeyboardData.Row r = new KeyboardData.Row(
        new ArrayList<KeyboardData.Key>(java.util.Arrays.asList(
            wide, oneKey(KV_B))), 0.f, -1.f);
    assertEquals(3.f, r.keysWidth, 0.f);
    assertEquals(0.5f, r.height, 0.f); // min height for non-empty rows
    assertEquals(0.f, r.shift, 0.f); // clamp
  }

  @Test
  public void row_copy_and_with_keys()
  {
    KeyboardData.Row r = row(oneKey(KV_A), oneKey(KV_B));
    KeyboardData.Row c = r.copy();
    assertEquals(2, c.keys.size());
    KeyboardData.Row w = r.with_keys(java.util.Arrays.asList(oneKey(KV_C)));
    assertEquals(1, w.keys.size());
    assertTrue(KV_C.equals(w.keys.get(0).keys[0]));
    KeyboardData.Row e = new KeyboardData.Row(
        new ArrayList<KeyboardData.Key>(), 0.f, 0.f);
    assertEquals(0.f, e.height, 0.f); // empty rows can be 0 high
  }

  @Test
  public void row_get_keys_and_get_key_at_pos()
  {
    KeyboardData.Row r = row(oneKey(KV_A), oneKey(KV_B));
    Map<KeyValue, KeyboardData.KeyPos> m = r.getKeys(4);
    assertEquals(2, m.size());
    assertEquals(4, m.get(KV_A).row);
    assertEquals(0, m.get(KV_A).col);
    assertEquals(1, m.get(KV_B).col);
    assertEquals(KV_A, r.get_key_at_pos(new KeyboardData.KeyPos(0, 0, 0)).keys[0]);
    assertNull(r.get_key_at_pos(new KeyboardData.KeyPos(0, 5, 0)));
  }

  @Test
  public void row_map_keys_and_update_width()
  {
    KeyboardData.Row r = row(oneKey(KV_A).withWidth(2.f), oneKey(KV_B).withWidth(2.f));
    assertEquals(4.f, r.keysWidth, 0.f);
    KeyboardData.Row u = r.updateWidth(8.f); // scale by 2
    assertEquals(4.f, u.keys.get(0).width, 0.f);
    KeyboardData.Row mapped = r.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      { return k.withWidth(1.f); }
    });
    assertEquals(1.f, mapped.keys.get(0).width, 0.f);
  }

  @Test
  public void row_parse_xml() throws Exception
  {
    FakeXmlPullParser p = parser(
        "<row height='2' shift='0.25'><key key0='a'/><key key0='b'/></row>");
    toFirstTag(p);
    KeyboardData.Row r = KeyboardData.Row.parse(p);
    assertEquals(2, r.keys.size());
    assertEquals(2.f, r.height, 0.f);
    assertEquals(0.25f, r.shift, 0.f);
    assertEquals(2.f, r.keysWidth, 0.f);
  }

  @Test
  public void row_parse_scales_width() throws Exception
  {
    FakeXmlPullParser p = parser(
        "<row scale='6'><key key0='a' width='2'/><key key0='b' width='2'/></row>");
    toFirstTag(p);
    KeyboardData.Row r = KeyboardData.Row.parse(p);
    assertEquals(6.f, r.keysWidth, 0.f);
    assertEquals(3.f, r.keys.get(0).width, 0.f);
  }

  @Test
  public void row_parse_empty_row() throws Exception
  {
    FakeXmlPullParser p = parser("<row height='0'/>");
    toFirstTag(p);
    KeyboardData.Row r = KeyboardData.Row.parse(p);
    assertEquals(0, r.keys.size());
  }

  @Test
  public void row_parse_bad_tag_is_an_error() throws Exception
  {
    FakeXmlPullParser p = parser("<row><notkey/></row>");
    toFirstTag(p);
    try
    {
      KeyboardData.Row.parse(p);
      fail("Expected parsing error");
    }
    catch (Exception _e) {}
  }

  @Test
  public void modmap_add_and_get()
  {
    Modmap mm = new Modmap();
    assertNull(mm.get(Modmap.M.Shift, KV_A));
    mm.add(Modmap.M.Shift, KV_A, KV_B);
    assertTrue(KV_B.equals(mm.get(Modmap.M.Shift, KV_A)));
    assertNull(mm.get(Modmap.M.Fn, KV_A));
  }

  @Test
  public void modmap_parse_xml() throws Exception
  {
    FakeXmlPullParser p = parser(
        "<modmap><shift a='a' b='b'/><fn a='a' b='c'/><ctrl a='a' b='c'/>"
        + "</modmap>");
    toFirstTag(p);
    Modmap mm = KeyboardData.parse_modmap(p);
    assertTrue(KV_B.equals(mm.get(Modmap.M.Shift, KV_A)));
    assertTrue(KV_C.equals(mm.get(Modmap.M.Fn, KV_A)));
    assertNotNull(mm.get(Modmap.M.Ctrl, KV_A));
  }

  @Test
  public void modmap_parse_bad_tag_is_an_error() throws Exception
  {
    FakeXmlPullParser p = parser("<modmap><zzz a='a' b='b'/></modmap>");
    toFirstTag(p);
    try
    {
      KeyboardData.parse_modmap(p);
      fail("Expected parsing error");
    }
    catch (Exception _e) {}
  }

  @Test
  public void key_pos_with_dir()
  {
    KeyboardData.KeyPos p = new KeyboardData.KeyPos(1, 2, 3);
    assertEquals(1, p.row);
    assertEquals(2, p.col);
    assertEquals(3, p.dir);
    KeyboardData.KeyPos p2 = p.with_dir(7);
    assertEquals(7, p2.dir);
    assertEquals(1, p2.row);
  }

  @Test
  public void preferred_pos_constructors_and_constants()
  {
    KeyboardData.PreferredPos any = KeyboardData.PreferredPos.ANYWHERE;
    assertEquals(1, any.positions.length);
    assertEquals(-1, any.positions[0].dir);
    KeyboardData.PreferredPos def = KeyboardData.PreferredPos.DEFAULT;
    assertEquals(4, def.positions.length);
    KeyboardData.PreferredPos p = new KeyboardData.PreferredPos(KV_A);
    assertEquals(KV_A, p.next_to);
    KeyboardData.PreferredPos c = new KeyboardData.PreferredPos(p);
    assertEquals(KV_A, c.next_to);
    assertEquals(p.positions, c.positions);
    KeyboardData.PreferredPos empty = new KeyboardData.PreferredPos();
    assertNull(empty.next_to);
    assertEquals(KV_A, new KeyboardData.PreferredPos(KV_A, any.positions).next_to);
  }

  @Test
  public void add_key_to_pos_places_in_first_free_dir()
  {
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(oneKey(KV_A)));
    boolean placed = newKeyboardWithRows(rows).
        add_key_to_pos(rows, KV_B, new KeyboardData.KeyPos(-1, -1, -1));
    assertTrue(placed);
    assertTrue(KV_B.equals(rows.get(0).keys.get(0).getKeyValue(1)));
  }

  @Test
  public void add_key_to_pos_full_row_returns_false()
  {
    // A key with all 9 slots filled leaves no room.
    KeyboardData.Key full = new KeyboardData.Key(new KeyValue[]{
        KV_A, KV_A, KV_A, KV_A, KV_A, KV_A, KV_A, KV_A, KV_A},
        null, 0, 1.f, 0.f, null, KeyboardData.Key.Role.Normal);
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(full));
    boolean placed = newKeyboardWithRows(rows).
        add_key_to_pos(rows, KV_B, new KeyboardData.KeyPos(0, 0, -1));
    assertFalse(placed);
  }

  @Test
  public void add_key_to_pos_specific_col()
  {
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(oneKey(KV_A), oneKey(KV_B)));
    newKeyboardWithRows(rows).add_key_to_pos(rows, KV_C,
        new KeyboardData.KeyPos(0, 1, 1));
    assertTrue(KV_C.equals(rows.get(0).keys.get(1).getKeyValue(1)));
  }

  @Test
  public void add_key_to_preferred_pos_next_to()
  {
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(oneKey(KV_A)));
    KeyboardData.PreferredPos pos = new KeyboardData.PreferredPos(
        new KeyboardData.KeyPos[]{ new KeyboardData.KeyPos(0, -1, 2) });
    pos.next_to = KV_A;
    boolean placed = newKeyboardWithRows(rows).add_key_to_preferred_pos(
        rows, KV_B, pos);
    assertTrue(placed);
    assertTrue(KV_B.equals(rows.get(0).keys.get(0).getKeyValue(2)));
  }

  @Test
  public void add_key_to_preferred_pos_without_anchor()
  {
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(oneKey(KV_A)));
    // next_to points at a missing key: falls back to the positions.
    KeyboardData.PreferredPos pos = new KeyboardData.PreferredPos(
        new KeyboardData.KeyPos[]{ new KeyboardData.KeyPos(0, 0, 1) });
    pos.next_to = KV_C;
    boolean placed = newKeyboardWithRows(rows).add_key_to_preferred_pos(
        rows, KV_B, pos);
    assertTrue(placed);
    assertTrue(KV_B.equals(rows.get(0).keys.get(0).getKeyValue(1)));
  }

  @Test
  public void add_key_to_preferred_pos_preferred_direction()
  {
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(oneKey(KV_A)));
    // A preferred position matching the anchor's row and column.
    KeyboardData.PreferredPos pos = new KeyboardData.PreferredPos(
        new KeyboardData.KeyPos[]{ new KeyboardData.KeyPos(0, 0, 1) });
    pos.next_to = KV_A;
    boolean placed = newKeyboardWithRows(rows).add_key_to_preferred_pos(
        rows, KV_B, pos);
    assertTrue(placed);
    assertTrue(KV_B.equals(rows.get(0).keys.get(0).getKeyValue(1)));
  }

  @Test
  public void add_key_to_preferred_pos_fallback_dir()
  {
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    rows.add(row(oneKey(KV_A)));
    // No position matches the anchor: the free direction of the anchor is used.
    KeyboardData.PreferredPos pos = new KeyboardData.PreferredPos(
        new KeyboardData.KeyPos[]{ new KeyboardData.KeyPos(1, 0, 1) });
    pos.next_to = KV_A;
    boolean placed = newKeyboardWithRows(rows).add_key_to_preferred_pos(
        rows, KV_B, pos);
    assertTrue(placed);
    assertTrue(KV_B.equals(rows.get(0).keys.get(0).getKeyValue(1)));
  }

  @Test
  public void add_extra_keys_places_at_anywhere_fallback()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)));
    // DEFAULT positions target rows 1..2: none exist, so ANYWHERE is used.
    KeyboardData out = kb.addExtraKeys(
        extras(KV_B, KeyboardData.PreferredPos.DEFAULT).iterator());
    assertNotNull(out.findKeyWithValue(KV_B));
  }

  @Test
  public void add_extra_keys_prefers_existing_positions()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)));
    KeyboardData out = kb.addExtraKeys(extras(KV_B,
        new KeyboardData.PreferredPos(new KeyboardData.KeyPos[]{
            new KeyboardData.KeyPos(0, 0, 1) })).iterator());
    assertNotNull(out.findKeyWithValue(KV_B));
  }

  @Test
  public void keyboard_ctor_computes_width_and_height()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A).withWidth(2.f),
        oneKey(KV_B).withWidth(1.5f)), row(oneKey(KV_C).withWidth(1.f)));
    assertEquals(3.5f, kb.keysWidth, 0.f);
    assertEquals(2.f, kb.keysHeight, 0.f); // two rows of height 1
    assertEquals("n", kb.name);
    assertEquals("s", kb.script);
    assertEquals("s", kb.numpad_script);
    assertTrue(kb.bottom_row);
    assertFalse(kb.embedded_number_row);
  }

  @Test
  public void keyboard_min_width_is_one()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)));
    assertEquals(1.f, kb.keysWidth, 0.f);
  }

  @Test
  public void map_keys_transforms_every_key()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)));
    KeyboardData out = kb.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      { return k.withWidth(3.f); }
    });
    assertEquals(3.f, out.rows.get(0).keys.get(0).width, 0.f);
    assertEquals(3.f, out.keysWidth, 0.f);
  }

  @Test
  public void map_key_values_respects_localized_flag()
  {
    KeyboardData.Key bLoc = new KeyboardData.Key(
        new KeyValue[]{ KV_B, null, null, null, null, KV_A, null, null, null },
        null, KeyboardData.Key.F_LOC, 1.f, 0.f, null,
        KeyboardData.Key.Role.Normal);
    KeyboardData out = keyboard(row(bLoc, oneKey(KV_A))).mapKeys(
        new KeyboardData.MapKeyValues() {
          public KeyValue apply(KeyValue c, boolean localized)
          { return localized ? KV_C : c; }
        });
    assertTrue(KV_C.equals(out.rows.get(0).keys.get(0).keys[0]));
    assertTrue(KV_A.equals(out.rows.get(0).keys.get(0).keys[5]));
  }

  @Test
  public void with_rows_recomputes_width()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)).updateWidth(4.f));
    assertEquals(4.f, kb.keysWidth, 0.f);
    KeyboardData out = kb.with_rows(new ArrayList<KeyboardData.Row>());
    assertEquals(1.f, out.keysWidth, 0.f); // no rows -> clamped to 1
    assertEquals(0.f, out.keysHeight, 0.f);
  }

  @Test
  public void add_num_pad_extends_each_row()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)), row(oneKey(KV_A)));
    KeyboardData np = keyboard(row(oneKey(KV_B), oneKey(KV_C)), row(oneKey(KV_B)));
    KeyboardData out = kb.addNumPad(np);
    assertEquals(3, out.rows.get(0).keys.size()); // 1 key + 2 numpad keys
    assertEquals(2, out.rows.get(1).keys.size()); // 1 key + 1 numpad key
    // The first numpad key in a row is shifted.
    assertTrue(out.rows.get(0).keys.get(1).shift > 0.f);
  }

  @Test
  public void add_num_pad_keeps_rows_without_numpad()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)), row(oneKey(KV_A)));
    KeyboardData np = keyboard(row(oneKey(KV_B)));
    KeyboardData out = kb.addNumPad(np);
    assertEquals(2, out.rows.get(0).keys.size());
    assertEquals(1, out.rows.get(1).keys.size()); // no numpad row: unchanged
  }

  @Test
  public void insert_row_scales_to_keyboard_width()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A).withWidth(2.f)));
    KeyboardData out = kb.insert_row(row(oneKey(KV_B).withWidth(1.f)), 1);
    assertEquals(2, out.rows.size());
    assertTrue(KV_B.equals(out.rows.get(1).keys.get(0).keys[0]));
    assertEquals(2.f, out.rows.get(1).keys.get(0).width, 0.f);
  }

  @Test
  public void find_key_with_value_handles_stale_positions()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)), row(oneKey(KV_B)));
    KeyValue ter = charKv('t');
    KeyboardData extended = kb.addExtraKeys(extras(ter,
        new KeyboardData.PreferredPos(new KeyboardData.KeyPos[]{
            new KeyboardData.KeyPos(1, 0, 3) })).iterator());
    assertNotNull(extended.findKeyWithValue(ter));
    // Rows shrunk below the cached position: lookup returns null.
    KeyboardData smaller = extended.with_rows(
        new ArrayList<KeyboardData.Row>(extended.rows.subList(0, 1)));
    assertNull(smaller.findKeyWithValue(ter));
  }

  @Test
  public void get_keys_is_cached_and_returns_positions()
  {
    KeyboardData kb = keyboard(row(oneKey(KV_A)));
    Map<KeyValue, KeyboardData.KeyPos> m1 = kb.getKeys();
    Map<KeyValue, KeyboardData.KeyPos> m2 = kb.getKeys();
    assertSame(m1, m2);
    assertEquals(0, m1.get(KV_A).row);
    assertEquals(0, m1.get(KV_A).col);
    assertEquals(0, m1.get(KV_A).dir);
  }

  @Test
  public void load_parses_full_layout() throws Exception
  {
    String xml =
        "<keyboard name='test' bottom_row='false' embedded_number_row='true'"
      + " locale_extra_keys='false' quick_tap='true' script='sr'"
      + " numpad_script='nps' width='12'>"
      + "<row height='1'><key key0='a'/></row>"
      + "<modmap><shift a='a' b='b'/></modmap>"
      + "</keyboard>";
    FauxResources res = new FauxResources().add(1000, xml);
    KeyboardData kb = KeyboardData.load(res, 1000);
    assertNotNull(kb);
    assertEquals("test", kb.name);
    assertEquals("sr", kb.script);
    assertEquals("nps", kb.numpad_script);
    assertFalse(kb.bottom_row);
    assertTrue(kb.embedded_number_row);
    assertFalse(kb.locale_extra_keys);
    assertTrue(kb.quick_tap);
    assertEquals(12.f, kb.keysWidth, 0.f);
    assertEquals(1, kb.rows.size());
    assertNotNull(kb.modmap);
    assertTrue(KV_B.equals(kb.modmap.get(Modmap.M.Shift, KV_A)));
  }

  @Test
  public void load_caches_results() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(1001, "<keyboard name='x'><row><key key0='a'/></row></keyboard>");
    KeyboardData k1 = KeyboardData.load(res, 1001);
    KeyboardData k2 = KeyboardData.load(res, 1001);
    assertNotNull(k1);
    assertSame(k1, k2);
  }

  @Test
  public void load_returns_null_and_caches_on_error() throws Exception
  {
    FauxResources res = new FauxResources().add(1002, "<foo/>");
    assertNull(KeyboardData.load(res, 1002));
    assertNull(KeyboardData.load(res, 1002)); // cached
  }

  @Test
  public void load_handles_missing_resources() throws Exception
  {
    FauxResources res = new FauxResources();
    assertNull(KeyboardData.load(res, 0x7f010000));
  }

  @Test
  public void load_num_pad_uses_parse_keyboard() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(R.xml.numpad,
            "<keyboard><row><key key0='a'/></row></keyboard>");
    KeyboardData np = KeyboardData.load_num_pad(res);
    assertNotNull(np);
    assertEquals(1, np.rows.size());
  }

  @Test
  public void load_row_parses_a_single_row() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(1004, "<row><key key0='a'/><key key0='b'/></row>");
    KeyboardData.Row r = KeyboardData.load_row(res, 1004);
    assertEquals(2, r.keys.size());
  }

  @Test
  public void load_empty_document_returns_null() throws Exception
  {
    FauxResources res = new FauxResources().add(2000, "");
    assertNull(KeyboardData.load(res, 2000));
  }

  @Test
  public void load_row_empty_document_throws() throws Exception
  {
    FauxResources res = new FauxResources().add(2001, "");
    try
    {
      KeyboardData.load_row(res, 2001);
      fail("Expected a parsing error");
    }
    catch (Exception _e) {}
  }

  @Test
  public void load_string_error_returns_null()
  {
    assertNull(KeyboardData.load_string("<keyboard/>"));
  }

  @Test
  public void parse_keyboard_inherits_numpad_script() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(1005, "<keyboard script='s'><row><key key0='a'/></row></keyboard>");
    KeyboardData kb = KeyboardData.load(res, 1005);
    assertNotNull(kb);
    assertEquals("s", kb.script);
    assertEquals("s", kb.numpad_script);
  }

  @Test
  public void parse_keyboard_empty_script_is_an_error() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(1006, "<keyboard script=''><row><key key0='a'/></row></keyboard>");
    assertNull(KeyboardData.load(res, 1006));
  }

  @Test
  public void parse_keyboard_empty_numpad_script_is_an_error() throws Exception
  {
    FauxResources res = new FauxResources().add(1007,
        "<keyboard script='s' numpad_script=''><row><key key0='a'/></row>"
        + "</keyboard>");
    assertNull(KeyboardData.load(res, 1007));
  }

  @Test
  public void parse_keyboard_multiple_modmaps_is_an_error() throws Exception
  {
    FauxResources res = new FauxResources().add(1008,
        "<keyboard><modmap><shift a='a' b='a'/></modmap>"
        + "<modmap><fn a='a' b='a'/></modmap><row><key key0='a'/></row>"
        + "</keyboard>");
    assertNull(KeyboardData.load(res, 1008));
  }

  @Test
  public void parse_keyboard_unknown_child_is_an_error() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(1009, "<keyboard><whatever/></keyboard>");
    assertNull(KeyboardData.load(res, 1009));
  }

  @Test
  public void parse_keyboard_missing_root_is_an_error() throws Exception
  {
    FauxResources res = new FauxResources()
        .add(1010, "<not-keyboard/>");
    assertNull(KeyboardData.load(res, 1010));
  }

  @Test
  public void parse_keyboard_computes_width_from_rows() throws Exception
  {
    FauxResources res = new FauxResources().add(1011,
        "<keyboard><row><key key0='a' width='3'/><key key0='b' width='2'/>"
        + "</row></keyboard>");
    KeyboardData kb = KeyboardData.load(res, 1011);
    assertNotNull(kb);
    assertEquals(5.f, kb.keysWidth, 0.f);
  }

  @Test
  public void parse_keyboard_bool_attribute_accepts_only_true() throws Exception
  {
    FauxResources res = new FauxResources().add(1012,
        "<keyboard bottom_row='yes'><row><key key0='a'/></row></keyboard>");
    KeyboardData kb = KeyboardData.load(res, 1012);
    assertNotNull(kb);
    assertFalse(kb.bottom_row);
  }

  @Test
  public void key_value_in_multiple_positions_maps_to_last() throws Exception
  {
    // A value present at several positions maps to the last one walked.
    FakeXmlPullParser p = parser("<key key0='a' key6='a' key7='a'/>");
    toFirstTag(p);
    KeyboardData.Key k = KeyboardData.Key.parse(p);
    Map<KeyValue, KeyboardData.KeyPos> m = new HashMap<KeyValue, KeyboardData.KeyPos>();
    k.getKeys(m, 0, 0);
    assertEquals(7, m.get(KV_A).dir);
  }
}