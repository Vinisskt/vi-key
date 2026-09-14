package com.vinisskt.vikey.prefs;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import com.vinisskt.vikey.KeyboardData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** [LayoutsPreference] statics: the layout names and ids coming out of the
    resources and the "system" fallback. */
public class LayoutsPreferenceTest
{
  static Resources resources_with(final String[] names, final TypedArray ids)
  {
    return new Resources(null, null, null)
    {
      @Override public String[] getStringArray(int id) { return names; }
      @Override public TypedArray obtainTypedArray(int id) { return ids; }
    };
  }

  @Before public void reset_static_cache() throws Exception
  {
    set_static(LayoutsPreference.class, "_unsafe_layout_ids_str", null);
    set_static(LayoutsPreference.class, "_unsafe_layout_ids_res", null);
  }

  @After public void restore_static_cache() throws Exception
  {
    // The cache survives this class: leave it clean so other tests that read
    // 'LayoutsPreference' (e.g. Config.refresh) see the pristine defaults.
    set_static(LayoutsPreference.class, "_unsafe_layout_ids_str", null);
    set_static(LayoutsPreference.class, "_unsafe_layout_ids_res", null);
  }

  static void set_static(Class<?> cls, String name, Object value) throws Exception
  {
    Field f = cls.getDeclaredField(name);
    f.setAccessible(true);
    f.set(null, value);
  }

  @Test
  public void get_layout_names_returns_the_resource_names()
  {
    Resources res = resources_with(new String[]{"system", "vim_prog"}, null);
    assertEquals(Arrays.asList("system", "vim_prog"),
        LayoutsPreference.get_layout_names(res));
  }

  @Test
  public void layout_id_of_name_maps_names_into_the_typed_array()
  {
    TypedArray ids = mock(TypedArray.class);
    when(ids.getResourceId(1, 0)).thenReturn(7);
    Resources res = resources_with(new String[]{"system", "vim_prog"}, ids);
    assertEquals(7, LayoutsPreference.layout_id_of_name(res, "vim_prog"));
    assertEquals(-1, LayoutsPreference.layout_id_of_name(res, "unknown"));
  }

  @Test
  public void layout_of_string_falls_back_to_the_system_layout()
  {
    // No real layout id is found so both names resolve to the "system" null.
    TypedArray ids = mock(TypedArray.class);
    Resources res = resources_with(new String[]{"system", "vim_prog"}, ids);
    assertNull(LayoutsPreference.layout_of_string(res, "system"));
    assertNull(LayoutsPreference.layout_of_string(res, "unknown"));
  }

  @Test
  public void load_from_preferences_defaults_to_the_system_layout()
  {
    Resources res = resources_with(new String[]{"system", "vim_prog"}, null);
    List<KeyboardData> layouts =
        LayoutsPreference.load_from_preferences(res, mock(SharedPreferences.class));
    assertEquals(1, layouts.size());
    assertNull(layouts.get(0));
  }
}