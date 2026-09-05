package juloo.keyboard2;

/** Command mode used in VIM mode (the ':' command line).
    Typing ':' starts a command: characters are appended to the command line,
    backspace erases the last character and enter executes the command. The
    command itself is dispatched by [KeyEventHandler.execute_vim_command],
    which is easy to extend with new commands. */
final class CommandBar
{
  final VimEngine _vim;
  final StringBuilder _cmd = new StringBuilder();
  boolean _active = false;

  CommandBar(VimEngine vim)
  {
    _vim = vim;
  }

  String command()
  {
    return _cmd.toString();
  }

  boolean active()
  {
    return _active;
  }

  void begin()
  {
    reset();
    _active = true;
  }

  void reset()
  {
    _active = false;
    _cmd.setLength(0);
  }

  void type(char c)
  {
    if (!_active)
      return;
    _cmd.append(c);
    _vim.update_status();
  }

  void backspace()
  {
    if (!_active)
      return;
    int n = _cmd.length();
    if (n > 0)
    {
      _cmd.setLength(n - 1);
      _vim.update_status();
    }
  }

  void cancel()
  {
    reset();
    _vim.set_mode(VimEngine.MODE_NORMAL);
  }

  void execute()
  {
    if (!_active)
      return;
    String cmd = _cmd.toString().trim();
    reset();
    _vim.set_mode(VimEngine.MODE_NORMAL);
    if (!cmd.isEmpty())
      _vim._handler.execute_vim_command(cmd);
  }
}