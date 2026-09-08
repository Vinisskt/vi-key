package juloo.keyboard2;

import android.view.KeyEvent;
import juloo.keyboard2.KeyValue;
import org.junit.Test;
import static juloo.keyboard2.TestUtils.*;
import static org.junit.Assert.*;

public class KeyValueTest
{
  public KeyValueTest() {}

  @Test
  public void equals()
  {
    assertEquals(str("Foo").withSymbol("Symbol"),
        KeyValue.makeMacro("Symbol", new KeyValue[] { str("Foo") }, 0));
    assertEquals(KeyValue.getSpecialKeyByName("tab"),
        KeyValue.keyeventKey(0xF03C, KeyEvent.KEYCODE_TAB, KeyValue.FLAG_KEY_FONT | KeyValue.FLAG_SMALLER_FONT));
    assertEquals(KeyValue.getSpecialKeyByName("tab").withSymbol("t"),
        KeyValue.keyeventKey("t", KeyEvent.KEYCODE_TAB, 0));
    assertEquals(KeyValue.getSpecialKeyByName("tab").withSymbol("tab"),
        KeyValue.keyeventKey("tab", KeyEvent.KEYCODE_TAB, KeyValue.FLAG_SMALLER_FONT));
  }

  @Test
  public void numpad_script()
  {
    assertEquals(apply_numpad_script("hindu-arabic"), "٠١٢٣٤٥٦٧٨٩");
    assertEquals(apply_numpad_script("bengali"), "০১২৩৪৫৬৭৮৯");
    assertEquals(apply_numpad_script("devanagari"), "०१२३४५६७८९");
    assertEquals(apply_numpad_script("persian"), "۰۱۲۳۴۵۶۷۸۹");
    assertEquals(apply_numpad_script("gujarati"), "૦૧૨૩૪૫૬૭૮૯");
    assertEquals(apply_numpad_script("kannada"), "೦೧೨೩೪೫೬೭೮೯");
    assertEquals(apply_numpad_script("tamil"), "௦௧௨௩௪௫௬௭௮௯");
  }
  String apply_numpad_script(String script)
  {
    StringBuilder b = new StringBuilder();
    int map = KeyModifier.modify_numpad_script(script);
    for (char c : "0123456789".toCharArray())
      b.append(ComposeKey.apply(map, c).getChar());
    return b.toString();
  }

  @Test
  public void withSymbol()
  {
    // Multi-char symbols are rendered smaller.
    KeyValue char_ = KeyValue.makeCharKey('a').withSymbol("Ab");
    assertEquals(KeyValue.Kind.Char, char_.getKind());
    assertEquals('a', char_.getChar());
    assertEquals("Ab", char_.getString());
    assertTrue(char_.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT));
    // Single-char symbols clear the font flags.
    KeyValue tab = key("tab").withSymbol("t");
    assertEquals(KeyEvent.KEYCODE_TAB, tab.getKeyevent());
    assertEquals("t", tab.getString());
    assertFalse(tab.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT));
    assertFalse(tab.hasFlagsAny(KeyValue.FLAG_KEY_FONT));
    // The kind is preserved.
    assertEquals("bs", key("backspace").withSymbol("bs").getString());
    assertEquals(KeyValue.Editing.BACKSPACE, key("backspace").withSymbol("bs").getEditing());
    KeyValue cancel = KeyValue.COMPOSE_CANCEL.withSymbol("cc");
    assertEquals(KeyValue.Placeholder.COMPOSE_CANCEL, cancel.getPlaceholder());
    assertEquals("cc", cancel.getString());
    assertEquals(KeyValue.Modifier.SHIFT, key("shift").withSymbol("sh").getModifier());
    assertEquals(KeyValue.Kind.Compose_pending, KeyValue.COMPOSE.withSymbol("c2").getKind());
    // Macro and fall-through kinds.
    KeyValue macro = KeyValue.makeMacro("m", new KeyValue[] { str("a"), str("b") }, 0)
        .withSymbol("m2");
    assertEquals(KeyValue.Kind.Macro, macro.getKind());
    assertEquals("m2", macro.getString());
    assertEquals(2, macro.getMacro().length);
    KeyValue fallback = str("foo").withSymbol("bar");
    assertEquals(KeyValue.Kind.Macro, fallback.getKind());
    assertEquals(1, fallback.getMacro().length);
    assertEquals(str("foo"), fallback.getMacro()[0]);
  }

  @Test
  public void sameKeyAndCompareTo()
  {
    assertFalse(str("a").sameKey(null));
    assertEquals(str("a"), str("a"));
    assertTrue(str("a").sameKey(str("a")));
    // Flags are part of the identity.
    assertFalse(str("a").sameKey(KeyValue.makeStringKey("a", KeyValue.FLAG_GREYED)));
    assertFalse(str("a").equals(KeyValue.makeStringKey("a", KeyValue.FLAG_GREYED)));
    assertTrue(str("a").compareTo(str("b")) < 0);
    assertTrue(str("b").compareTo(str("a")) > 0);
    assertEquals(0, str("a").compareTo(str("a")));
    assertTrue(str("a").compareTo(
        KeyValue.makeStringKey("a", KeyValue.FLAG_GREYED)) != 0);
    assertEquals(str("a").hashCode(), str("a").hashCode());
  }

  @Test
  public void macroComparison()
  {
    KeyValue.Macro m1 = new KeyValue.Macro(new KeyValue[] { str("a") }, "s");
    KeyValue.Macro m2 = new KeyValue.Macro(new KeyValue[] { str("a"), str("b") }, "s");
    KeyValue.Macro m3 = new KeyValue.Macro(new KeyValue[] { str("a") }, "t");
    assertTrue(m1.compareTo(m2) < 0);
    assertTrue(m2.compareTo(m1) > 0);
    assertEquals(0, m1.compareTo(new KeyValue.Macro(new KeyValue[] { str("a") }, "s")));
    assertTrue(m1.compareTo(m3) != 0);
    KeyValue.Macro empty = new KeyValue.Macro(new KeyValue[] {}, "");
    assertEquals("", empty.describe());
    assertEquals(0, empty.compareTo(new KeyValue.Macro(new KeyValue[] {}, "")));
  }

  @Test
  public void toStringDescribes()
  {
    assertEquals("Char:a", str("a").toString());
    assertTrue(key("delete").toString().startsWith("Keyevent:"));
    assertTrue(key("capslock").toString().startsWith("Event:"));
    assertEquals("Macro:m:Char:a,Char:b",
        KeyValue.makeMacro("m", new KeyValue[] { str("a"), str("b") }, 0).toString());
    assertTrue(key("cursor_up").toString().contains("Cursor_up"));
    assertTrue(key("complete_first").toString().contains("Complete_first"));
  }

  @Test
  public void sliders()
  {
    KeyValue left = key("cursor_left");
    assertEquals(KeyValue.Kind.Slider, left.getKind());
    assertEquals(1, left.getSliderRepeat());
    assertEquals(KeyValue.Slider.Cursor_left, left.getSlider());
    assertFalse(left.getSlider().isVertical());
    KeyValue up = key("cursor_up");
    assertTrue(up.getSlider().isVertical());
    // Slider glyphs come from the special font (private-use codepoint).
    assertEquals("\uF106", up.getString());
    assertEquals(up.getString(), up.getSlider().toString());
    assertEquals("Cursor_up", up.getSlider().describe());
    // Sliders that move the left side of the selection use a negative repeat.
    assertEquals(-1, key("selection_cursor_left").getSliderRepeat());
    assertEquals(1, key("selection_cursor_right").getSliderRepeat());
  }

  @Test
  public void statefulKeys()
  {
    KeyValue complete = key("complete_first");
    assertEquals(KeyValue.Kind.Stateful, complete.getKind());
    assertEquals(KeyValue.Stateful.Complete_first, complete.getStateful());
    assertEquals("", complete.getString());
    KeyValue.Stateful._handler = new KeyValue.Stateful.Symbol_provider() {
      @Override
      public String provide_stateful_key_symbol(KeyValue.Stateful q)
      {
        return (q == KeyValue.Stateful.Complete_first) ? "A" : null;
      }
    };
    try
    {
      assertEquals("A", complete.getString());
      assertEquals("", key("complete_emoji").getString());
      assertEquals("Complete_emoji", key("complete_emoji").getStateful().describe());
    }
    finally
    {
      KeyValue.Stateful._handler = null;
    }
  }

  @Test
  public void nullPayloadThrows()
  {
    try
    {
      new KeyValue(null, KeyValue.Kind.Char, 'a', 0);
      fail("expected NullPointerException");
    }
    catch (NullPointerException e) {}
  }

  @Test
  public void constructors()
  {
    KeyValue internal = KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL);
    assertEquals(KeyValue.Kind.Modifier, internal.getKind());
    assertEquals(KeyValue.Modifier.CTRL, internal.getModifier());
    assertEquals(0, internal.getFlags());
    KeyValue pending = KeyValue.makeComposePending("cmp", 123, 0);
    assertEquals(KeyValue.Kind.Compose_pending, pending.getKind());
    assertEquals(123, pending.getPendingCompose());
    assertTrue(pending.hasFlagsAny(KeyValue.FLAG_LATCH));
    KeyValue pending_font = KeyValue.makeComposePending(0xE016, 456, 0);
    assertEquals(456, pending_font.getPendingCompose());
    assertTrue(pending_font.hasFlagsAny(KeyValue.FLAG_KEY_FONT));
  }

  @Test
  public void getKeyByName()
  {
    assertEquals(KeyValue.COMPOSE, KeyValue.getKeyByName("compose"));
    assertEquals(KeyValue.COMPOSE_CANCEL, KeyValue.getKeyByName("compose_cancel"));
    assertEquals(KeyEvent.KEYCODE_TAB, KeyValue.getKeyByName("tab").getKeyevent());
    assertTrue(KeyValue.getKeyByName("tab").hasFlagsAny(KeyValue.FLAG_SMALLER_FONT));
    assertEquals('a', KeyValue.getKeyByName("a").getChar());
    KeyValue unknown = KeyValue.getKeyByName("unknown_key_x");
    assertEquals(KeyValue.Kind.String, unknown.getKind());
    assertEquals("unknown_key_x", unknown.getString());
    // Tamil letters are made smaller.
    KeyValue tamil = KeyValue.getKeyByName("\u0B85");
    assertTrue(tamil.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT));
    assertEquals('\u0B85', tamil.getChar());
    // A parser macro with a custom symbol.
    KeyValue macro = KeyValue.getKeyByName("ab:keyevent:1,keyevent:2");
    assertEquals(KeyValue.Kind.Macro, macro.getKind());
    assertEquals("ab", macro.getString());
    assertEquals(2, macro.getMacro().length);
    assertEquals(1, macro.getMacro()[0].getKeyevent());
    assertEquals(2, macro.getMacro()[1].getKeyevent());
  }

  @Test
  public void placeholdersAndRemoved()
  {
    KeyValue removed = KeyValue.getKeyByName("removed");
    assertEquals(KeyValue.Kind.Placeholder, removed.getKind());
    assertEquals(KeyValue.Placeholder.REMOVED, removed.getPlaceholder());
    assertEquals("", removed.getString());
    assertEquals(KeyValue.Placeholder.F11,
        KeyValue.getKeyByName("f11_placeholder").getPlaceholder());
    // Placeholder keys (empty string) are rejected by KeyModifier.modify.
    assertNull(KeyModifier.modify(removed, Pointers.Modifiers.EMPTY));
  }

  @Test
  public void specialKeyByName()
  {
    assertNull(KeyValue.getSpecialKeyByName("no_such_key"));
    assertEquals(KeyValue.ENTER, KeyValue.getSpecialKeyByName("enter"));
    assertEquals(KeyValue.Modifier.SHIFT,
        KeyValue.getSpecialKeyByName("shift").getModifier());
    assertEquals(KeyValue.Event.SWITCH_VOICE_TYPING,
        KeyValue.getSpecialKeyByName("voice_typing").getEvent());
    assertEquals(KeyValue.Editing.COPY,
        KeyValue.getSpecialKeyByName("copy").getEditing());
  }
}
