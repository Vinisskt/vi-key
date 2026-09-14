package com.vinisskt.vikey.prefs;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Round-tripping of [ListGroupPreference] serializers, in particular the
    [LayoutsPreference] one. Uses the real [org.json] pulled as a test
    dependency, since the Android [org.json] is an empty stub on the JVM. */
public class ListGroupPreferenceTest
{
  static String round_trip(List<LayoutsPreference.Layout> items)
  {
    return ListGroupPreference.save_to_string(items, LayoutsPreference.SERIALIZER);
  }

  static List<LayoutsPreference.Layout> reload(String s)
  {
    return ListGroupPreference.load_from_string(s, LayoutsPreference.SERIALIZER);
  }

  @Test
  public void layouts_system_round_trip()
  {
    List<LayoutsPreference.Layout> items = new ArrayList<LayoutsPreference.Layout>();
    items.add(new LayoutsPreference.SystemLayout());
    List<LayoutsPreference.Layout> loaded = reload(round_trip(items));
    assertEquals(1, loaded.size());
    assertTrue(loaded.get(0) instanceof LayoutsPreference.SystemLayout);
  }

  @Test
  public void layouts_named_round_trip()
  {
    List<LayoutsPreference.Layout> items = new ArrayList<LayoutsPreference.Layout>();
    items.add(new LayoutsPreference.NamedLayout("vim_prog"));
    items.add(new LayoutsPreference.SystemLayout());
    List<LayoutsPreference.Layout> loaded = reload(round_trip(items));
    assertEquals(2, loaded.size());
    assertTrue(loaded.get(0) instanceof LayoutsPreference.NamedLayout);
    assertEquals("vim_prog", ((LayoutsPreference.NamedLayout)loaded.get(0)).name);
    assertTrue(loaded.get(1) instanceof LayoutsPreference.SystemLayout);
  }

  @Test
  public void layouts_custom_round_trip_preserves_xml()
  {
    List<LayoutsPreference.Layout> items = new ArrayList<LayoutsPreference.Layout>();
    items.add(LayoutsPreference.CustomLayout.parse("rows: qwerty"));
    List<LayoutsPreference.Layout> loaded = reload(round_trip(items));
    assertEquals(1, loaded.size());
    assertTrue(loaded.get(0) instanceof LayoutsPreference.CustomLayout);
    assertEquals("rows: qwerty", ((LayoutsPreference.CustomLayout)loaded.get(0)).xml);
  }

  @Test
  public void layouts_custom_unknown_kind_falls_back_to_system()
  {
    List<LayoutsPreference.Layout> loaded =
      reload("[\"system\",{\"kind\":\"custom\",\"xml\":\"x\"}]");
    assertEquals(2, loaded.size());
    assertTrue(loaded.get(0) instanceof LayoutsPreference.SystemLayout);
    assertTrue(loaded.get(1) instanceof LayoutsPreference.CustomLayout);
  }

  @Test
  public void load_from_string_invalid_returns_null()
  {
    assertNull(ListGroupPreference.load_from_string("not json", LayoutsPreference.SERIALIZER));
    assertNull(ListGroupPreference.load_from_string(null, LayoutsPreference.SERIALIZER));
    assertNull(ListGroupPreference.load_from_string("[}", LayoutsPreference.SERIALIZER));
  }

  @Test
  public void save_serializes_null_as_system_layout()
  {
    // [null] items match neither [NamedLayout] nor [CustomLayout] and fall
    // back to the "system" representation on reload.
    List<LayoutsPreference.Layout> items = new ArrayList<LayoutsPreference.Layout>();
    items.add(null);
    items.add(new LayoutsPreference.NamedLayout("a"));
    List<LayoutsPreference.Layout> loaded = reload(round_trip(items));
    assertEquals(2, loaded.size());
    assertTrue(loaded.get(0) instanceof LayoutsPreference.SystemLayout);
    assertTrue(loaded.get(1) instanceof LayoutsPreference.NamedLayout);
    assertEquals("a", ((LayoutsPreference.NamedLayout)loaded.get(1)).name);
  }
}