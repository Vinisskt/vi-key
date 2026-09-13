package com.vinisskt.vikey;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** A partial set of keyboard theme overrides defined from a Lua script (see
    [vim.theme] in [LuaEngine]) and applied through [vim.set_theme]. Fields are
    [null] when the default "Gruvbox" style value should be kept; [active] is
    the currently applied override ([null] for the built-in theme). */
final class ThemeData
{
  static volatile ThemeData _active = null;
  static int _revision = 0;

  /** An override with no field set: [value] returns the default for every
      attribute. Used to merge the styled defaults when no theme applies. */
  static final ThemeData EMPTY = new ThemeData();

  Integer colorKeyboard;
  Integer colorKey;
  Integer colorKeyActivated;
  Integer colorKeyAction;
  Integer colorKeySpaceBar;
  Integer keyboardGradientStart;
  Integer keyboardGradientEnd;
  Integer labelColor;
  Integer labelPressed;
  Integer labelActivated;
  Integer labelLocked;
  Integer subLabelColor;
  Float secondaryDimming;
  Float greyedDimming;
  Float keyBorderRadius;
  Float keyBorderWidth;
  Float keyBorderWidthActivated;
  Float keyBorderWidthAction;
  Float keyBorderWidthSpaceBar;
  Integer keyBorderColorLeft;
  Integer keyBorderColorTop;
  Integer keyBorderColorRight;
  Integer keyBorderColorBottom;
  Integer navBarColor;
  Boolean lightNavBar;

  /** The currently applied override, or [null] for the built-in theme. */
  static ThemeData active()
  {
    return _active;
  }

  /** Bumped every time [set_active] is called; the keyboard view is recreated
      when the revision changes. */
  static int revision()
  {
    return _revision;
  }

  static void set_active(ThemeData d)
  {
    _active = d;
    _revision++;
  }

  int value(int def, Integer override)
  {
    return (override == null) ? def : override.intValue();
  }

  float value(float def, Float override)
  {
    return (override == null) ? def : override.floatValue();
  }

  boolean value(boolean def, Boolean override)
  {
    return (override == null) ? def : override.booleanValue();
  }

  /** Parse a theme definition table into a [ThemeData]. Returns [null] for a
      non-table argument. Throws [IllegalArgumentException] on an invalid
      color. */
  static ThemeData from_lua_table(LuaValue v)
  {
    if (!v.istable())
      return null;
    LuaTable t = v.checktable();
    ThemeData d = new ThemeData();
    d.colorKeyboard = color_opt(t, "colorKeyboard");
    d.colorKey = color_opt(t, "colorKey");
    d.colorKeyActivated = color_opt(t, "colorKeyActivated");
    d.colorKeyAction = color_opt(t, "colorKeyAction");
    d.colorKeySpaceBar = color_opt(t, "colorKeySpaceBar");
    d.keyboardGradientStart = color_opt(t, "keyboardGradientStart");
    d.keyboardGradientEnd = color_opt(t, "keyboardGradientEnd");
    d.labelColor = color_opt(t, "colorLabel");
    d.labelPressed = color_opt(t, "colorLabelPressed");
    d.labelActivated = color_opt(t, "colorLabelActivated");
    d.labelLocked = color_opt(t, "colorLabelLocked");
    d.subLabelColor = color_opt(t, "colorSubLabel");
    d.secondaryDimming = float_opt(t, "secondaryDimming");
    d.greyedDimming = float_opt(t, "greyedDimming");
    d.keyBorderRadius = float_opt(t, "keyBorderRadius");
    d.keyBorderWidth = float_opt(t, "keyBorderWidth");
    d.keyBorderWidthActivated = float_opt(t, "keyBorderWidthActivated");
    d.keyBorderWidthAction = float_opt(t, "keyBorderWidthAction");
    d.keyBorderWidthSpaceBar = float_opt(t, "keyBorderWidthSpaceBar");
    d.keyBorderColorLeft = color_opt(t, "keyBorderColorLeft");
    d.keyBorderColorTop = color_opt(t, "keyBorderColorTop");
    d.keyBorderColorRight = color_opt(t, "keyBorderColorRight");
    d.keyBorderColorBottom = color_opt(t, "keyBorderColorBottom");
    d.navBarColor = color_opt(t, "navigationBarColor");
    d.lightNavBar = bool_opt(t, "windowLightNavigationBar");
    return d;
  }

  /** The table value for [name], or [null] when absent. Throws on an invalid
      color. */
  static Integer color_opt(LuaTable t, String name)
  {
    LuaValue v = t.get(name);
    if (v.isnil())
      return null;
    if (v.isnumber())
      return v.toint();
    Integer c = parse_color(v.tojstring());
    if (c == null)
      throw new IllegalArgumentException("invalid color '" + v.tojstring()
          + "' for " + name);
    return c;
  }

  /** Parse "#rgb", "#rrggbb" or "#aarrggbb" (a bare leading "#" is optional).
      Returns [null] when [s] is not a valid color. */
  static Integer parse_color(String s)
  {
    String hex = s.startsWith("#") ? s.substring(1) : s;
    int len = hex.length();
    if (len == 3 || len == 6 || len == 8)
    {
      try
      {
        long v = Long.parseLong(hex, 16);
        if (len == 3)
        {
          int r = (int)((v >> 8) & 0xF) * 0x11;
          int g = (int)((v >> 4) & 0xF) * 0x11;
          int b = (int)(v & 0xF) * 0x11;
          return 0xFF000000 | r << 16 | g << 8 | b;
        }
        if (len == 6)
          return 0xFF000000 | (int)v;
        return (int)v;
      }
      catch (NumberFormatException ex) {}
    }
    return null;
  }

  static Float float_opt(LuaTable t, String name)
  {
    LuaValue v = t.get(name);
    return v.isnil() ? null : v.tofloat();
  }

  static Boolean bool_opt(LuaTable t, String name)
  {
    LuaValue v = t.get(name);
    return v.isnil() ? null : v.toboolean();
  }
}