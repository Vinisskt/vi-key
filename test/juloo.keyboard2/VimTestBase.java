package juloo.keyboard2;

import org.junit.Before;

/** Base for pure-JVM tests driving [KeyEventHandler]/[VimEngine] through a
    fake [IReceiver]. The [Suggestions] component and the [Config] require an
    Android [Context] and are not exercisable on the JVM; the handler is built
    with [null] suggestions, which the production code tolerates. */
public abstract class VimTestBase
{
  protected FakeReceiver _receiver;
  protected KeyEventHandler _handler;
  protected FakeInputConnection _conn;

  @Before
  public void setup_vim()
  {
    _receiver = new FakeReceiver();
    _handler = new KeyEventHandler(_receiver, null);
    _conn = _receiver.conn;
  }

  /** Set the editor text and selection, then press the given keys.
      Keys are names understood by [KeyValue.getKeyByName]. */
  protected void buffer(String text, int sel)
  {
    _conn.set_text(text, sel, sel);
  }

  protected void buffer(String text, int sel_s, int sel_e)
  {
    _conn.set_text(text, sel_s, sel_e);
  }

  protected void press(String name)
  {
    _handler.key_up(TestUtils.key(name), Pointers.Modifiers.EMPTY);
  }

  /** Exit INSERT mode: [ctrl]+[esc]. A plain [esc] is passed to the app
      (Termux, nvim...), so it does not switch modes. */
  protected void press_escape()
  {
    press_with_mods("esc", Pointers.Modifiers.EMPTY.with_extra_mod(TestUtils.key("ctrl")));
  }

  protected void press_char(char c)
  {
    press(Character.toString(c));
  }

  /** Simultaneously press (a latched) system modifier. */
  protected void press_with_mods(String name, Pointers.Modifiers mods)
  {
    _handler.key_up(TestUtils.key(name), mods);
  }

  protected void latch_modifier(String name)
  {
    _handler.mods_changed(Pointers.Modifiers.EMPTY.with_extra_mod(TestUtils.key(name)));
  }

  protected boolean normal()
  {
    return FakeReceiver.is_normal(_receiver.lastStatus());
  }

  protected boolean insert()
  {
    return FakeReceiver.is_insert(_receiver.lastStatus());
  }

  /** The last status text with the trailing NUL + color decoration stripped. */
  protected String lastStatusText()
  {
    String s = _receiver.lastStatus();
    if (s == null)
      return null;
    int i = s.indexOf('\u0000');
    return (i < 0) ? s : s.substring(0, i);
  }
}