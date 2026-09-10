package com.vinisskt.vikey;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Smoke test for the pure-JVM Vim test harness: INSERT typing, NORMAL-mode
    commands and word-motion (which goes through [setSelection]). */
public class VimHarnessSmokeTest extends VimTestBase
{
  @Test
  public void typing_in_insert_commits_text()
  {
    buffer("", 0);
    assertTrue(insert());
    press("h");
    press("e");
    press("i");
    press("o");
    assertEquals("heio", _conn.text());
  }

  @Test
  public void esc_switches_to_normal_and_l_emits_dpad_events()
  {
    buffer("abcd", 0);
    assertTrue(insert());
    press_escape();
    assertTrue(normal());
    press("l");
    press("l");
    assertEquals(6, _conn.keyEvents.size());
  }

  @Test
  public void w_word_motion_moves_selection()
  {
    buffer("hello world", 0);
    press_escape();
    press("w");
    assertEquals(6, _conn.selStart());
  }

  @Test
  public void jk_quick_escape_switches_to_normal()
  {
    buffer("x", 0);
    press("j");
    press("k");
    assertTrue(normal());
    // The pending 'j' must not be committed.
    assertEquals("x", _conn.text());
  }
}