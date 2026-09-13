package com.vinisskt.vikey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class LayoutModifierTest
{
  static final KeyValue KV_A = KeyValue.getKeyByName("a");
  static final KeyValue KV_B = KeyValue.getKeyByName("b");

  @Before public void reset()
  {
    LayoutModifier.globalConfig = null;
    LayoutModifier.bottom_row = null;
    LayoutModifier.number_row_no_symbols = null;
    LayoutModifier.number_row_symbols = null;
    LayoutModifier.num_pad = null;
  }

  /** A Config whose private constructor didn't run: all writable fields stay
      usable while the file/resource fields keep their defaults. */
  static Config allocatedConfig()
  {
    return mock(Config.class);
  }

  static Config defaultConfig()
  {
    Config c = allocatedConfig();
    c.editor_config = new EditorConfig();
    c.layouts = new ArrayList<KeyboardData>();
    c.extra_keys_param = new HashMap<KeyValue, KeyboardData.PreferredPos>();
    c.extra_keys_custom = new HashMap<KeyValue, KeyboardData.PreferredPos>();
    c.shouldOfferVoiceTyping = false;
    c.change_method_key_replacement = KeyValue.CHANGE_METHOD;
    c.show_numpad = false;
    c.inverse_numpad = false;
    c.add_number_row = false;
    c.number_row_symbols = false;
    c.split_layout = false;
    return c;
  }

  static KeyboardData.Key oneKey(KeyValue center, float width, float shift)
  {
    return new KeyboardData.Key(new KeyValue[]{ center, null, null, null, null,
        null, null, null, null }, null, 0, width, shift, null,
        KeyboardData.Key.Role.Normal);
  }

  static KeyboardData.Row row(KeyboardData.Key... keys)
  {
    return new KeyboardData.Row(Arrays.asList(keys), 1.f, 0.f);
  }

  static KeyboardData keyboard(boolean bottom_row, KeyboardData.Row... rows)
  {
    return new KeyboardData(Arrays.asList(rows), 4.f, null, "s", null, "n",
        bottom_row, false, false, false);
  }

  static KeyValue digit(char c)
  {
    return KeyValue.makeCharKey(c);
  }

  static KeyValue key(String name)
  {
    return KeyValue.getKeyByName(name);
  }

  @Test public void inverseNumpadChar()
  {
    char[] flipped = { '7', '8', '9', '1', '2', '3' };
    char[] reversed = { '1', '2', '3', '7', '8', '9' };
    for (int i = 0; i < flipped.length; i++)
      assertEquals(reversed[i], LayoutModifier.inverse_numpad_char(flipped[i]));
    // Other characters are left alone.
    assertEquals('4', LayoutModifier.inverse_numpad_char('4'));
    assertEquals('0', LayoutModifier.inverse_numpad_char('0'));
    assertEquals('5', LayoutModifier.inverse_numpad_char('5'));
  }

  @Test public void numpadScriptMap()
  {
    assertNull(LayoutModifier.numpad_script_map(null));
    assertNull(LayoutModifier.numpad_script_map("unknown-script"));
    KeyboardData.MapKeyValues m = LayoutModifier.numpad_script_map("tamil");
    assertNotNull(m);
    KeyValue mapped = m.apply(digit('1'), false);
    assertEquals(KeyValue.Kind.Char, mapped.getKind());
    assertEquals('\u0BE7', mapped.getChar()); // Tamil digit ௧
    // A non-Char key is left unchanged by the script map.
    assertEquals(key("delete"), m.apply(key("delete"), false));
  }

  @Test public void modifyNumberRow()
  {
    KeyboardData.Row digits = row(
        oneKey(digit('1'), 1.f, 0.f), oneKey(digit('2'), 1.f, 0.f));
    KeyboardData main = keyboard(false, row(oneKey(KV_A, 1.f, 0.f)));
    // No script: the row is returned as is.
    assertSame(digits, LayoutModifier.modify_number_row(digits, main));
    main = new KeyboardData(new ArrayList<KeyboardData.Row>(main.rows), 4.f, null, "s", "tamil",
        "n", false, false, false, false);
    KeyboardData.Row mapped = LayoutModifier.modify_number_row(digits, main);
    assertEquals('\u0BE7', mapped.keys.get(0).keys[0].getChar());
  }

  @Test public void modifyPinentry()
  {
    KeyboardData pin = keyboard(false, row(
        oneKey(digit('1'), 1.f, 0.f), oneKey(digit('2'), 1.f, 0.f)));
    KeyboardData main = keyboard(false, row(oneKey(KV_A, 1.f, 0.f)));
    // No numpad script: unchanged.
    assertSame(pin, LayoutModifier.modify_pinentry(pin, main));
    KeyboardData scripted = new KeyboardData(new ArrayList<KeyboardData.Row>(main.rows), 4.f,
        null, "s", "tamil", "n", false, false, false, false);
    KeyboardData out = LayoutModifier.modify_pinentry(pin, scripted);
    assertEquals('\u0BE7', out.rows.get(0).keys.get(0).keys[0].getChar());
  }

  @Test public void modifyKeyEvents()
  {
    Config c = defaultConfig();
    LayoutModifier.globalConfig = c;
    // The change-method picker is replaced by the config key.
    assertEquals(KeyValue.CHANGE_METHOD,
        LayoutModifier.modify_key(key("change_method")));
    // The action key follows the editor config.
    EditorConfig ec = c.editor_config;
    assertNull(LayoutModifier.modify_key(key("action")));
    ec.action_key_replacement = key("enter");
    assertEquals(key("enter"), LayoutModifier.modify_key(key("action")));
    // Forward/backward keep or remove the key according to layout count.
    assertNull(LayoutModifier.modify_key(key("switch_forward"))); // 1 layout left
    assertNull(LayoutModifier.modify_key(key("switch_backward")));
    c.layouts.add(keyboard(false, row()));
    c.layouts.add(keyboard(false, row()));
    assertEquals(key("switch_forward"),
        LayoutModifier.modify_key(key("switch_forward"))); // keep with 2
    assertNull(LayoutModifier.modify_key(key("switch_backward")));
    c.layouts.add(keyboard(false, row()));
    assertEquals(key("switch_forward"),
        LayoutModifier.modify_key(key("switch_forward")));
    assertEquals(key("switch_backward"),
        LayoutModifier.modify_key(key("switch_backward"))); // keep with 3
    // Voice typing depends on the config.
    assertNull(LayoutModifier.modify_key(key("voice_typing")));
    assertNull(LayoutModifier.modify_key(key("voice_typing_chooser")));
    c.shouldOfferVoiceTyping = true;
    assertEquals(key("voice_typing"), LayoutModifier.modify_key(key("voice_typing")));
    assertEquals(key("voice_typing_chooser"), LayoutModifier.modify_key(key("voice_typing_chooser")));
  }

  @Test public void modifyKeyEnter()
  {
    Config c = defaultConfig();
    LayoutModifier.globalConfig = c;
    // Default: nothing replaces the Enter key.
    assertEquals(key("enter"), LayoutModifier.modify_key(key("enter")));
    c.editor_config.enter_key_replacement = key("backspace");
    assertEquals(key("backspace"), LayoutModifier.modify_key(key("enter")));
    // Other keys are untouched.
    assertEquals(KV_A, LayoutModifier.modify_key(KV_A));
    assertEquals(key("delete"), LayoutModifier.modify_key(key("delete")));
  }

  @Test public void modifyNumpad()
  {
    KeyboardData numpad = keyboard(false, row(
        oneKey(digit('1'), 1.f, 0.f), oneKey(digit('7'), 1.f, 0.f)));
    KeyboardData main = new KeyboardData(Collections.<KeyboardData.Row>emptyList(),
        4.f, null, "s", null, "n", false, false, false, false);
    LayoutModifier.globalConfig = defaultConfig();
    // Without script or inversion the keys are kept.
    KeyboardData out = LayoutModifier.modify_numpad(numpad, main);
    assertEquals('1', out.rows.get(0).keys.get(0).keys[0].getChar());
    // Inversion flips 7 and 1.
    Config c = defaultConfig();
    c.inverse_numpad = true;
    LayoutModifier.globalConfig = c;
    out = LayoutModifier.modify_numpad(numpad, main);
    assertEquals('7', out.rows.get(0).keys.get(0).keys[0].getChar());
    assertEquals('1', out.rows.get(0).keys.get(1).keys[0].getChar());
    // A tamil script maps the digits (winning over the inversion).
    LayoutModifier.globalConfig = defaultConfig();
    KeyboardData tamil = new KeyboardData(main.rows, 4.f, null, "s", "tamil",
        "n", false, false, false, false);
    out = LayoutModifier.modify_numpad(numpad, tamil);
    assertEquals('\u0BE7', out.rows.get(0).keys.get(0).keys[0].getChar());
  }

  @Test public void modifyNumpadLeavesNonCharKeys()
  {
    KeyboardData numpad = keyboard(false, row(oneKey(key("enter"), 1.f, 0.f)));
    LayoutModifier.globalConfig = defaultConfig();
    KeyboardData out = LayoutModifier.modify_numpad(numpad, keyboard(false, row()));
    assertEquals(key("enter"), out.rows.get(0).keys.get(0).keys[0]);
  }

  @Test public void modifyLayoutAddsConfigKeysAndKeepsMainKeys()
  {
    LayoutModifier.globalConfig = defaultConfig();
    KeyboardData kw = keyboard(false, row(oneKey(KV_A, 1.f, 0.f), oneKey(KV_B, 1.f, 0.f)));
    KeyboardData out = LayoutModifier.modify_layout(kw);
    assertTrue(out.getKeys().containsKey(KeyValue.CONFIG));
    assertEquals(2, out.rows.get(0).keys.size());
    assertEquals(KV_A, out.rows.get(0).keys.get(0).keys[0]);
  }

  @Test public void modifyLayoutAppendsBottomRow()
  {
    LayoutModifier.globalConfig = defaultConfig();
    LayoutModifier.bottom_row = row(oneKey(key("space"), 1.f, 0.f));
    KeyboardData kw = keyboard(true, row(oneKey(KV_A, 1.f, 0.f)));
    KeyboardData out = LayoutModifier.modify_layout(kw);
    assertEquals(2, out.rows.size());
    assertEquals(key("space"), out.rows.get(1).keys.get(0).keys[0]);
  }

  @Test public void modifyLayoutAddsNumberRowAndRemovesDuplicates()
  {
    Config c = defaultConfig();
    c.add_number_row = true;
    c.number_row_symbols = false;
    LayoutModifier.globalConfig = c;
    LayoutModifier.number_row_no_symbols = row(
        oneKey(digit('1'), 1.f, 0.f), oneKey(digit('2'), 1.f, 0.f));
    KeyboardData kw = keyboard(false, row(oneKey(digit('1'), 1.f, 0.f), oneKey(KV_A, 1.f, 0.f)));
    KeyboardData out = LayoutModifier.modify_layout(kw);
    assertEquals(2, out.rows.size());
    // The added number row is inserted on top.
    assertEquals(2, out.rows.get(0).keys.size());
    assertEquals('1', out.rows.get(0).keys.get(0).keys[0].getChar());
    // The duplicated digit is removed from the main row (its cell goes null).
    assertNull(out.rows.get(1).keys.get(0).keys[0]);
    assertEquals(KV_A, out.rows.get(1).keys.get(1).keys[0]);
  }

  @Test public void modifyLayoutAddsNumpad()
  {
    Config c = defaultConfig();
    c.show_numpad = true;
    LayoutModifier.globalConfig = c;
    LayoutModifier.num_pad = keyboard(false, row(
        oneKey(digit('1'), 1.f, 0.f), oneKey(digit('2'), 1.f, 0.f)));
    KeyboardData kw = keyboard(false, row(oneKey(digit('1'), 1.f, 0.f), oneKey(KV_A, 1.f, 0.f)));
    KeyboardData out = LayoutModifier.modify_layout(kw);
    // The numpad digits are added to the first row.
    assertTrue(out.getKeys().containsKey(digit('1')));
    assertTrue(out.getKeys().containsKey(digit('2')));
    // The digit that was kept in the main row got removed by the numpad keys.
    assertNull(out.rows.get(0).keys.get(0).keys[0]);
    assertEquals(KV_A, out.rows.get(0).keys.get(1).keys[0]);
    assertTrue(out.rows.get(0).keys.get(2).keys[0].getChar() == '1');
  }

  @Test public void modifyLayoutWithLocaleExtraKeysComputesSubtype()
  {
    Config c = defaultConfig();
    c.extra_keys_subtype = new ExtraKeys(Collections.singletonList(
        new ExtraKeys.ExtraKey(KeyValue.makeCharKey('x'), null,
            Collections.<KeyValue>emptyList(), null)));
    LayoutModifier.globalConfig = c;
    KeyboardData kw = new KeyboardData(
        Arrays.asList(row(oneKey(KV_A, 1.f, 0.f))), 4.f, null, "s", null, "n",
        false, false, true, false);
    KeyboardData out = LayoutModifier.modify_layout(kw);
    assertTrue(out.getKeys().containsKey(KeyValue.makeCharKey('x')));
    assertEquals(KV_A, out.rows.get(0).keys.get(0).keys[0]);
  }

  @Test public void modifyLayoutRemovesUnlocalizedKeys()
  {
    LayoutModifier.globalConfig = defaultConfig();
    KeyValue z = KeyValue.makeCharKey('z');
    KeyboardData.Key locKey = new KeyboardData.Key(
        new KeyValue[]{ z, null, null, null, null, null, null, null, null },
        null, KeyboardData.Key.F_LOC, 1.f, 0.f, null,
        KeyboardData.Key.Role.Normal);
    KeyboardData kw = new KeyboardData(
        Arrays.asList(new KeyboardData.Row(
            Arrays.asList(locKey, oneKey(KV_A, 1.f, 0.f)), 1.f, 0.f)),
        4.f, null, "s", null, "n", false, false, true, false);
    KeyboardData out = LayoutModifier.modify_layout(kw);
    // The localized key is removed (not in extra_keys), 'a' is kept.
    assertFalse(out.getKeys().containsKey(z));
    assertNull(out.rows.get(0).keys.get(0).keys[0]);
    assertEquals(KV_A, out.rows.get(0).keys.get(1).keys[0]);
  }

  @Test public void modifyLayoutNumberRowSymbolsAndSplit()
  {
    Config c = defaultConfig();
    c.add_number_row = true;
    c.number_row_symbols = true;
    c.split_layout = true;
    LayoutModifier.globalConfig = c;
    LayoutModifier.number_row_symbols = row(
        oneKey(digit('1'), 1.f, 0.f), oneKey(digit('2'), 1.f, 0.f));
    KeyboardData kw = keyboard(false,
        row(oneKey(digit('1'), 1.f, 0.f), oneKey(KV_A, 1.f, 0.f)));
    KeyboardData out = LayoutModifier.modify_layout(kw);
    // The symbol number row is transformed and inserted on top.
    assertEquals(2, out.rows.size());
    assertEquals(2, out.rows.get(0).keys.size());
    assertTrue(out.getKeys().containsKey(digit('2')));
    // The main row was split: it keeps its original keys.
    assertTrue(out.getKeys().containsKey(KV_A));
    assertTrue(out.getKeys().containsKey(digit('1')));
  }
}