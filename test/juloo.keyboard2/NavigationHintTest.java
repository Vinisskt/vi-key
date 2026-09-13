package com.vinisskt.vikey;

import org.junit.Test;
import static org.junit.Assert.*;

/** Command hint shown at the right of the status bar in NORMAL mode while a
    command is being composed: the valid completion keys, so the user learns
    the navigation shortcuts. Empty in every other case. */
public class NavigationHintTest extends VimTestBase
{
  @Test
  public void no_hint_in_insert_mode()
  {
    buffer("abc", 0);
    press_char('x');
    assertEquals("", lastHint());
  }

  @Test
  public void no_hint_when_idle_in_normal_mode()
  {
    buffer("abc", 0);
    press_escape();
    assertEquals("", lastHint());
  }

  @Test
  public void d_operator_shows_deletions()
  {
    buffer("abc", 0);
    press_escape();
    press("d");
    assertEquals("dd dw de d0 d$", lastHint());
  }

  @Test
  public void completing_the_delete_clears_the_hint()
  {
    buffer("abc", 0);
    press_escape();
    press("d");
    press("d");
    assertEquals("", lastHint());
  }

  @Test
  public void pending_g_shows_gg()
  {
    buffer("abc", 0);
    press_escape();
    press("g");
    assertEquals("gg G", lastHint());
  }

  @Test
  public void completing_gg_clears_the_hint()
  {
    buffer("abc", 0);
    press_escape();
    press("g");
    press("g");
    assertEquals("", lastHint());
  }

  @Test
  public void count_shows_motions()
  {
    buffer("hello world foo", 0);
    press_escape();
    press("3");
    assertEquals("h j k l w b e G 0 $", lastHint());
    press("w");
    assertEquals("", lastHint());
  }

  @Test
  public void no_hint_after_reaching_insert_mode()
  {
    buffer("abc", 0);
    press_escape();
    press("i");
    assertEquals("", lastHint());
  }
}