package juloo.keyboard2;

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
    EditorConfig ec = new EditorConfig();
    EditorInfo info = new EditorInfo();
    info.inputType = inputType;
    info.imeOptions = options;
    info.packageName = "com.example.editor";
    ec.refresh(info, null);
    return ec;
  }

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
}