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

  /** All ASCII letters and digits map to a key event under Ctrl/Alt/Meta. */
  @Test
  public void ctrlMapsWholeAlphabet()
  {
    for (char c : "abcdefghijklmnopqrstuvwxyz0123456789`-=[]\\;'/@+,.*#() ".toCharArray())
    {
      KeyValue k = KeyModifier.modify(KeyValue.makeCharKey(c), KeyValue.Modifier.CTRL);
      assertEquals(KeyValue.Kind.Keyevent, k.getKind());
    }
    // Editing keys other than the space bar are left alone.
    assertEquals(key("backspace"), KeyModifier.modify(key("backspace"), KeyValue.Modifier.CTRL));
  }

  /** A user modmap overrides the default behaviors. */
  @Test
  public void modmapOverridesDefaults()
  {
    Modmap mm = new Modmap();
    mm.add(Modmap.M.Ctrl, KeyValue.makeCharKey('x'), KeyValue.makeCharKey('y'));
    mm.add(Modmap.M.Shift, KeyValue.makeCharKey('a'), KeyValue.makeCharKey('q'));
    mm.add(Modmap.M.Fn, KeyValue.makeCharKey('a'), KeyValue.makeCharKey('%'));
    KeyModifier.set_modmap(mm);
    // Ctrl turns the *mapped* key into a key event.
    assertEquals(KeyEvent.KEYCODE_Y,
        KeyModifier.modify(KeyValue.makeCharKey('x'), KeyValue.Modifier.CTRL).getKeyevent());
    // Shift and Fn return the mapped key as is.
    assertChar('q', KeyModifier.modify(KeyValue.makeCharKey('a'), KeyValue.Modifier.SHIFT));
    assertChar('%', KeyModifier.modify(KeyValue.makeCharKey('a'), KeyValue.Modifier.FN));
    // A gesture tries the shift mapping first, but the Fn modmap wins over it.
    assertChar('%', KeyModifier.modify(KeyValue.makeCharKey('a'), KeyValue.Modifier.GESTURE));
    KeyModifier.set_modmap(null);
  }

  /** The Fn modifier turns placeholders into their corresponding keys. */
  @Test
  public void fnTurnsPlaceholdersIntoTheirKeys()
  {
    assertKeyevent(KeyEvent.KEYCODE_F11,
        KeyModifier.modify(key("f11_placeholder"), KeyValue.Modifier.FN));
    assertKeyevent(KeyEvent.KEYCODE_F12,
        KeyModifier.modify(key("f12_placeholder"), KeyValue.Modifier.FN));
    assertChar('\u05C1', KeyModifier.modify(key("shindot_placeholder"), KeyValue.Modifier.FN));
    assertChar('\u05C2', KeyModifier.modify(key("sindot_placeholder"), KeyValue.Modifier.FN));
    assertChar('\u05AB', KeyModifier.modify(key("ole_placeholder"), KeyValue.Modifier.FN));
    assertChar('\u05BD', KeyModifier.modify(key("meteg_placeholder"), KeyValue.Modifier.FN));
    // A placeholder without an Fn counterpart is left alone.
    assertEquals(key("removed"), KeyModifier.modify(key("removed"), KeyValue.Modifier.FN));
  }

  /** The arrow-right accent combines a combining char with a char key. */
  @Test
  public void arrowRightCombinesCombiningChar()
  {
    KeyValue combined = KeyModifier.modify(key("a"), KeyValue.Modifier.ARROW_RIGHT);
    assertEquals(KeyValue.Kind.String, combined.getKind());
    assertEquals("a\u20D7", combined.getString());
    // Non-char keys are not modified.
    assertEquals(key("enter"), KeyModifier.modify(key("enter"), KeyValue.Modifier.ARROW_RIGHT));
    assertEquals(str("ab"), KeyModifier.modify(str("ab"), KeyValue.Modifier.ARROW_RIGHT));
  }

  @Test
  public void uncomposableComposeStatesKeepTheKey()
  {
    KeyValue hindi_a = KeyValue.makeCharKey('\u0905');
    assertEquals(hindi_a, KeyModifier.modify(hindi_a, KeyValue.Modifier.SUPERSCRIPT));
    assertEquals(hindi_a, KeyModifier.modify(hindi_a, KeyValue.Modifier.SUBSCRIPT));
    assertEquals(hindi_a, KeyModifier.modify(hindi_a, KeyValue.Modifier.ARROWS));
    // Dead-char on non-char, non-editing keys is a no-op.
    assertEquals(key("enter"), KeyModifier.modify(key("enter"), KeyValue.Modifier.BREVE));
  }

  /** A different hangul initial is greyed, other kinds are preserved. */
  @Test
  public void hangulInitialOtherKinds()
  {
    KeyValue initial = KeyValue.makeHangulInitial("y", 1);
    KeyValue greyed = KeyModifier.modify(initial, key("\u3131"));
    assertTrue(greyed.hasFlagsAny(KeyValue.FLAG_GREYED));
    // String keys are left untouched.
    assertEquals(str("xy"), KeyModifier.modify(str("xy"), key("\u3131")));
  }

  /** Each hangul vowel maps to the expected precomposed medial. */
  @Test
  public void hangulMedialVowels()
  {
    char[] vowels = "\u314F\u3150\u3151\u3152\u3153\u3154\u3155\u3156\u3157\u3158\u3159\u315A\u315B\u315C\u315D\u315E\u315F\u3160\u3161\u3162\u3163".toCharArray();
    for (int i = 0; i < vowels.length; i++)
    {
      KeyValue m = KeyModifier.modify(KeyValue.makeCharKey(vowels[i]), key("\u3131"));
      assertEquals(KeyValue.Kind.Hangul_medial, m.getKind());
      assertEquals(0xAC00 + i * 28, m.getHangulPrecomposed());
    }
  }

  /** Each hangul final combining with a medial produces the expected syllable. */
  @Test
  public void hangulMedialFinals()
  {
    char[] finals = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".toCharArray();
    KeyValue medial = KeyValue.makeHangulMedial(0xAC00, 0);
    for (int i = 0; i < finals.length; i++)
    {
      KeyValue k = KeyModifier.modify(KeyValue.makeCharKey(finals[i]), medial);
      assertEquals(KeyValue.Kind.Char, k.getKind());
      assertEquals(0xAC00 + (i + 1), k.getChar());
    }
    // Finals that are also initials, and the space bar, combine too.
    KeyValue viaInitial = KeyModifier.modify(KeyValue.makeHangulInitial("\u3134", 1), medial);
    assertEquals(0xAC00 + 4, viaInitial.getChar());
    assertEquals(0xAC00, KeyModifier.modify(key("space"), medial).getChar());
    // Non-final characters are greyed.
    KeyValue greyed = KeyModifier.modify(key("q"), medial);
    assertTrue(greyed.hasFlagsAny(KeyValue.FLAG_GREYED));
    // Kinds that do not combine are preserved.
    assertEquals(key("enter"), KeyModifier.modify(key("enter"), medial));
  }

  /** The gesture maps the Keyevent delete to delete_word. */
  @Test
  public void gestureOnKeyeventDelete()
  {
    KeyValue del = KeyValue.keyeventKey("del", KeyEvent.KEYCODE_DEL, 0);
    KeyValue del_word = KeyModifier.modify(del, KeyValue.Modifier.GESTURE);
    assertEquals(KeyValue.Editing.DELETE_WORD, del_word.getEditing());
  }

  /** Every compose accent state resolves to its composed form or the input. */
  @Test
  public void allComposeAccentStates()
  {
    KeyValue in = key("a");
    KeyValue.Modifier[] mods = {
      KeyValue.Modifier.DOT_ABOVE, KeyValue.Modifier.ORDINAL,
      KeyValue.Modifier.BOX, KeyValue.Modifier.SLASH,
      KeyValue.Modifier.BAR, KeyValue.Modifier.DOT_BELOW,
      KeyValue.Modifier.HORN, KeyValue.Modifier.HOOK_ABOVE,
      KeyValue.Modifier.DOUBLE_GRAVE, KeyValue.Modifier.SMALL_CAPS,
    };
    for (KeyValue.Modifier m : mods)
    {
      KeyValue expected = expected_compose(m, in);
      assertEquals("modifier " + m, expected, KeyModifier.modify(in, m));
    }
  }

  static KeyValue expected_compose(KeyValue.Modifier m, KeyValue in)
  {
    return expected_compose_state(compose_state(m), in);
  }
  static KeyValue expected_compose_state(int state, KeyValue in)
  {
    KeyValue r = ComposeKey.apply(state, in);
    return (r != null) ? r : in;
  }
  static int compose_state(KeyValue.Modifier m)
  {
    switch (m)
    {
      case DOT_ABOVE: return ComposeKeyData.accent_dot_above;
      case ORDINAL: return ComposeKeyData.accent_ordinal;
      case BOX: return ComposeKeyData.accent_box;
      case SLASH: return ComposeKeyData.accent_slash;
      case BAR: return ComposeKeyData.accent_bar;
      case DOT_BELOW: return ComposeKeyData.accent_dot_below;
      case HORN: return ComposeKeyData.accent_horn;
      case HOOK_ABOVE: return ComposeKeyData.accent_hook_above;
      case DOUBLE_GRAVE: return ComposeKeyData.accent_double_grave;
      case SMALL_CAPS: return ComposeKeyData.accent_small_caps;
      default: throw new AssertionError();
    }
  }

  /** Shift falls back on the compose table before capitalizing. */
  @Test
  public void shiftComposesNonCapitalizableChars()
  {
    KeyValue sharp_s = KeyValue.makeCharKey('\u00DF');
    KeyValue expected = expected_compose_state(ComposeKeyData.shift, sharp_s);
    assertEquals(expected, KeyModifier.modify(sharp_s, KeyValue.Modifier.SHIFT));
  }

  /** The Fn modifier removes page-up/down keys and ignores other events. */
  @Test
  public void fnRemovesPageMovers()
  {
    KeyValue removed = KeyModifier.modify(key("page_up"), KeyValue.Modifier.FN);
    assertEquals(KeyValue.Kind.Placeholder, removed.getKind());
    assertEquals(KeyValue.Placeholder.REMOVED, removed.getPlaceholder());
    assertEquals(KeyValue.Placeholder.REMOVED,
        KeyModifier.modify(key("page_down"), KeyValue.Modifier.FN).getPlaceholder());
    // An event that has no Fn counterpart is kept.
    assertEquals(key("switch_greekmath"),
        KeyModifier.modify(key("switch_greekmath"), KeyValue.Modifier.FN));
  }

  /** Dead-char accent on the space bar key is a no-op. */
  @Test
  public void deadCharOnSpaceBarKey()
  {
    assertEquals(key("space"), KeyModifier.modify(key("space"), KeyValue.Modifier.BREVE));
  }

  /** Selection mode maps the right cursor to the right selection cursor. */
  @Test
  public void selectionModeRightCursor()
  {
    KeyValue cursor = KeyModifier.modify(key("cursor_right"), KeyValue.Modifier.SELECTION_MODE);
    assertEquals(KeyValue.Slider.Selection_cursor_right, cursor.getSlider());
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