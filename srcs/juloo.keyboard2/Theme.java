package com.vinisskt.vikey;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class Theme
{
  public final int colorKeyboard;
  // Key colors
  public final int colorKey;
  public final int colorKeyActivated;
  public final int colorKeyAction;
  public final int colorKeySpaceBar;

  // Optional keyboard background gradient
  public final boolean hasKeyboardGradient;
  public final int keyboardGradientStart;
  public final int keyboardGradientEnd;

  // Label colors
  public final int lockedColor;
  public final int activatedColor;
  public final int pressedColor;
  public final int labelColor;
  public final int subLabelColor;
  public final int secondaryLabelColor;
  public final int greyedLabelColor;

  // Key borders
  public final float keyBorderRadius;
  public final float keyBorderWidth;
  public final float keyBorderWidthActivated;
  public final float keyBorderWidthAction;
  public final float keyBorderWidthSpaceBar;
  public final int keyBorderColorLeft;
  public final int keyBorderColorTop;
  public final int keyBorderColorRight;
  public final int keyBorderColorBottom;

  public final int colorNavBar;
  public final boolean isLightNavBar;

  public Theme(Context context, AttributeSet attrs)
  {
    getKeyFont(context); // _key_font will be accessed
    getSpecialFont(context); // _special_font will be accessed
    // [tv] merges the active Lua override with the styled defaults: fields
    // from the override win, everything else falls back to the "Gruvbox"
    // style values read below.
    ThemeData tv = ThemeData.active();
    if (tv == null)
      tv = ThemeData.EMPTY;
    TypedArray s = context.getTheme().obtainStyledAttributes(attrs, R.styleable.keyboard, 0, 0);
    colorKeyboard = tv.value(
        s.getColor(R.styleable.keyboard_colorKeyboard, 0), tv.colorKeyboard);
    hasKeyboardGradient = (tv.keyboardGradientStart != null
        && tv.keyboardGradientEnd != null)
      || (s.hasValue(R.styleable.keyboard_keyboardGradientStart)
          && s.hasValue(R.styleable.keyboard_keyboardGradientEnd));
    keyboardGradientStart = tv.value(
        s.getColor(R.styleable.keyboard_keyboardGradientStart, 0), tv.keyboardGradientStart);
    keyboardGradientEnd = tv.value(
        s.getColor(R.styleable.keyboard_keyboardGradientEnd, 0), tv.keyboardGradientEnd);
    colorKey = tv.value(
        s.getColor(R.styleable.keyboard_colorKey, 0), tv.colorKey);
    colorKeyActivated = tv.value(
        s.getColor(R.styleable.keyboard_colorKeyActivated, 0), tv.colorKeyActivated);
    colorKeyAction = tv.value(
        s.getColor(R.styleable.keyboard_colorKeyAction, colorKey), tv.colorKeyAction);
    colorKeySpaceBar = tv.value(
        s.getColor(R.styleable.keyboard_colorKeySpaceBar, colorKey), tv.colorKeySpaceBar);
    colorNavBar = tv.value(
        s.getColor(R.styleable.keyboard_navigationBarColor, 0), tv.navBarColor);
    isLightNavBar = tv.value(
        s.getBoolean(R.styleable.keyboard_windowLightNavigationBar, false), tv.lightNavBar);
    labelColor = tv.value(
        s.getColor(R.styleable.keyboard_colorLabel, 0), tv.labelColor);
    activatedColor = tv.value(
        s.getColor(R.styleable.keyboard_colorLabelActivated, 0), tv.labelActivated);
    pressedColor = tv.value(
        s.getColor(R.styleable.keyboard_colorLabelPressed, labelColor), tv.labelPressed);
    lockedColor = tv.value(
        s.getColor(R.styleable.keyboard_colorLabelLocked, 0), tv.labelLocked);
    subLabelColor = tv.value(
        s.getColor(R.styleable.keyboard_colorSubLabel, 0), tv.subLabelColor);
    float sec_dim = s.getFloat(R.styleable.keyboard_secondaryDimming, 0.25f);
    float grey_dim = s.getFloat(R.styleable.keyboard_greyedDimming, 0.5f);
    sec_dim = tv.value(sec_dim, tv.secondaryDimming);
    grey_dim = tv.value(grey_dim, tv.greyedDimming);
    secondaryLabelColor = adjustLight(labelColor, sec_dim);
    greyedLabelColor = adjustLight(labelColor, grey_dim);
    float density = context.getResources().getDisplayMetrics().density;
    keyBorderRadius = dp_value(s, R.styleable.keyboard_keyBorderRadius, 0, tv, tv.keyBorderRadius, density);
    keyBorderWidth = dp_value(s, R.styleable.keyboard_keyBorderWidth, 0, tv, tv.keyBorderWidth, density);
    keyBorderWidthActivated = dp_value(s, R.styleable.keyboard_keyBorderWidthActivated, 0, tv, tv.keyBorderWidthActivated, density);
    keyBorderWidthAction = dp_value(s, R.styleable.keyboard_keyBorderWidthAction, 0, tv, tv.keyBorderWidthAction, density);
    keyBorderWidthSpaceBar = dp_value(s, R.styleable.keyboard_keyBorderWidthSpaceBar, 0, tv, tv.keyBorderWidthSpaceBar, density);
    keyBorderColorLeft = tv.value(
        s.getColor(R.styleable.keyboard_keyBorderColorLeft, colorKey), tv.keyBorderColorLeft);
    keyBorderColorTop = tv.value(
        s.getColor(R.styleable.keyboard_keyBorderColorTop, colorKey), tv.keyBorderColorTop);
    keyBorderColorRight = tv.value(
        s.getColor(R.styleable.keyboard_keyBorderColorRight, colorKey), tv.keyBorderColorRight);
    keyBorderColorBottom = tv.value(
        s.getColor(R.styleable.keyboard_keyBorderColorBottom, colorKey), tv.keyBorderColorBottom);
    s.recycle();
  }

  /** A dimension attribute value (in px), or the Lua override converted from
      dp to px when present. */
  static float dp_value(TypedArray s, int index, float def, ThemeData tv,
      Float override, float density)
  {
    if (override != null)
      return override.floatValue() * density;
    return s.getDimension(index, def);
  }

  /** Interpolate the 'value' component toward its opposite by 'alpha'. */
  static int adjustLight(int color, float alpha)
  {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    float v = hsv[2];
    hsv[2] = alpha - (2 * alpha - 1) * v;
    return Color.HSVToColor(hsv);
  }

  Paint initIndicationPaint(Paint.Align align, Typeface font)
  {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextAlign(align);
    if (font != null)
      paint.setTypeface(font);
    return (paint);
  }

  static Typeface _key_font = null;
  static Typeface _special_font = null;

  static public Typeface getKeyFont(Context context)
  {
    if (_key_font == null)
      _key_font = Typeface.createFromAsset(context.getAssets(), "cascadia_mono.ttf");
    return _key_font;
  }

  static public Typeface getSpecialFont(Context context)
  {
    if (_special_font == null)
      _special_font = Typeface.createFromAsset(context.getAssets(), "special_font.ttf");
    return _special_font;
  }

  public static final class Computed
  {
    public final float vertical_margin;
    public final float horizontal_margin;
    public final float margin_top;
    public final float margin_left;
    public final float row_height;
    public final Paint keyboard_background_paint;
    public final Paint indication_paint;

    public final Key key;
    public final Key key_activated;
    public final Key key_action;
    public final Key key_space_bar;
    public final Key key_suggestion;

    public Computed(Theme theme, Config config, float keyWidth, KeyboardData layout)
    {
      // Make sure that the layout isn't higher than the screen. Take the
      // height of the candidates view into account.
      row_height = Math.min(config.keyboard_rows_height_pixels,
          (config.screenHeightPixels - config.keyboard_rows_height_pixels) / layout.keysHeight);
      vertical_margin = config.key_vertical_margin * row_height;
      horizontal_margin = config.key_horizontal_margin * keyWidth;
      // Add half of the key margin on the left and on the top as it's also
      // added on the right and on the bottom of every keys.
      margin_top = config.marginTop + vertical_margin / 2;
      margin_left = horizontal_margin / 2;
      keyboard_background_paint = init_keyboard_background_paint(theme, margin_top + row_height * layout.keysHeight + config.margin_bottom);
      key = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Normal);
      key_action = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Action);
      key_space_bar = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Space_bar);
      key_activated = new Key(theme, config, keyWidth, true, KeyboardData.Key.Role.Normal);
      key_suggestion = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Suggestion);
      indication_paint = init_label_paint(config, null);
      indication_paint.setColor(theme.subLabelColor);
    }

    static Paint init_keyboard_background_paint(Theme theme, float height)
    {
      if (!theme.hasKeyboardGradient)
        return null;

      Paint p = new Paint();
      p.setShader(new LinearGradient(
              0,
              0,
              0,
              height,
              theme.keyboardGradientStart,
              theme.keyboardGradientEnd,
              Shader.TileMode.CLAMP));
      return p;
    }

    public static final class Key
    {
      public final Paint bg_paint = new Paint();
      public final Paint border_left_paint;
      public final Paint border_top_paint;
      public final Paint border_right_paint;
      public final Paint border_bottom_paint;
      public final Paint pressed_paint;
      public final float border_width;
      public final float border_radius;
      final Paint _label_paint;
      final Paint _special_label_paint;
      final Paint _sublabel_paint;
      final Paint _special_sublabel_paint;
      final int _label_alpha_bits;

      public Key(Theme theme, Config config, float keyWidth, boolean activated,
          KeyboardData.Key.Role role)
      {
        border_radius = config.borderConfig ? config.customBorderRadius * keyWidth : theme.keyBorderRadius;
        int bg_color;
        if (activated)
        {
          bg_color = theme.colorKeyActivated;
          border_width = theme.keyBorderWidthActivated;
          bg_paint.setAlpha(config.keyActivatedOpacity);
        }
        else
        {
          switch (role)
          {
            case Action:
              bg_color = theme.colorKeyAction;
              border_width = theme.keyBorderWidthAction;
              break;
            case Space_bar:
              bg_color = theme.colorKeySpaceBar;
              border_width = theme.keyBorderWidthSpaceBar;
              break;
            case Suggestion:
              bg_color = 0;
              border_width = 0;
              break;
            default:
              bg_color = theme.colorKey;
              border_width = config.borderConfig ? config.customBorderLineWidth : theme.keyBorderWidth;
              break;
          }
          bg_paint.setAlpha(config.keyOpacity);
        }
        bg_paint.setColor(bg_color);
        border_left_paint = init_border_paint(config, border_width, theme.keyBorderColorLeft);
        border_top_paint = init_border_paint(config, border_width, theme.keyBorderColorTop);
        border_right_paint = init_border_paint(config, border_width, theme.keyBorderColorRight);
        border_bottom_paint = init_border_paint(config, border_width, theme.keyBorderColorBottom);
        pressed_paint = init_border_paint(config, Math.max(border_width, 2.f), theme.pressedColor);
        pressed_paint.setAlpha(64);
        _label_paint = init_label_paint(config, _key_font);
        _special_label_paint = init_label_paint(config, _special_font);
        _sublabel_paint = init_label_paint(config, _key_font);
        _special_sublabel_paint = init_label_paint(config, _special_font);
        _label_alpha_bits = (config.labelBrightness & 0xFF) << 24;
      }

      public Paint label_paint(boolean special_font, int color, float text_size)
      {
        Paint p = special_font ? _special_label_paint : _label_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        return p;
      }

      public Paint sublabel_paint(boolean special_font, int color, float text_size, Paint.Align align)
      {
        Paint p = special_font ? _special_sublabel_paint : _sublabel_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        p.setTextAlign(align);
        return p;
      }
    }

    static Paint init_border_paint(Config config, float border_width, int color)
    {
      Paint p = new Paint();
      p.setAlpha(config.keyOpacity);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(border_width);
      p.setColor(color);
      return p;
    }

    static Paint init_label_paint(Config config, Typeface font)
    {
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setTextAlign(Paint.Align.CENTER);
      if (font != null)
        p.setTypeface(font);
      return p;
    }
  }
}
