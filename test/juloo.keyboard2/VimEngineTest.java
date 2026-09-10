package juloo.keyboard2;

import org.junit.Test;
import static org.junit.Assert.*;

/** NORMAL-mode behavior of the [VimEngine]: motions (selection-based and
    key-event based), counts, line/word deletions, [o]/[O], search and the
    [':'] command line. */
public class VimEngineTest extends VimTestBase
{
  @Test
  public void escapes_to_normal_mode()
  {
    buffer("abc", 0);
    press_escape();
    assertTrue(normal());
    assertFalse(insert());
  }

  @Test
  public void i_returns_to_insert_mode()
  {
    buffer("abc", 0);
    press_escape();
    press("i");
    assertTrue(insert());
  }

  @Test
  public void a_appends_after_cursor_into_insert_mode()
  {
    buffer("abc", 0);
    press_escape();
    press("a");
    assertTrue(insert());
    // moves one char to the right (DPAD_RIGHT: down + up) before inserting
    assertEquals(4, _conn.keyEvents.size());
  }

  @Test
  public void plain_esc_in_insert_goes_to_the_app()
  {
    buffer("abc", 0);
    press("esc");
    assertTrue(insert());
    assertEquals(2, _conn.keyEvents.size());
  }

  // ---- Motions (word/line, selection-based) -----------------------------

  @Test
  public void w_moves_over_word_and_spaces()
  {
    buffer("hello world foo", 0);
    press_escape();
    press("w");
    assertEquals(6, _conn.selStart());
    press("w");
    assertEquals(12, _conn.selStart());
  }

  @Test
  public void count_motion_repeats()
  {
    buffer("hello world foo", 0);
    press_escape();
    press("2");
    press("w");
    assertEquals(12, _conn.selStart());
  }

  @Test
  public void b_moves_word_backward()
  {
    buffer("hello world foo", 12);
    press_escape();
    press("b");
    assertEquals(6, _conn.selStart());
  }

  @Test
  public void e_moves_to_word_end()
  {
    buffer("hello world", 0);
    press_escape();
    press("e");
    assertEquals(4, _conn.selStart());
  }

  @Test
  public void gg_moves_to_document_start()
  {
    buffer("foo bar", 5);
    press_escape();
    press("g");
    press("g");
    assertEquals(0, _conn.selStart());
  }

  @Test
  public void G_moves_to_document_end()
  {
    buffer("foo bar", 0);
    press_escape();
    press("G");
    assertEquals(7, _conn.selStart());
  }

  @Test
  public void count_G_moves_to_line()
  {
    buffer("aa\nbb\ncc", 0);
    press_escape();
    press("2");
    press("G");
    assertEquals(3, _conn.selStart());
  }

  // ---- Motions (key events) ---------------------------------------------

  @Test
  public void count_h_emits_dpad_events()
  {
    buffer("abc", 0);
    press_escape();
    press("3");
    press("h");
    assertEquals(8, _conn.keyEvents.size());
  }

  @Test
  public void col_start_end_emit_home_end_events()
  {
    buffer("abc", 1);
    press_escape();
    press("0");
    assertEquals(4, _conn.keyEvents.size());
    press("$");
    assertEquals(6, _conn.keyEvents.size());
  }

  @Test
  public void jk_k_quick_escape_switches_to_normal_without_committing_j()
  {
    buffer("abc", 0);
    press("j");
    assertEquals("abc", _conn.text());
    press("k");
    assertTrue(normal());
    assertEquals("abc", _conn.text());
  }

  @Test
  public void j_followed_by_other_char_flushes_j()
  {
    buffer("abc", 0);
    press("j");
    press("x");
    assertTrue(insert());
    assertEquals("jxabc", _conn.text());
  }

  // ---- Line opening ------------------------------------------------------

  @Test
  public void o_opens_line_below_and_enters_insert()
  {
    buffer("abc\ndef", 2);
    press_escape();
    press("o");
    assertTrue(insert());
    assertEquals("abc\n\ndef", _conn.text());
    assertEquals(4, _conn.selStart());
  }

  @Test
  public void O_opens_line_above_and_enters_insert()
  {
    buffer("abc\ndef", 2);
    press_escape();
    press("O");
    assertTrue(insert());
    assertEquals("\nabc\ndef", _conn.text());
    assertEquals(1, _conn.selStart());
  }

  // ---- Deletions ---------------------------------------------------------

  @Test
  public void x_deletes_character()
  {
    buffer("hello", 1);
    press_escape();
    press("x");
    assertEquals("hllo", _conn.text());
  }

  @Test
  public void count_x_deletes_many()
  {
    buffer("hello", 0);
    press_escape();
    press("2");
    press("x");
    assertEquals("llo", _conn.text());
  }

  @Test
  public void x_deletes_selection()
  {
    buffer("hello", 1, 4);
    press_escape();
    press("x");
    assertEquals("ho", _conn.text());
    assertEquals(1, _conn.selStart());
  }

  @Test
  public void dd_deletes_line()
  {
    buffer("aa\nbb\ncc", 0);
    press_escape();
    press("d");
    press("d");
    assertEquals("bb\ncc", _conn.text());
    assertEquals(0, _conn.selStart());
  }

  @Test
  public void count_dd_deletes_lines()
  {
    buffer("aa\nbb\ncc", 0);
    press_escape();
    press("2");
    press("d");
    press("d");
    assertEquals("cc", _conn.text());
  }

  @Test
  public void dw_deletes_word()
  {
    buffer("hello world", 0);
    press_escape();
    press("d");
    press("w");
    assertEquals("world", _conn.text());
  }

  @Test
  public void de_deletes_to_word_end()
  {
    buffer("hello world", 0);
    press_escape();
    press("d");
    press("e");
    assertEquals(" world", _conn.text());
  }

  @Test
  public void d_dollar_deletes_to_column_end()
  {
    buffer("hello world", 3);
    press_escape();
    press("d");
    press("$");
    assertEquals("hel", _conn.text());
  }

  @Test
  public void d0_deletes_to_column_start()
  {
    buffer("hello world", 3);
    press_escape();
    press("d");
    press("0");
    assertEquals("lo world", _conn.text());
  }

  // ---- Word helpers ------------------------------------------------------

  @Test
  public void word_helpers()
  {
    assertEquals(6, VimEngine.word_start_after("hello world", 0));
    assertEquals(6, VimEngine.word_start_after("hello world foo", 0));
    assertEquals(6, VimEngine.word_start_before("hello world", 11));
    assertEquals(5, VimEngine.word_end_forward("hello world", 0));
    // Cursor already on a word boundary.
    assertEquals(0, VimEngine.word_start_before("hello world", 6));
  }

  // ---- Search ------------------------------------------------------------

  @Test
  public void search_selects_match_as_typed()
  {
    buffer("hello world", 0);
    press_escape();
    press("/");
    press("w");
    press("o");
    assertEquals(6, _conn.selStart());
    assertEquals(8, _conn.selEnd());
  }

  @Test
  public void backspace_edits_search_query()
  {
    buffer("hello world", 0);
    press_escape();
    press("/");
    press("w");
    press("o");
    press("backspace");
    press("w");
    assertEquals(6, _conn.selStart());
    assertEquals(7, _conn.selEnd());
  }

  @Test
  public void search_commit_then_n_and_N_navigate()
  {
    buffer("foo bar foo", 0);
    press_escape();
    press("/");
    press("f");
    press("o");
    press("o");
    press("enter");
    assertTrue(normal());
    assertEquals(0, _conn.selStart());
    assertEquals(3, _conn.selEnd());
    press("n");
    assertEquals(8, _conn.selStart());
    assertEquals(11, _conn.selEnd());
    press("N");
    assertEquals(0, _conn.selStart());
    assertEquals(3, _conn.selEnd());
  }

  @Test
  public void search_no_match_marks_status_and_enter_returns_normal()
  {
    buffer("hello", 0);
    press_escape();
    press("/");
    press("z");
    press("z");
    assertTrue(lastStatusText().endsWith("[no match]"));
    press("enter");
    assertTrue(normal());
  }

  @Test
  public void esc_cancels_search()
  {
    buffer("hello", 0);
    press_escape();
    press("/");
    press("e");
    press_escape();
    assertTrue(normal());
  }

  // ---- Command line ------------------------------------------------------

  @Test
  public void colon_upper_transforms_selection()
  {
    buffer("hello world", 0, 5);
    press_escape();
    press(":");
    press("u");
    press("p");
    press("p");
    press("e");
    press("r");
    assertEquals(":upper", lastStatusText());
    press("enter");
    assertEquals("HELLO world", _conn.text());
    // The replacement must go through commitText over the selection, without
    // deleteSurroundingText (whose after length is ignored by some editors,
    // e.g. Termux), so the original text is actually removed.
    assertTrue("expected selection to be replaced in place",
        _conn.deletions.isEmpty());
    assertEquals("HELLO".length(), _conn.selStart());
    assertEquals("HELLO".length(), _conn.selEnd());
  }

  @Test
  public void colon_lower_transforms_line()
  {
    buffer("AB cd", 2);
    press_escape();
    press(":");
    press("l");
    press("o");
    press("w");
    press("e");
    press("r");
    press("enter");
    assertEquals("ab cd", _conn.text());
    // Same as colon_upper_transforms_selection: in-place replacement, no
    // deleteSurroundingText, cursor ends up after the replaced line.
    assertTrue(_conn.deletions.isEmpty());
    assertEquals("ab cd".length(), _conn.selStart());
    assertEquals("ab cd".length(), _conn.selEnd());
  }

  @Test
  public void colon_title_transforms_selection()
  {
    buffer("hELLO WORLD", 0, 5);
    press_escape();
    press(":");
    press("t");
    press("i");
    press("t");
    press("l");
    press("e");
    press("enter");
    assertEquals("Hello WORLD", _conn.text());
  }

  @Test
  public void colon_goto_line()
  {
    buffer("a\nb\nc", 0);
    press_escape();
    press(":");
    press("g");
    press("o");
    press("t");
    press("o");
    press(" ");
    press("2");
    press("enter");
    assertEquals(2, _conn.selStart());
  }

  @Test
  public void colon_unknown_command_flashes()
  {
    buffer("abc", 0);
    press_escape();
    press(":");
    press("z");
    press("z");
    press("z");
    press("enter");
    assertTrue(_receiver.lastStatus().contains("unknown command"));
  }

  @Test
  public void colon_help_opens_the_basics_page()
  {
    buffer("abc", 0);
    press_escape();
    press(":");
    press("h");
    press("enter");
    assertEquals(1, _receiver.pages.size());
    assertTrue(_receiver.pages.get(0).contains("vi_key"));
  }

  @Test
  public void colon_float_toggles_browser()
  {
    buffer("abc", 0);
    press_escape();
    press(":");
    press("b");
    press("r");
    press("enter");
    assertTrue(_receiver.is_float_open());
    _handler.execute_vim_command("br");
    assertFalse(_receiver.is_float_open());
  }

  @Test
  public void colon_browser_with_url()
  {
    buffer("abc", 0);
    press_escape();
    press(":");
    press("b");
    press("r");
    press(" ");
    press("x");
    press("enter");
    assertTrue(_receiver.is_float_open());
    assertEquals("x", _receiver.floatUrl);
  }

  @Test
  public void backspace_in_command_line()
  {
    buffer("abc", 0);
    press_escape();
    press(":");
    press("u");
    press("backspace");
    assertEquals(":", lastStatusText());
    press("l");
    press("o");
    press("w");
    press("e");
    press("r");
    press("enter");
    assertEquals("abc", _conn.text());
  }

  @Test
  public void esc_cancels_command_line()
  {
    buffer("abc", 0);
    press_escape();
    press(":");
    press("x");
    press_escape();
    assertTrue(normal());
    assertEquals("abc", _conn.text());
  }

  @Test
  public void enter_in_normal_moves_down()
  {
    buffer("a\nb\nc", 0);
    press_escape();
    press("enter");
    assertEquals(4, _conn.keyEvents.size());
    assertEquals("a\nb\nc", _conn.text());
  }

  @Test
  public void space_bar_types_in_command_and_search()
  {
    buffer("a b", 0);
    press_escape();
    press("/");
    press("space");
    assertEquals(1, _conn.selStart());
    assertEquals(2, _conn.selEnd());
    buffer("abc", 0);
    press_escape();
    press(":");
    press("space");
    assertEquals(": ", lastStatusText());
  }

  // ---- Help --------------------------------------------------------------

  @Test
  public void question_mark_opens_help()
  {
    buffer("abc", 0);
    press_escape();
    press("?");
    assertEquals(1, _receiver.helpOpened);
    assertTrue(_receiver.lastStatus().contains("ajuda"));
  }
}