package com.vinisskt.vikey;

import org.junit.Test;
import static org.junit.Assert.*;

/** Multi-tap accent (PT-BR): 1 toque = letra; 2+ toques rápidos na mesma
    tecla substituem a letra pelo acento (2=agudo, 3=circunflexo, 4=til,
    5=grave; c: 2=ç). A repetição por segurar não conta como toque. */
public class MultiTapAccentTest extends VimTestBase
{
  /** Toque real: key_down seguido de key_up (como um dedo faz). */
  private void tap(char c)
  {
    KeyValue kv = TestUtils.key(String.valueOf(c));
    _handler.key_down(kv, false);
    _handler.key_up(kv, Pointers.Modifiers.EMPTY);
  }

  @Test
  public void single_tap_types_plain_letter()
  {
    buffer("", 0);
    tap('a');
    assertEquals("a", _conn.text());
  }

  @Test
  public void double_tap_types_acute()
  {
    buffer("", 0);
    tap('a');
    tap('a');
    assertEquals("á", _conn.text());
  }

  @Test
  public void triple_tap_types_circumflex()
  {
    buffer("", 0);
    tap('a');
    tap('a');
    tap('a');
    assertEquals("â", _conn.text());
  }

  @Test
  public void four_taps_types_tilde()
  {
    buffer("", 0);
    for (int i = 0; i < 4; i++) tap('o');
    assertEquals("õ", _conn.text());
  }

  @Test
  public void five_taps_types_grave()
  {
    buffer("", 0);
    for (int i = 0; i < 5; i++) tap('a');
    assertEquals("à", _conn.text());
  }

  @Test
  public void double_tap_c_types_cedilla()
  {
    buffer("", 0);
    tap('c');
    tap('c');
    assertEquals("ç", _conn.text());
  }

  @Test
  public void slow_second_tap_types_letter_twice()
  {
    buffer("", 0);
    tap('a');
    try { Thread.sleep(VimEngine.MULTI_TAP_TIMEOUT_MS + 50); } catch (InterruptedException e) {}
    tap('a');
    assertEquals("aa", _conn.text());
  }

  @Test
  public void different_letter_breaks_the_sequence()
  {
    buffer("", 0);
    tap('a');
    tap('b');
    tap('a');
    assertEquals("aba", _conn.text());
  }

  @Test
  public void space_breaks_the_sequence()
  {
    buffer("", 0);
    tap('a');
    tap('a');
    assertEquals("á", _conn.text());
    tap(' ');
    tap('a');
    tap('a');
    assertEquals("á á", _conn.text());
  }

  @Test
  public void hold_repeat_does_not_count_as_tap()
  {
    buffer("", 0);
    // Segurar: key_down, depois repetições de key_up (umas chamadas do
    // on_multi_tap_pointer_repeat) e por fim o release.
    KeyValue kv = TestUtils.key("a");
    _handler.key_down(kv, false);
    _handler.on_multi_tap_pointer_repeat();
    _handler.key_up(kv, Pointers.Modifiers.EMPTY);   // 1a repetição
    _handler.on_multi_tap_pointer_repeat();
    _handler.key_up(kv, Pointers.Modifiers.EMPTY);   // 2a repetição
    assertEquals("aa", _conn.text());
  }

  @Test
  public void accent_works_with_pending_letter_in_word()
  {
    buffer("", 0);
    tap('b');
    tap('o');
    tap('o');
    assertEquals("bó", _conn.text());
  }

  @Test
  public void holds_keep_typing_after_accent()
  {
    buffer("", 0);
    tap('a');
    tap('a');                       // vira 'á'
    assertEquals("á", _conn.text());
    try { Thread.sleep(VimEngine.MULTI_TAP_TIMEOUT_MS + 50); } catch (InterruptedException e) {}
    tap('a');
    assertEquals("áa", _conn.text());
  }

  @Test
  public void numbers_are_not_accented()
  {
    buffer("", 0);
    tap('1');
    tap('1');
    assertEquals("11", _conn.text());
  }
}