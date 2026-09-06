package juloo.keyboard2;

import android.view.KeyEvent;

import org.junit.Before;
import org.junit.Test;

import static juloo.keyboard2.TestUtils.*;
import static org.junit.Assert.*;

public class KeyModifierTest
{
  @Before
  public void resetModmap()
  {
    KeyModifier.set_modmap(null);
  }
  /** Empty-string placeholder keys and null keys are removed. */
  @Test
  public void nullAndPlaceholderKeys()
  {
    assertNull(KeyModifier.modify(null, Pointers.Modifiers.EMPTY));
    assertNull(KeyModifier.modify(KeyValue.makeStringKey(""), Pointers.Modifiers.EMPTY));
    assertNull(KeyModifier.modify(KeyValue.makeStringKey(""), mods(key("shift"))));
  }

  /** Empty modifiers leave the key untouched. */
  @Test
  public void emptyModsReturnsKey()
  {
    KeyValue a = key("a");
    assertEquals(a, KeyModifier.modify(a, Pointers.Modifiers.EMPTY));
  }

  /** Keys that are not modifiers are ignored. */
  @Test
  public void nonModifierModKeysAreIgnored()
  {
    assertEquals(key("a"), KeyModifier.modify(key("a"), key("esc")));
    assertEquals(key("a"), KeyModifier.modify(key("a"), key("a")));
    assertEquals(key("b"), KeyModifier.modify(key("b"), mods(key("a"))));
  }

  @Test
  public void shiftCapitalizesChars()
  {
    assertChar('A', KeyModifier.modify(key("a"), KeyValue.Modifier.SHIFT));
    assertChar('A', KeyModifier.modify(key("A"), KeyValue.Modifier.SHIFT));
    assertChar('5', KeyModifier.modify(key("5"), KeyValue.Modifier.SHIFT));
    assertChar('1', KeyModifier.modify(KeyValue.makeCharKey('1'), KeyValue.Modifier.SHIFT));
  }

  @Test
  public void shiftCapitalizesStringKeys()
  {
    assertEquals("Abc", KeyModifier.modify(str("abc"), KeyValue.Modifier.SHIFT).getString());
    assertEquals("ABC", KeyModifier.modify(str("ABC"), KeyValue.Modifier.SHIFT).getString());
    assertEquals("ABc", KeyModifier.modify(str("aBc"), KeyValue.Modifier.SHIFT).getString());
    // Multi-character strings fall back on Utils.capitalize_string, preserving flags.
    KeyValue small = KeyModifier.modify(
        KeyValue.makeStringKey("aBc", KeyValue.FLAG_SMALLER_FONT), KeyValue.Modifier.SHIFT);
    assertEquals("ABc", small.getString());
    assertEquals(KeyValue.FLAG_SMALLER_FONT, small.getFlags());
    // Single-char strings are composed through ComposeKeyData.shift.
    KeyValue single = KeyModifier.modify(
        KeyValue.makeStringKey("x", KeyValue.FLAG_SMALLER_FONT), KeyValue.Modifier.SHIFT);
    assertEquals(KeyValue.Kind.Char, single.getKind());
    assertEquals('X', single.getChar());
  }

  @Test
  public void shiftDoesNotModifySpecialKeys()
  {
    assertEquals(key("delete"), KeyModifier.modify(key("delete"), KeyValue.Modifier.SHIFT));
  }

  @Test
  public void multipleModifiersAreApplied()
  {
    // shift is applied first (get(0)), then ctrl.
    KeyValue a = KeyModifier.modify(key("a"),
        Pointers.Modifiers.EMPTY.with_extra_mod(key("ctrl")).with_extra_mod(key("shift")));
    assertEquals(KeyValue.Kind.Keyevent, a.getKind());
    assertEquals(KeyEvent.KEYCODE_A, a.getKeyevent());
    // aigu is applied first, then shift.
    KeyValue e = KeyModifier.modify(key("e"),
        Pointers.Modifiers.EMPTY.with_extra_mod(key("shift")).with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.AIGU)));
    assertChar('\u00C9', e);
    // Same result through modify_no_modmap (no user modmap is installed).
    KeyValue nmod = KeyModifier.modify_no_modmap(key("e"),
        Pointers.Modifiers.EMPTY.with_extra_mod(key("shift")).with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.AIGU)));
    assertChar('\u00C9', nmod);
  }

  @Test
  public void ctrlTurnsCharsIntoKeyEvents()
  {
    assertCtrl('a', KeyEvent.KEYCODE_A);
    assertCtrl('z', KeyEvent.KEYCODE_Z);
    assertCtrl('m', KeyEvent.KEYCODE_M);
    assertCtrl('0', KeyEvent.KEYCODE_0);
    assertCtrl('9', KeyEvent.KEYCODE_9);
    assertCtrl('`', KeyEvent.KEYCODE_GRAVE);
    assertCtrl('-', KeyEvent.KEYCODE_MINUS);
    assertCtrl('=', KeyEvent.KEYCODE_EQUALS);
    assertCtrl('[', KeyEvent.KEYCODE_LEFT_BRACKET);
    assertCtrl(']', KeyEvent.KEYCODE_RIGHT_BRACKET);
    assertCtrl('\\', KeyEvent.KEYCODE_BACKSLASH);
    assertCtrl(';', KeyEvent.KEYCODE_SEMICOLON);
    assertCtrl('\'', KeyEvent.KEYCODE_APOSTROPHE);
    assertCtrl('/', KeyEvent.KEYCODE_SLASH);
    assertCtrl('@', KeyEvent.KEYCODE_AT);
    assertCtrl('+', KeyEvent.KEYCODE_PLUS);
    assertCtrl(',', KeyEvent.KEYCODE_COMMA);
    assertCtrl('.', KeyEvent.KEYCODE_PERIOD);
    assertCtrl('*', KeyEvent.KEYCODE_STAR);
    assertCtrl('#', KeyEvent.KEYCODE_POUND);
    assertCtrl('(', KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN);
    assertCtrl(')', KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN);
  }

  @Test
  public void ctrlTurnsSpaceIntoKeyEvent()
  {
    KeyValue returned = KeyModifier.modify(key("space"), KeyValue.Modifier.CTRL);
    assertEquals(KeyValue.Kind.Keyevent, returned.getKind());
    assertEquals(KeyEvent.KEYCODE_SPACE, returned.getKeyevent());
    KeyValue charspace = KeyModifier.modify(KeyValue.makeCharKey(' '), KeyValue.Modifier.CTRL);
    assertEquals(KeyValue.Kind.Keyevent, charspace.getKind());
    assertEquals(KeyEvent.KEYCODE_SPACE, charspace.getKeyevent());
  }

  @Test
  public void ctrlDoesNotChangeUnmappableKeys()
  {
    KeyValue n_tilde = KeyValue.makeCharKey('\u00F1');
    assertEquals(n_tilde, KeyModifier.modify(n_tilde, KeyValue.Modifier.CTRL));
    assertEquals(key("esc"), KeyModifier.modify(key("esc"), KeyValue.Modifier.CTRL));
    assertEquals(str("abc"), KeyModifier.modify(str("abc"), KeyValue.Modifier.CTRL));
  }

  @Test
  public void altAndMetaTurnIntoKeyEvents()
  {
    KeyValue a = KeyModifier.modify(key("a"), KeyValue.Modifier.ALT);
    assertEquals(KeyValue.Kind.Keyevent, a.getKind());
    assertEquals(KeyEvent.KEYCODE_A, a.getKeyevent());
    KeyValue m = KeyModifier.modify(key("a"), KeyValue.Modifier.META);
    assertEquals(KeyValue.Kind.Keyevent, m.getKind());
    assertEquals(KeyEvent.KEYCODE_A, m.getKeyevent());
    KeyValue space = KeyModifier.modify(key("space"), KeyValue.Modifier.META);
    assertEquals(KeyEvent.KEYCODE_SPACE, space.getKeyevent());
    KeyValue n_tilde = KeyValue.makeCharKey('\u00F1');
    assertEquals(n_tilde, KeyModifier.modify(n_tilde, KeyValue.Modifier.ALT));
    assertEquals(str("abc"), KeyModifier.modify(str("abc"), KeyValue.Modifier.META));
  }

  @Test
  public void fnTurnsDpadAndMiscIntoOtherKeys()
  {
    assertKeyevent(KeyEvent.KEYCODE_PAGE_UP, KeyModifier.modify(key("up"), KeyValue.Modifier.FN));
    assertKeyevent(KeyEvent.KEYCODE_PAGE_DOWN, KeyModifier.modify(key("down"), KeyValue.Modifier.FN));
    assertKeyevent(KeyEvent.KEYCODE_MOVE_HOME, KeyModifier.modify(key("left"), KeyValue.Modifier.FN));
    assertKeyevent(KeyEvent.KEYCODE_MOVE_END, KeyModifier.modify(key("right"), KeyValue.Modifier.FN));
    assertKeyevent(KeyEvent.KEYCODE_INSERT, KeyModifier.modify(key("esc"), KeyValue.Modifier.FN));
    assertChar('\t', KeyModifier.modify(key("tab"), KeyValue.Modifier.FN));
  }

  @Test
  public void fnTurnsEventsAndEditingIntoOtherKeys()
  {
    KeyValue greek = KeyModifier.modify(key("switch_numeric"), KeyValue.Modifier.FN);
    assertEquals(KeyValue.Kind.Event, greek.getKind());
    assertEquals(KeyValue.Event.SWITCH_GREEKMATH, greek.getEvent());
    assertChar('\u00A0', KeyModifier.modify(key("space"), KeyValue.Modifier.FN));
    KeyValue redo = KeyModifier.modify(key("undo"), KeyValue.Modifier.FN);
    assertEquals(KeyValue.Kind.Editing, redo.getKind());
    assertEquals(KeyValue.Editing.REDO, redo.getEditing());
    KeyValue plain = KeyModifier.modify(key("paste"), KeyValue.Modifier.FN);
    assertEquals(KeyValue.Editing.PASTE_PLAIN, plain.getEditing());
    assertEquals(key("backspace"), KeyModifier.modify(key("backspace"), KeyValue.Modifier.FN));
    assertEquals(key("delete"), KeyModifier.modify(key("delete"), KeyValue.Modifier.FN));
    assertEquals(key("enter"), KeyModifier.modify(key("enter"), KeyValue.Modifier.FN));
  }

  @Test
  public void accentsCompose()
  {
    assertChar('\u00E0', KeyModifier.modify(key("a"), KeyValue.Modifier.GRAVE));
    assertChar('\u00E8', KeyModifier.modify(key("e"), KeyValue.Modifier.GRAVE));
    assertChar('\u00E9', KeyModifier.modify(key("e"), KeyValue.Modifier.AIGU));
    assertChar('\u00E1', KeyModifier.modify(key("a"), KeyValue.Modifier.AIGU));
    assertChar('\u00F4', KeyModifier.modify(key("o"), KeyValue.Modifier.CIRCONFLEXE));
    assertChar('\u00F1', KeyModifier.modify(key("n"), KeyValue.Modifier.TILDE));
    assertChar('\u00FC', KeyModifier.modify(key("u"), KeyValue.Modifier.TREMA));
    assertChar('\u010D', KeyModifier.modify(key("c"), KeyValue.Modifier.CARON));
    assertChar('\u00E5', KeyModifier.modify(key("a"), KeyValue.Modifier.RING));
    assertChar('\u0101', KeyModifier.modify(key("a"), KeyValue.Modifier.MACRON));
    assertChar('\u0105', KeyModifier.modify(key("a"), KeyValue.Modifier.OGONEK));
    assertChar('\u00E7', KeyModifier.modify(key("c"), KeyValue.Modifier.CEDILLE));
    assertChar('\u0151', KeyModifier.modify(key("o"), KeyValue.Modifier.DOUBLE_AIGU));
  }

  @Test
  public void composedSuperscriptsAndSubscripts()
  {
    assertChar('\u00B9', KeyModifier.modify(key("1"), KeyValue.Modifier.SUPERSCRIPT));
    assertChar('\u2080', KeyModifier.modify(key("0"), KeyValue.Modifier.SUBSCRIPT));
    assertChar('\u2199', KeyModifier.modify(key("1"), KeyValue.Modifier.ARROWS));
  }

  @Test
  public void uncomposableAccentFallsBack()
  {
    KeyValue z = key("z");
    assertEquals(z, KeyModifier.modify(z, KeyValue.Modifier.TREMA));
    assertEquals(key("a"), KeyModifier.modify(key("a"), KeyValue.Modifier.BREVE));
    KeyValue q = KeyValue.makeCharKey('Q');
    assertEquals(q, KeyModifier.modify(q, KeyValue.Modifier.TREMA));
  }

  @Test
  public void longPressModifies()
  {
    assertEquals(KeyValue.CHANGE_METHOD,
        KeyModifier.modify_long_press(key("change_method_prev")));
    assertEquals(KeyValue.CHANGE_METHOD,
        KeyModifier.modify_long_press(key("change_method_next")));
    assertEquals(KeyValue.VOICE_TYPING_CHOOSER,
        KeyModifier.modify_long_press(key("voice_typing")));
    assertEquals(key("enter"), KeyModifier.modify_long_press(key("enter")));
    assertEquals(key("a"), KeyModifier.modify_long_press(key("a")));
  }

  @Test
  public void gestureAppliesShift()
  {
    assertChar('A', KeyModifier.modify(key("a"), KeyValue.Modifier.GESTURE));
    KeyValue capslock = KeyModifier.modify(key("shift"), KeyValue.Modifier.GESTURE);
    assertEquals(KeyValue.Kind.Event, capslock.getKind());
    assertEquals(KeyValue.Event.CAPS_LOCK, capslock.getEvent());
    KeyValue delete_word = KeyModifier.modify(key("backspace"), KeyValue.Modifier.GESTURE);
    assertEquals(KeyValue.Editing.DELETE_WORD, delete_word.getEditing());
    KeyValue fwd = KeyModifier.modify(key("delete"), KeyValue.Modifier.GESTURE);
    assertEquals(KeyValue.Editing.FORWARD_DELETE_WORD, fwd.getEditing());
    // The space bar is turned into a non-breaking space by the Fn modifier.
    assertChar('\u00A0', KeyModifier.modify(key("space"), KeyValue.Modifier.GESTURE));
  }

  @Test
  public void selectionModeModifies()
  {
    assertEquals(key("a"), KeyModifier.modify(key("a"), KeyValue.Modifier.SELECTION_MODE));
    KeyValue cancel = KeyModifier.modify(key(" "), KeyValue.Modifier.SELECTION_MODE);
    assertEquals(KeyValue.Editing.SELECTION_CANCEL, cancel.getEditing());
    assertEquals(KeyValue.Editing.SELECTION_CANCEL,
        KeyModifier.modify(key("space"), KeyValue.Modifier.SELECTION_MODE).getEditing());
    assertEquals(KeyValue.Editing.SELECTION_CANCEL,
        KeyModifier.modify(key("esc"), KeyValue.Modifier.SELECTION_MODE).getEditing());
    KeyValue cursor = KeyModifier.modify(key("cursor_left"), KeyValue.Modifier.SELECTION_MODE);
    assertEquals(KeyValue.Kind.Slider, cursor.getKind());
    assertEquals(KeyValue.Slider.Selection_cursor_left, cursor.getSlider());
    assertEquals(key("enter"), KeyModifier.modify(key("enter"), KeyValue.Modifier.SELECTION_MODE));
  }

  @Test
  public void composePendingComposes()
  {
    // compose+space opens a sub-state waiting for another key.
    KeyValue state = KeyModifier.modify(key("space"), mods(key("compose")));
    assertEquals(KeyValue.Kind.Compose_pending, state.getKind());
    assertEquals(str("~"), eval("compose", "-", " "));
  }

  @Test
  public void composePendingGreysOutUnmatchables()
  {
    KeyValue q = KeyModifier.modify(key("q"), mods(key("compose")));
    assertEquals('q', q.getChar());
    assertTrue(q.hasFlagsAny(KeyValue.FLAG_GREYED));
  }

  @Test
  public void composePendingIgnoresEventKeysButGreysKeyevents()
  {
    KeyValue capslock = KeyModifier.modify(key("capslock"), mods(key("compose")));
    assertEquals(KeyValue.Kind.Event, capslock.getKind());
    assertFalse(capslock.hasFlagsAny(KeyValue.FLAG_GREYED));
    KeyValue enter = KeyModifier.modify(key("enter"), mods(key("compose")));
    assertEquals(KeyEvent.KEYCODE_ENTER, enter.getKeyevent());
    assertTrue(enter.hasFlagsAny(KeyValue.FLAG_GREYED));
  }

  @Test
  public void composePendingCancelsOnCompose()
  {
    KeyValue cancel = KeyModifier.modify(key("compose"), mods(key("compose")));
    assertEquals(KeyValue.Kind.Placeholder, cancel.getKind());
    assertEquals(KeyValue.Placeholder.COMPOSE_CANCEL, cancel.getPlaceholder());
  }

  @Test
  public void evalComposesSequences()
  {
    assertEquals(str("~"), eval("compose", "-", " "));
    assertEquals(key("nbsp"), eval("compose", "space", "space"));
    assertEquals(str("\u00E9"), eval("compose", "'", "e"));
  }

  @Test
  public void numpadScripts()
  {
    assertEquals(-1, KeyModifier.modify_numpad_script(null));
    assertEquals(-1, KeyModifier.modify_numpad_script("unknown-script"));
    assertEquals(ComposeKeyData.numpad_hindu,
        KeyModifier.modify_numpad_script("hindu-arabic"));
    assertEquals(ComposeKeyData.numpad_bengali,
        KeyModifier.modify_numpad_script("bengali"));
    assertEquals(ComposeKeyData.numpad_tamil,
        KeyModifier.modify_numpad_script("tamil"));
  }

  @Test
  public void hangulInitialSelfModifies()
  {
    // A single-char string key is returned as a greyed Char key.
    KeyValue k = KeyModifier.modify(key("\u3131"), key("\u3131"));
    assertEquals("\u3131", k.getString());
    assertEquals('\u3131', k.getChar());
    assertTrue(k.hasFlagsAny(KeyValue.FLAG_GREYED));
  }

  @Test
  public void hangulInitialCombinesWithVowel()
  {
    KeyValue m = KeyModifier.modify(key("\u314F"), key("\u3131"));
    assertEquals(KeyValue.Kind.Hangul_medial, m.getKind());
    assertEquals(0xAC00, m.getHangulPrecomposed());
    KeyValue q = KeyModifier.modify(key("q"), key("\u3131"));
    assertEquals('q', q.getChar());
    assertTrue(q.hasFlagsAny(KeyValue.FLAG_GREYED));
  }

  @Test
  public void hangulMedialCombinesWithFinal()
  {
    KeyValue medial = KeyValue.makeHangulMedial(0xAC00, 0);
    assertEquals(KeyValue.Kind.Hangul_medial, medial.getKind());
    KeyValue k = KeyModifier.modify(key("\u3131"), medial);
    assertEquals(KeyValue.Kind.Char, k.getKind());
    assertEquals(0xAC01, k.getChar());
    KeyValue q = KeyModifier.modify(key("q"), medial);
    assertEquals('q', q.getChar());
    assertTrue(q.hasFlagsAny(KeyValue.FLAG_GREYED));
  }

  static void assertChar(char c, KeyValue k)
  {
    assertEquals(KeyValue.Kind.Char, k.getKind());
    assertEquals(c, k.getChar());
  }

  static void assertKeyevent(int code, KeyValue k)
  {
    assertEquals(KeyValue.Kind.Keyevent, k.getKind());
    assertEquals(code, k.getKeyevent());
  }

  static void assertCtrl(char c, int code)
  {
    KeyValue k = KeyModifier.modify(KeyValue.makeCharKey(c), KeyValue.Modifier.CTRL);
    assertEquals(KeyValue.Kind.Keyevent, k.getKind());
    assertEquals(code, k.getKeyevent());
  }

  static Pointers.Modifiers mods(KeyValue k)
  {
    return Pointers.Modifiers.EMPTY.with_extra_mod(k);
  }
}