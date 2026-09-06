package juloo.keyboard2;

import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.*;

/** [KeyEventHandler] level behavior: INSERT typing, editing keys, sliders,
    system modifiers, the quick double-tap, the floating browser overlay and
    the [':'] commands that go through the handler. */
public class KeyEventHandlerTest extends VimTestBase
{
  @Test
  public void typing_commits_text()
  {
    buffer("", 0);
    press("a");
    press("b");
    press("c");
    assertEquals("abc", _conn.text());
  }

  @Test
  public void string_key_commits_text()
  {
    buffer("", 0);
    press("x");
    press("y");
    assertEquals("xy", _conn.text());
  }

  @Test
  public void event_key_reaches_receiver()
  {
    buffer("", 0);
    press("switch_text");
    press("switch_numeric");
    assertEquals(2, _receiver.events.size());
    assertEquals(KeyValue.Event.SWITCH_TEXT, _receiver.events.get(0));
    assertEquals(KeyValue.Event.SWITCH_NUMERIC, _receiver.events.get(1));
  }

  @Test
  public void keyevent_sends_key_events()
  {
    buffer("", 0);
    press("enter");
    assertTrue(insert());
    assertEquals(2, _conn.keyEvents.size());
  }

  @Test
  public void space_bar_types_a_space()
  {
    buffer("a", 1);
    press("space");
    assertEquals("a ", _conn.text());
  }

  @Test
  public void backspace_in_insert_sends_del_events()
  {
    buffer("abc", 1);
    press("backspace");
    assertEquals(2, _conn.keyEvents.size());
  }

  @Test
  public void editing_keys_send_context_menu_actions()
  {
    buffer("abc", 0);
    press("undo");
    press("redo");
    press("selectAll");
    press("paste");
    press("pasteAsPlainText");
    press("shareText");
    press("replaceText");
    press("textAssist");
    press("autofill");
    assertEquals(9, _conn.contextActions.size());
    assertTrue(_conn.contextActions.contains(android.R.id.undo));
    assertTrue(_conn.contextActions.contains(android.R.id.redo));
    assertTrue(_conn.contextActions.contains(android.R.id.selectAll));
    assertTrue(_conn.contextActions.contains(android.R.id.paste));
    assertTrue(_conn.contextActions.contains(android.R.id.shareText));
  }

  @Test
  public void copy_cut_require_selection()
  {
    buffer("abc", 0);
    press("copy");
    press("cut");
    assertEquals(0, _conn.contextActions.size());
  }

  @Test
  public void delete_word_sends_ctrl_del_events()
  {
    buffer("abc", 0);
    press("delete_word");
    press("forward_delete_word");
    assertEquals(4, _conn.keyEvents.size());
  }

  @Test
  public void cursor_slider_moves_selection()
  {
    buffer("abcde", 2);
    press("cursor_left");
    assertEquals(1, _conn.selStart());
    press("cursor_right");
    press("cursor_right");
    assertEquals(3, _conn.selStart());
  }

  @Test
  public void cursor_slider_expands_selection()
  {
    buffer("abcde", 1, 3);
    press("cursor_right");
    assertEquals(1, _conn.selStart());
    assertEquals(4, _conn.selEnd());
  }

  @Test
  public void selection_slider_moves_side_of_selection()
  {
    buffer("abcde", 1, 3);
    press("selection_cursor_left");
    press("selection_cursor_right");
    assertEquals(0, _conn.selStart());
    assertEquals(4, _conn.selEnd());
  }

  @Test
  public void complete_first_enters_stateful_symbol()
  {
    KeyValue.Stateful._handler = new KeyValue.Stateful.Symbol_provider()
    {
      public String provide_stateful_key_symbol(KeyValue.Stateful q)
      { return "First"; }
    };
    try
    {
      buffer("", 0);
      press("complete_first");
      assertEquals("First", _conn.text());
    }
    finally
    {
      KeyValue.Stateful._handler = null;
    }
  }

  @Test
  public void complete_first_without_symbol_provider_is_noop()
  {
    buffer("", 0);
    press("complete_first");
    assertEquals("", _conn.text());
  }

  @Test
  public void system_modifier_latch_sends_meta_key_events()
  {
    buffer("", 0);
    latch_modifier("ctrl");
    assertEquals(1, _conn.keyEvents.size());
    _handler.mods_changed(Pointers.Modifiers.EMPTY);
    assertEquals(2, _conn.keyEvents.size());
  }

  @Test
  public void shift_latch_sends_meta_key_events()
  {
    buffer("", 0);
    latch_modifier("shift");
    assertEquals(1, _conn.keyEvents.size());
    _handler.mods_changed(Pointers.Modifiers.EMPTY);
    assertEquals(2, _conn.keyEvents.size());
  }

  @Test
  public void quick_double_tap_types_symbol()
  {
    TreeMap<Character, Character> symbols = new TreeMap<Character, Character>();
    symbols.put('x', '\u20AC');
    _handler.quick_tap_symbols(symbols);
    buffer("", 0);
    press("x");
    press("x");
    assertEquals("\u20AC", _conn.text());
    assertEquals(1, _conn.deletions.size());
    assertEquals("1:0", _conn.deletions.get(0));
  }

  @Test
  public void quick_double_tap_cancels_pending_j()
  {
    TreeMap<Character, Character> symbols = new TreeMap<Character, Character>();
    symbols.put('j', 'J');
    _handler.quick_tap_symbols(symbols);
    buffer("", 0);
    press("j");
    press("j");
    // First tap is a pending quick-tap, the second turns it into 'J'.
    assertEquals("J", _conn.text());
  }

  @Test
  public void float_overlay_sends_keys_straight()
  {
    _receiver.floatOpen = true;
    buffer("", 0);
    press("a");
    press("b");
    assertEquals("ab", _conn.text());
  }

  @Test
  public void float_overlay_esc_closes()
  {
    _receiver.floatOpen = true;
    buffer("", 0);
    press("esc");
    assertFalse(_receiver.is_float_open());
    assertEquals("", _conn.text());
  }

  @Test
  public void colon_copy_flashes_copied_count()
  {
    buffer("hello world", 0, 5);
    press("esc");
    press(":");
    press("c");
    press("o");
    press("p");
    press("y");
    press("enter");
    assertTrue(_receiver.lastStatus().startsWith("5 copied"));
  }

  @Test
  public void colon_upper_no_selection_uses_line()
  {
    buffer("ab cd", 2);
    press("esc");
    press(":");
    press("u");
    press("p");
    press("p");
    press("e");
    press("r");
    press("enter");
    assertEquals("AB CD", _conn.text());
    assertTrue(_conn.deletions.isEmpty());
    assertEquals("AB CD".length(), _conn.selStart());
    assertEquals("AB CD".length(), _conn.selEnd());
  }

  @Test
  public void colon_paste_with_null_context_is_noop()
  {
    buffer("abc", 0);
    press("esc");
    press(":");
    press("p");
    press("a");
    press("s");
    press("t");
    press("e");
    press("enter");
    assertEquals("abc", _conn.text());
  }

  @Test
  public void colon_addlua_usage_flash()
  {
    buffer("abc", 0);
    press("esc");
    press(":");
    press("a");
    press("d");
    press("d");
    press("l");
    press("u");
    press("a");
    press("enter");
    assertTrue(_receiver.lastStatus().contains("usage"));
  }

  @Test
  public void called_callback_hooks_do_not_crash_without_suggestions()
  {
    buffer("abc", 0);
    press("hello ");
    _handler.currently_typed_word("foo");
    _handler.dictionary_changed();
    _handler.selection_updated(5, 6, 6);
  }

  @Test
  public void titlize_and_join_names()
  {
    assertEquals("Hello World Foo", KeyEventHandler.titlize("hello WORLD foo"));
    assertEquals("A B", KeyEventHandler.titlize("a b"));
    assertEquals("no lua commands", KeyEventHandler.join_names(new String[0]));
    assertEquals("a b", KeyEventHandler.join_names(new String[] {"a", "b"}));
  }

  @Test
  public void vim_help_html_contains_sections()
  {
    String html = KeyEventHandler.vim_help_html();
    assertTrue(html.contains("vi_key"));
    assertTrue(html.contains("Modo NORMAL"));
    assertTrue(html.contains("Busca"));
    assertTrue(html.contains("Comandos"));
  }

  // ---- Macros -------------------------------------------------------------

  @Test
  public void evaluate_macro_empty_is_noop()
  {
    buffer("", 0);
    _handler.evaluate_macro(new KeyValue[0]);
    assertEquals("", _conn.text());
  }

  @Test
  public void evaluate_macro_types_char_keys()
  {
    buffer("", 0);
    _handler.evaluate_macro(new KeyValue[] {TestUtils.key("h"), TestUtils.key("i")});
    assertEquals("hi", _conn.text());
  }

  @Test
  public void evaluate_macro_with_latched_modifier()
  {
    buffer("", 0);
    _handler.evaluate_macro(new KeyValue[] {TestUtils.key("shift"), TestUtils.key("a")});
    assertEquals("A", _conn.text());
    assertTrue(_conn.commits.contains("A"));
  }

  @Test
  public void evaluate_macro_ctrl_turns_char_into_keyevent()
  {
    buffer("", 0);
    _handler.evaluate_macro(new KeyValue[] {TestUtils.key("ctrl"), TestUtils.key("a")});
    assertEquals("", _conn.text());
    assertEquals(4, _conn.keyEvents.size());
  }

  @Test
  public void evaluate_macro_with_editing_key_delays_remaining_keys()
  {
    buffer("abc", 0);
    _handler.evaluate_macro(new KeyValue[] {TestUtils.key("backspace"), TestUtils.key("a")});
    // The backspace is delivered, the delayed 'a' never runs without a looper.
    assertEquals(2, _conn.keyEvents.size());
    assertEquals("abc", _conn.text());
  }

  // ---- Commands -----------------------------------------------------------

  @Test
  public void execute_vim_command_lua_commands_without_context_are_noops()
  {
    buffer("abc", 0);
    _handler.execute_vim_command("ls");
    _handler.execute_vim_command("reload");
    _handler.execute_vim_command("rmlua");
    _handler.execute_vim_command("default");
    assertEquals("abc", _conn.text());
  }

  @Test
  public void colon_copy_no_selection_copies_line()
  {
    buffer("aa\nbb\ncc", 3);
    press("esc");
    _handler.execute_vim_command("copy");
    assertTrue(lastStatusText().startsWith("2 copied"));
    assertEquals("aa\nbb\ncc", _conn.text());
  }
}