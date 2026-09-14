package com.vinisskt.vikey;

import org.junit.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static org.junit.Assert.*;

/** [ThemeData] tests: the "#rgb"/"#rrggbb"/"#aarrggbb" color parser and the
    Lua table conversion used by [vim.set_theme]. */
public class ThemeDataTest
{
  @Test
  public void parse_color_rgb_expands_each_channel()
  {
    assertEquals(0xFFFFFFFF, (int)ThemeData.parse_color("#fff"));
    assertEquals(0xFF000000, (int)ThemeData.parse_color("#000"));
    assertEquals(0xFFFF0000, (int)ThemeData.parse_color("#f00"));
    assertEquals(0xFF00FF00, (int)ThemeData.parse_color("#0f0"));
    assertEquals(0xFF0000FF, (int)ThemeData.parse_color("#00f"));
  }

  @Test
  public void parse_color_rrggbb()
  {
    assertEquals(0xFF010203, (int)ThemeData.parse_color("#010203"));
    assertEquals(0xFFAABBCC, (int)ThemeData.parse_color("#aabbcc"));
    assertEquals(0xFF000000, (int)ThemeData.parse_color("#000000"));
  }

  @Test
  public void parse_color_aarrggbb()
  {
    assertEquals(0xFF112233, (int)ThemeData.parse_color("#ff112233"));
    assertEquals(0x80112233, (int)ThemeData.parse_color("#80112233"));
    assertEquals(0x00000000, (int)ThemeData.parse_color("#00000000"));
  }

  @Test
  public void parse_color_leading_hash_is_optional()
  {
    assertEquals(0xFFAABBCC, (int)ThemeData.parse_color("aabbcc"));
    assertEquals(0xFFAABBCC, (int)ThemeData.parse_color("#aabbcc"));
  }

  @Test
  public void parse_color_rejects_invalid_input()
  {
    assertNull(ThemeData.parse_color(null));
    assertNull(ThemeData.parse_color(""));
    assertNull(ThemeData.parse_color("#"));
    assertNull(ThemeData.parse_color("abcde"));
    assertNull(ThemeData.parse_color("#abcde"));
    assertNull(ThemeData.parse_color("zzzzzz"));
    assertNull(ThemeData.parse_color("#gggggg"));
    assertNull(ThemeData.parse_color("#aaaaaaaaaa"));
  }

  @Test
  public void value_returns_default_when_no_override()
  {
    assertEquals(5, ThemeData.EMPTY.value(5, (Integer)null));
    assertEquals(2, ThemeData.EMPTY.value(5, 2));
    assertEquals(1.5f, ThemeData.EMPTY.value(1.5f, (Float)null), 1e-5f);
    assertEquals(2.5f, ThemeData.EMPTY.value(1.5f, 2.5f), 1e-5f);
    assertTrue(ThemeData.EMPTY.value(true, null));
    assertFalse(ThemeData.EMPTY.value(true, false));
  }

  @Test
  public void from_lua_table_non_table_returns_null()
  {
    assertNull(ThemeData.from_lua_table(LuaValue.valueOf("nope")));
    assertNull(ThemeData.from_lua_table(LuaValue.NIL));
  }

  @Test
  public void from_lua_table_parses_fields()
  {
    LuaTable t = LuaValue.tableOf();
    t.set("colorKeyboard", LuaValue.valueOf("#ffffff"));
    t.set("colorKey", LuaValue.valueOf("#102030"));
    t.set("keyBorderRadius", LuaValue.valueOf(2.5f));
    t.set("windowLightNavigationBar", LuaValue.valueOf(true));
    ThemeData d = ThemeData.from_lua_table(t);
    assertEquals(0xFFFFFFFF, (int)d.colorKeyboard);
    assertEquals(0xFF102030, (int)d.colorKey);
    assertEquals(2.5f, (float)d.keyBorderRadius, 1e-5f);
    assertEquals(true, (boolean)d.lightNavBar);
    assertNull(d.subLabelColor);
  }

  @Test(expected = IllegalArgumentException.class)
  public void from_lua_table_invalid_color_throws()
  {
    LuaTable t = LuaValue.tableOf();
    t.set("colorKeyboard", LuaValue.valueOf("notacolor!"));
    ThemeData.from_lua_table(t);
  }

  @Test
  public void set_active_bumps_revision()
  {
    ThemeData before_active = ThemeData.active();
    int rev = ThemeData.revision();
    ThemeData d = new ThemeData();
    try
    {
      ThemeData.set_active(d);
      assertSame(d, ThemeData.active());
      assertEquals(rev + 1, ThemeData.revision());
    }
    finally
    {
      ThemeData.set_active(before_active);
    }
  }
}