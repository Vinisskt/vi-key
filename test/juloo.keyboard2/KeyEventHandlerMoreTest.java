package com.vinisskt.vikey;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** [KeyEventHandler] methods that were previously uncovered: [started],
    [vim_help_page_names], [send_key_down_up_repeat], [cancel_selection],
    [get_clipboard_text] and [paste_from_clipboard_pane]. */
public class KeyEventHandlerMoreTest extends VimTestBase
{
  @Test
  public void started_resets_handler_state()
  {
    buffer("abc", 3);
    press("a");
    assertEquals("abca", _conn.text());
    Config conf = mock(Config.class);
    try
    {
      Config.class.getField("editor_config").set(conf, new EditorConfig());
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
    _handler.started(conf);
    // Typing still works after the restart.
    press("b");
    assertEquals("abcab", _conn.text());
  }

  @Test
  public void vim_help_page_names_all_resolve()
  {
    String[] names = KeyEventHandler.vim_help_page_names();
    assertNotNull(names);
    assertTrue(names.length > 0);
    for (String n : names)
    {
      assertNotNull(n);
      String html = KeyEventHandler.vim_help_page_html(n);
      assertNotNull(html);
      assertTrue(html.trim().length() > 0);
    }
  }

  @Test
  public void send_key_down_up_repeat_emits_down_up_pairs()
  {
    _handler.send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, 3);
    // Down + up for each repeat (the JVM [KeyEvent] stub cannot distinguish
    // the actions, only the count is observable).
    assertEquals(6, _conn.keyEvents.size());
  }

  @Test
  public void cancel_selection_collapses_selection_and_notifies()
  {
    buffer("abc", 1, 3);
    _handler.cancel_selection();
    assertEquals(1, _conn.selStart());
    assertEquals(1, _conn.selEnd());
    assertFalse(_receiver.selectionStates.isEmpty());
    assertFalse(_receiver.selectionStates.get(_receiver.selectionStates.size() - 1));
  }

  @Test
  public void get_clipboard_text_without_context_is_null()
  {
    // [FakeReceiver.getApplicationContext] returns null.
    assertNull(_handler.get_clipboard_text());
  }

  @Test
  public void paste_from_clipboard_pane_types_the_content()
  {
    buffer("", 0);
    _handler.paste_from_clipboard_pane("cli&board");
    assertEquals("cli&board", _conn.text());
  }
}