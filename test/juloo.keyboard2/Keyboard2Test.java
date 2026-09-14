package com.vinisskt.vikey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.*;

/** [Keyboard2.setTextLayout]/[incrTextLayout]: layout switching and its
    exhaustion guard (a layout cycle over an empty ['layouts'] list used to
    divide by zero). */
public class Keyboard2Test
{
  @Before public void reset_layout_modifier()
  {
    LayoutModifier.globalConfig = null;
    LayoutModifier.bottom_row = null;
    LayoutModifier.number_row_no_symbols = null;
    LayoutModifier.number_row_symbols = null;
    LayoutModifier.num_pad = null;
  }

  static Keyboard2 make_kb(Config config, Keyboard2View view) throws Exception
  {
    // 'LayoutModifier' static state is required by [setTextLayout]'s
    // [current_layout] pass.
    LayoutModifier.globalConfig = config;
    Keyboard2 kb = new Keyboard2();
    set_field(kb, "_config", config);
    set_field(kb, "_keyboard_layout_view", view);
    return kb;
  }

  static void set_field(Object obj, String name, Object value) throws Exception
  {
    Field f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(obj, value);
  }

  static Config config_with_layouts(int current, KeyboardData... layouts)
  {
    Config c = mock(Config.class);
    when(c.get_current_layout()).thenReturn(current);
    c.extra_keys_param = new HashMap<KeyValue, KeyboardData.PreferredPos>();
    c.extra_keys_custom = new HashMap<KeyValue, KeyboardData.PreferredPos>();
    c.show_numpad = false;
    c.add_number_row = false;
    c.split_layout = false;
    List<KeyboardData> list = new ArrayList<KeyboardData>();
    for (KeyboardData layout : layouts)
      list.add(layout);
    c.layouts = list;
    return c;
  }

  /** A Config mock whose current layout really evolves when
      [set_current_layout] is called. */
  static Config config_with_wrapping_layout(int current, KeyboardData... layouts)
  {
    final int[] cur = {current};
    Config c = config_with_layouts(0);
    when(c.get_current_layout()).thenAnswer(new Answer<Integer>() {
      public Integer answer(InvocationOnMock m) { return cur[0]; }
    });
    doAnswer(new Answer<Void>() {
      public Void answer(InvocationOnMock m)
      {
        cur[0] = (Integer)m.getArgument(0);
        return null;
      }
    }).when(c).set_current_layout(anyInt());
    List<KeyboardData> list = new ArrayList<KeyboardData>();
    for (KeyboardData layout : layouts)
      list.add(layout);
    c.layouts = list;
    return c;
  }

  static KeyboardData plain_layout()
  {
    KeyboardData.Key key = new KeyboardData.Key(new KeyValue[]{
        KeyValue.getKeyByName("a"), null, null, null, null, null, null, null,
        null }, null, 0, 1.f, 0.f, null, KeyboardData.Key.Role.Normal);
    KeyboardData.Row row = new KeyboardData.Row(Arrays.asList(key), 1.f, 0.f);
    return new KeyboardData(Arrays.asList(row), 4.f, null, "s", null, "n",
        false, false, false, false);
  }

  @Test
  public void incr_wraps_around_the_layout_cycle() throws Exception
  {
    Config c = config_with_wrapping_layout(0, plain_layout(), plain_layout());
    Keyboard2 kb = make_kb(c, mock(Keyboard2View.class));
    kb.incrTextLayout(1);  // -> 1
    kb.incrTextLayout(1);  // -> 0
    kb.incrTextLayout(-1); // -> 1
    kb.incrTextLayout(-1); // -> 0
    verify(c, times(2)).set_current_layout(1);
    verify(c, times(2)).set_current_layout(0);
  }

  @Test
  public void incr_on_empty_layouts_is_a_noop() throws Exception
  {
    // Regression: an empty layout cycle used to divide by zero.
    Config c = config_with_layouts(0);
    Keyboard2 kb = make_kb(c, mock(Keyboard2View.class));
    kb.incrTextLayout(1);
    kb.incrTextLayout(-1);
    verify(c, never()).set_current_layout(anyInt());
  }

  @Test
  public void incr_on_single_layout_keeps_it() throws Exception
  {
    Config c = config_with_wrapping_layout(0, plain_layout());
    Keyboard2 kb = make_kb(c, mock(Keyboard2View.class));
    kb.incrTextLayout(1);
    kb.incrTextLayout(-1);
    kb.incrTextLayout(1);
    // A single-layout cycle always points back to layout 0.
    verify(c, times(3)).set_current_layout(0);
  }
}