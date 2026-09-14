package com.vinisskt.vikey.prefs;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.vinisskt.vikey.KeyboardData;
import com.vinisskt.vikey.KeyValue;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pure [ExtraKeysPreference] statics and [CustomExtraKeysPreference.get]:
    the default/enabled keys computed from the shared preferences. */
public class ExtraKeysPreferenceTest
{
  @Test
  public void default_checked_keys_enabled_by_default()
  {
    assertTrue(ExtraKeysPreference.default_checked("voice_typing"));
    assertTrue(ExtraKeysPreference.default_checked("change_method"));
    assertTrue(ExtraKeysPreference.default_checked("switch_clipboard"));
    assertTrue(ExtraKeysPreference.default_checked("compose"));
    assertTrue(ExtraKeysPreference.default_checked("tab"));
    assertTrue(ExtraKeysPreference.default_checked("esc"));
    assertTrue(ExtraKeysPreference.default_checked("f11_placeholder"));
    assertTrue(ExtraKeysPreference.default_checked("f12_placeholder"));
  }

  @Test
  public void default_checked_keys_disabled_by_default()
  {
    assertFalse(ExtraKeysPreference.default_checked("copy"));
    assertFalse(ExtraKeysPreference.default_checked("capslock"));
    assertFalse(ExtraKeysPreference.default_checked("€"));
  }

  @Test
  public void pref_key_of_key_name_is_prefixed()
  {
    assertEquals("extra_key_tab", ExtraKeysPreference.pref_key_of_key_name("tab"));
    assertEquals("extra_key_copy", ExtraKeysPreference.pref_key_of_key_name("copy"));
  }

  @Test
  public void key_title_names_the_function_keys()
  {
    assertEquals("F11", ExtraKeysPreference.key_title("f11_placeholder", KeyValue.getKeyByName("a")));
    assertEquals("F12", ExtraKeysPreference.key_title("f12_placeholder", KeyValue.getKeyByName("a")));
    assertEquals("a", ExtraKeysPreference.key_title("a", KeyValue.getKeyByName("a")));
  }

  @Test
  public void format_key_combination_joins_keys()
  {
    assertEquals("a + b + c", ExtraKeysPreference.format_key_combination(new String[]{"a", "b", "c"}));
    assertEquals("a", ExtraKeysPreference.format_key_combination(new String[]{"a"}));
  }

  @Test
  public void key_preferred_pos_places_action_keys()
  {
    KeyboardData.PreferredPos copy = ExtraKeysPreference.key_preferred_pos("copy");
    assertNotNull(copy);
    assertEquals(KeyValue.getKeyByName("c"), copy.next_to);
    assertEquals(5, copy.positions.length);
    assertSame(KeyboardData.PreferredPos.DEFAULT,
        ExtraKeysPreference.key_preferred_pos("unknown"));
  }

  @Test
  public void mk_preferred_pos_tunes_direction_and_neighbour()
  {
    KeyboardData.PreferredPos br =
        ExtraKeysPreference.mk_preferred_pos("c", 2, 3, true);
    assertEquals(KeyValue.getKeyByName("c"), br.next_to);
    assertEquals(2, br.positions[0].row);
    assertEquals(3, br.positions[0].col);
    // Prefer bottom-right then bottom-left.
    assertEquals(4, br.positions[0].dir);
    assertEquals(3, br.positions[1].dir);

    KeyboardData.PreferredPos bl =
        ExtraKeysPreference.mk_preferred_pos(null, 1, 2, false);
    assertNull(bl.next_to);
    assertEquals(3, bl.positions[0].dir);
    assertEquals(4, bl.positions[1].dir);
  }

  @Test
  public void get_extra_keys_reads_the_boolean_preferences()
  {
    SharedPreferences prefs = mock(SharedPreferences.class);
    // 'esc', 'copy' and 'compose' are enabled by pref; 'tab' is disabled by
    // its pref even though the default is on.
    when(prefs.getBoolean("extra_key_esc", true)).thenReturn(true);
    when(prefs.getBoolean("extra_key_copy", false)).thenReturn(true);
    when(prefs.getBoolean("extra_key_compose", true)).thenReturn(true);
    when(prefs.getBoolean("extra_key_tab", true)).thenReturn(false);
    Map<KeyValue, KeyboardData.PreferredPos> ks =
        ExtraKeysPreference.get_extra_keys(prefs);
    assertTrue(ks.containsKey(KeyValue.getKeyByName("copy")));
    assertTrue(ks.containsKey(KeyValue.getKeyByName("esc")));
    assertTrue(ks.containsKey(KeyValue.getKeyByName("compose")));
    assertFalse(ks.containsKey(KeyValue.getKeyByName("tab")));
  }

  @Test
  public void custom_get_returns_empty_when_unset()
  {
    SharedPreferences prefs = mock(SharedPreferences.class);
    assertTrue(CustomExtraKeysPreference.get(prefs).isEmpty());
  }

  @Test
  public void custom_get_parses_the_stored_key_names()
  {
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getString("custom_extra_keys", null)).thenReturn("[\"a\",\"b\"]");
    Map<KeyValue, KeyboardData.PreferredPos> ks =
        CustomExtraKeysPreference.get(prefs);
    assertEquals(2, ks.size());
    assertTrue(ks.containsKey(KeyValue.getKeyByName("a")));
    assertTrue(ks.containsKey(KeyValue.getKeyByName("b")));
    assertSame(KeyboardData.PreferredPos.DEFAULT,
        ks.get(KeyValue.getKeyByName("a")));
  }

  @Test
  public void custom_get_is_empty_on_a_corrupt_store()
  {
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getString("custom_extra_keys", null)).thenReturn("not json");
    assertTrue(CustomExtraKeysPreference.get(prefs).isEmpty());
  }

  @Test
  public void string_serializer_round_trips()
  {
    List<String> items = Arrays.asList("a", "bå");
    ListGroupPreference.Serializer<String> s =
        new ListGroupPreference.StringSerializer();
    String stored = ListGroupPreference.save_to_string(items, s);
    assertEquals(items, ListGroupPreference.load_from_string(stored, s));
  }
}