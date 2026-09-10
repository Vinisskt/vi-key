package com.vinisskt.vikey;

import android.content.res.Resources;
import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;

/** [EditorConfig.refresh] recomputes per-editor settings from an
    [EditorInfo]: action & enter key replacements, numeric layout detection,
    the setSelection fallback and autocapitalisation settings. */
public class EditorConfigTest
{
  static EditorConfig refresh(int inputType, int options)
  {
    return refresh(inputType, options, null);
  }

  static EditorConfig refresh(int inputType, int options, Resources res)
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = inputType;
    info.imeOptions = options;
    info.packageName = "com.example.editor";
    ec.refresh(info, res);
    return ec;
  }

  static final Resources res = new Resources(null, null, null) {
    @Override public String getString(int id) { return "action"; }
  };

  @Test
  public void action_label_sets_action_key()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT;
    info.packageName = "com.example";
    info.actionLabel = "Send";
    info.actionId = 7;
    ec.refresh(info, null);
    assertEquals(7, ec.actionId);
    assertNotNull(ec.action_key_replacement);
    assertTrue(ec.action_key_replacement.toString().contains("Send"));
    assertNull(ec.enter_key_replacement);
  }

  @Test
  public void null_input_type_disables_selection_mode()
  {
    EditorConfig ec = refresh(InputType.TYPE_NULL, 0);
    assertFalse(ec.selection_mode_enabled);
  }

  @Test
  public void text_field_enables_selection_mode()
  {
    EditorConfig ec = refresh(InputType.TYPE_CLASS_TEXT, 0);
    assertTrue(ec.selection_mode_enabled);
  }

  @Test
  public void numeric_layouts_detected()
  {
    assertTrue(refresh(InputType.TYPE_CLASS_NUMBER, 0).numeric_layout);
    assertTrue(refresh(InputType.TYPE_CLASS_PHONE, 0).numeric_layout);
    assertTrue(refresh(InputType.TYPE_CLASS_DATETIME, 0).numeric_layout);
    assertFalse(refresh(InputType.TYPE_CLASS_TEXT, 0).numeric_layout);
  }

  @Test
  public void password_variation_forces_cursor_fallback()
  {
    int password = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    assertTrue(refresh(password, 0).should_move_cursor_force_fallback);
    assertFalse(refresh(InputType.TYPE_CLASS_TEXT, 0).should_move_cursor_force_fallback);
  }

  @Test
  public void godot_editor_forces_cursor_fallback()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT;
    info.packageName = "org.godotengine.editor.android";
    ec.refresh(info, null);
    assertTrue(ec.should_move_cursor_force_fallback);
  }

  @Test
  public void caps_mode_and_initial_enabled()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT | TextUtils.CAP_MODE_SENTENCES
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    info.initialCapsMode = 2;
    info.packageName = "com.example";
    ec.refresh(info, null);
    assertTrue(ec.caps_initially_enabled);
    assertTrue(ec.caps_initially_updated);
  }

  @Test
  public void caps_not_updated_for_password_variation()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    info.packageName = "com.example";
    ec.refresh(info, null);
    assertFalse(ec.caps_initially_updated);
  }

  @Test
  public void selection_initials_kept()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT;
    info.initialSelStart = 3;
    info.initialSelEnd = 5;
    info.packageName = "com.example";
    ec.refresh(info, null);
    assertEquals(3, ec.initial_sel_start);
    assertEquals(5, ec.initial_sel_end);
  }

  @Test
  public void action_label_with_no_enter_action_keeps_enter_key()
  {
    // imeAction DONE maps to a label via resources; NO_ENTER_ACTION swaps back
    // to a plain ENTER without an enter replacement.
    EditorConfig ec = refresh(InputType.TYPE_CLASS_TEXT,
        EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION, res);
    assertNotNull(ec.action_key_replacement);
    assertNull(ec.enter_key_replacement);
  }

  @Test
  public void action_label_swaps_enter_when_no_flag()
  {
    // imeAction DONE maps to a label; without the NO_ENTER_ACTION flag the
    // enter and action keys are swapped.
    EditorConfig ec = refresh(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE, res);
    assertNotNull(ec.enter_key_replacement);
    assertNotNull(ec.action_key_replacement);
  }

  @Test
  public void caps_not_updated_for_uri_variation()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
    info.packageName = "com.example";
    ec.refresh(info, null);
    assertFalse(ec.caps_initially_updated);
  }

  @Test
  public void caps_not_updated_for_phone_class()
  {
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_PHONE;
    info.packageName = "com.example";
    ec.refresh(info, null);
    assertFalse(ec.caps_initially_updated);
  }

  @Test
  public void enter_replacement_set_for_next_action()
  {
    // IME_ACTION_NEXT maps to a label, so the enter key is replaced.
    assertNotNull(refresh(InputType.TYPE_CLASS_TEXT,
        EditorInfo.IME_ACTION_NEXT, res).enter_key_replacement);
  }

  @Test
  public void password_numeric_input_type_forces_fallback()
  {
    int variation = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    assertTrue(refresh(variation, 0).should_move_cursor_force_fallback);
  }
}