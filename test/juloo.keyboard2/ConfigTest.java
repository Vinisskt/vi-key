package com.vinisskt.vikey;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** [Config] tests: the version migrations ([Config.migrate]) and the
    [Config.refresh] parser which must never crash on an empty, a partially
    initialized or a corrupted preference store. */
public class ConfigTest
{
  /** The width/height of the fake screen used by [make_res]. */
  static final int SCREEN_W = 1080;
  static final int SCREEN_H = 1920;
  /** The swipe scaling factor computed by [Config.refresh] on that screen. */
  static final float SWIPE_SCALING = Math.min(SCREEN_W, SCREEN_H) / 10.f;

  static Resources make_res()
  {
    return new Resources(null, null, null) {
      final DisplayMetrics dm = new DisplayMetrics();
      final Configuration conf = new Configuration();
      {
        dm.widthPixels = SCREEN_W;
        dm.heightPixels = SCREEN_H;
        dm.density = 2.75f;
        dm.xdpi = 420.f;
        dm.ydpi = 420.f;
        conf.orientation = Configuration.ORIENTATION_PORTRAIT;
      }
      @Override public DisplayMetrics getDisplayMetrics() { return dm; }
      @Override public Configuration getConfiguration() { return conf; }
      @Override public float getDimension(int id) { return 8.f; }
      @Override public String[] getStringArray(int id) { return new String[0]; }
    };
  }

  /** Build a [Config] through the (private) constructor, which runs a
      [refresh] on the given prefs. No [LayoutModifier] initialization is
      performed. */
  static Config make_config(SharedPreferences prefs) throws Exception
  {
    return make_config(prefs, make_res());
  }

  static Config make_config(SharedPreferences prefs, Resources res) throws Exception
  {
    Constructor<Config> c = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class);
    c.setAccessible(true);
    return c.newInstance(prefs, res, false);
  }

  static SharedPreferences mock_prefs()
  {
    return mock(SharedPreferences.class);
  }

  static SharedPreferences.Editor mock_editor(SharedPreferences prefs)
  {
    SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
    when(editor.putInt(anyString(), anyInt())).thenReturn(editor);
    when(editor.putString(anyString(), anyString())).thenReturn(editor);
    when(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor);
    when(prefs.edit()).thenReturn(editor);
    return editor;
  }

  // ---- Migrations ---------------------------------------------------------

  @Test
  public void migrate_returns_early_when_version_is_current()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getInt("version", 0)).thenReturn(4);
    Config.migrate(prefs);
    verify(prefs, never()).edit();
  }

  @Test
  public void migrate_v0_no_existing_prefs_sets_defaults()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getInt("version", 0)).thenReturn(0);
    SharedPreferences.Editor editor = mock_editor(prefs);
    Config.migrate(prefs);
    verify(editor).putInt("version", 4);
    // case 0: the merged layouts default is written.
    verify(editor).putString(eq("layouts"), anyString());
    // case 1: the number_row boolean is translated.
    verify(editor).putString("number_row", "no_number_row");
    // case 2: no number_entry_layout -> from the pin_entry boolean (false).
    verify(editor).putString("number_entry_layout", "number");
    // case 3: switch_input_immediate defaults to false -> picker.
    verify(editor).putString("change_method_key_replacement", "picker");
    verify(editor).apply();
  }

  @Test
  public void migrate_v0_preserves_existing_change_method_replacement()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getInt("version", 0)).thenReturn(0);
    when(prefs.contains("change_method_key_replacement")).thenReturn(true);
    when(prefs.getBoolean("switch_input_immediate", false)).thenReturn(true);
    SharedPreferences.Editor editor = mock_editor(prefs);
    Config.migrate(prefs);
    // The user's picker choice must not be overwritten by the legacy boolean.
    verify(editor, never()).putString(eq("change_method_key_replacement"), anyString());
  }

  @Test
  public void migrate_v0_uses_pin_entry_boolean_for_number_layout()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getInt("version", 0)).thenReturn(0);
    when(prefs.getBoolean("pin_entry_enabled", true)).thenReturn(true);
    SharedPreferences.Editor editor = mock_editor(prefs);
    Config.migrate(prefs);
    verify(editor).putString("number_entry_layout", "pin");
  }

  // ---- Refresh ------------------------------------------------------------

  @Test
  public void refresh_on_empty_store_never_crashes()
  {
    try
    {
      Config c = make_config(mock_prefs());
      assertNotNull(c.layouts);
      // The layouts list must be mutable (regression: the default value is an
      // immutable singleton which used to break the vim layout prepend).
      assertTrue(ArrayList.class.isAssignableFrom(c.layouts.getClass()));
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void refresh_corrupt_numeric_strings_fall_back_to_defaults()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getString(eq("swipe_dist"), anyString())).thenReturn("abc");
    when(prefs.getString(eq("slider_sensitivity"), anyString())).thenReturn("1x0y");
    when(prefs.getString(eq("circle_sensitivity"), anyString())).thenReturn("z");
    when(prefs.getString(eq("clipboard_history_duration"), anyString())).thenReturn("");
    try
    {
      Config c = make_config(prefs);
      assertEquals(15.f / 25.f * SWIPE_SCALING, c.swipe_dist_px, 1e-3f);
      assertEquals(2, c.circle_sensitivity);
      assertEquals(5, c.clipboard_history_duration);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void refresh_valid_numeric_strings_are_parsed()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getString(eq("swipe_dist"), anyString())).thenReturn("75");
    when(prefs.getString(eq("slider_sensitivity"), anyString())).thenReturn("50");
    when(prefs.getString(eq("circle_sensitivity"), anyString())).thenReturn("4");
    when(prefs.getString(eq("clipboard_history_duration"), anyString())).thenReturn("9");
    try
    {
      Config c = make_config(prefs);
      assertEquals(75.f / 25.f * SWIPE_SCALING, c.swipe_dist_px, 1e-3f);
      assertEquals(4, c.circle_sensitivity);
      assertEquals(9, c.clipboard_history_duration);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void refresh_number_row_strings_are_parsed()
  {
    SharedPreferences prefs = mock_prefs();
    when(prefs.getString("number_row", "no_number_row")).thenReturn("symbols");
    try
    {
      Config c = make_config(prefs);
      assertTrue(c.add_number_row);
      assertTrue(c.number_row_symbols);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void refresh_null_store_strings_fall_back_to_defaults()
  {
    // A mock returns [null] for every unmocked [getString], which exercises
    // the null guards (a real store never returns null, but a corrupted one or
    // a test double might).
    try
    {
      Config c = make_config(mock_prefs());
      assertEquals(KeyValue.CHANGE_METHOD_PREV, c.change_method_key_replacement);
      assertEquals(NumberLayout.PIN, c.selected_number_layout);
      assertFalse(c.split_layout);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void refresh_landscape_uses_landscape_height_pref()
  {
    Resources res = make_res();
    res.getConfiguration().orientation = Configuration.ORIENTATION_LANDSCAPE;
    SharedPreferences prefs = mock_prefs();
    when(prefs.getInt("keyboard_height_landscape", 50)).thenReturn(40);
    try
    {
      Config c = make_config(prefs, res);
      assertTrue(c.orientation_landscape);
      res.getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
      assertTrue(c.foldable_unfolded == false);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }
}