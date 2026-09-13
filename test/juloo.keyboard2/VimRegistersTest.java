package com.vinisskt.vikey;

import org.junit.Test;
import static org.junit.Assert.*;

/** Clipboard registers and the vim yank/paste shortcuts: [yy]/[yw]/[ye]/
    [y0]/[y$] copy text to the unnamed register (or the one selected with
    ["a]..["z]), [P] paste before the cursor, and operator deletes feed the
    unnamed register so they can be pasted back.  [p] works the same on a
    real device but the fake input connection does not simulate DPAD cursor
    movement. */
public class VimRegistersTest extends VimTestBase
{
  /** Press ["] followed by the register name [r]. */
  private void quote(char r)
  {
    _handler.key_up(KeyValue.makeCharKey('"'), Pointers.Modifiers.EMPTY);
    press_char(r);
  }

  @Test
  public void yy_copies_current_line()
  {
    buffer("hello world\nfoo bar", 2);
    press_escape();
    press("y");
    press("y");
    assertEquals("hello world", _handler._vim._unnamed_register);
  }

  @Test
  public void yw_copies_with_separators_up_to_next_word()
  {
    buffer("hello world foo", 2);
    press_escape();
    press("y");
    press("w");
    assertEquals("llo ", _handler._vim._unnamed_register);
  }

  @Test
  public void y_operator_hint_shows_completions()
  {
    buffer("hello world foo", 0);
    press_escape();
    press("y");
    assertEquals("yy yw ye y0 y$", lastHint());
  }

  @Test
  public void P_pastes_register_before_cursor()
  {
    buffer("hello world\nfoo bar", 0);
    press_escape();
    press("y");
    press("y");
    press("P");
    assertEquals("hello world" + "hello world\nfoo bar", _conn.text());
  }

  @Test
  public void named_register_keeps_the_yank()
  {
    buffer("aaaa\nbbbb", 0);
    press_escape();
    quote('a');
    press("y");
    press("y");
    assertEquals("aaaa", _handler._vim._registers.get('a'));
    assertEquals("aaaa", _handler._vim._unnamed_register);
  }

  @Test
  public void named_register_pastes_from_that_register()
  {
    buffer("aaaa\nbbbb", 0);
    press_escape();
    quote('a');
    press("y");
    press("y");
    press("2");
    press("G");
    quote('b');
    press("y");
    press("y");
    assertEquals("bbbb", _handler._vim._registers.get('b'));
    quote('a');
    press("P");
    assertEquals("aaaa\naaaa" + "bbbb", _conn.text());
  }

  @Test
  public void dd_feeds_register_and_P_restores_the_line()
  {
    buffer("aaa\nbbb", 0);
    press_escape();
    press("d");
    press("d");
    assertEquals("aaa\n", _handler._vim._unnamed_register);
    assertEquals("bbb", _conn.text());
    press("P");
    assertEquals("aaa\nbbb", _conn.text());
  }

  @Test
  public void x_feeds_the_unnamed_register()
  {
    buffer("abc", 0);
    press_escape();
    press("x");
    assertEquals("a", _handler._vim._unnamed_register);
  }
}