package juloo.keyboard2;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The pure-JVM [LuaEngine]: loading scripts from a directory, registering
    commands and running the [vim.*] API against the fake editor. */
public class LuaEngineTest extends VimTestBase
{
  File dir;
  LuaEngine lua;

  LuaEngine new_engine()
  {
    try
    {
      dir = Files.createTempDirectory("lua-engine").toFile();
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
    lua = new LuaEngine(_handler, dir);
    return lua;
  }

  void write_script(String name, String content)
  {
    try
    {
      File f = new File(dir, name);
      java.io.FileOutputStream out = new java.io.FileOutputStream(f);
      out.write(content.getBytes("UTF-8"));
      out.close();
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void empty_dir_has_no_commands()
  {
    new_engine();
    assertEquals(0, lua.count_commands());
    assertEquals(0, lua.command_names().length);
    assertFalse(lua.execute("anything", ""));
  }

  @Test
  public void registered_command_runs_and_flashes_status()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('hello', function(args) vim.status('hello ' .. args) end)");
    lua.reload();
    assertEquals(2, lua.count_commands());
    assertArrayEquals(new String[] { "a", "hello" }, lua.command_names());
    assertTrue(lua.execute("hello", "world"));
    assertTrue(_receiver.lastStatus().startsWith("hello world"));
  }

  @Test
  public void commands_are_sorted_in_command_names()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('zeta', function() end)\nvim.register('alpha', function() end)");
    lua.reload();
    assertArrayEquals(new String[] { "a", "alpha", "zeta" }, lua.command_names());
  }

  @Test
  public void invalid_lua_flashes_error()
  {
    new_engine();
    write_script("bad.lua", "this is not lua !!");
    lua.reload();
    assertTrue(_receiver.lastStatus().startsWith("lua: "));
  }

  @Test
  public void scripts_in_plugins_subfolder_are_loaded()
  {
    new_engine();
    File plugins = new File(dir, "plugins");
    assertTrue(plugins.mkdir());
    write_script("root.lua", "vim.status('root')");
    File p = new File(plugins, "extra.lua");
    try
    {
      java.io.FileOutputStream out = new java.io.FileOutputStream(p);
      out.write("vim.status('plugin')\nvim.register('plug', function() end)".getBytes("UTF-8"));
      out.close();
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
    lua.reload();
    assertArrayEquals(new String[] { "extra", "plug", "root" }, lua.command_names());
  }

  void write_nested_script(String subpath, String content)
  {
    try
    {
      File f = new File(dir, subpath);
      f.getParentFile().mkdirs();
      java.io.FileOutputStream out = new java.io.FileOutputStream(f);
      out.write(content.getBytes("UTF-8"));
      out.close();
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void init_lua_requires_plugins_instead_of_loading_flat_files()
  {
    new_engine();
    write_script("init.lua",
        "require('mod')\n");
    write_script("unrelated.lua",
        "vim.register('flat', function() end)");
    write_nested_script("plugins/mod.lua",
        "vim.register('mod', function(args) vim.status('mod ' .. args) end)");
    lua.reload();
    // Only the commands registered by the required modules exist; unrelated.lua
    // (flat, non-required) must not be loaded and "init" is not a command.
    assertArrayEquals(new String[] { "mod" }, lua.command_names());
    assertTrue(lua.execute("mod", "ok"));
    assertTrue(_receiver.lastStatus().startsWith("mod ok"));
  }

  @Test
  public void interval_and_clear_interval_wired_through_commands()
  {
    new_engine();
    write_script("ivl.lua",
        "handle = nil\n" +
        "vim.register('mk', function()\n" +
        "  handle = vim.interval(1000, function() end)\n" +
        "end)\n" +
        "vim.register('stop_ivl', function() vim.clear_interval(handle) end)\n");
    lua.reload();
    // Creating and cancelling an interval through the Lua API must not error.
    assertTrue(lua.execute("mk", ""));
    assertTrue(lua.execute("stop_ivl", ""));
    // A reload with live timers cancels them without error.
    assertTrue(lua.execute("mk", ""));
    lua.reload();
  }

  @Test
  public void get_text_exposed_to_scripts()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('echo', function() vim.status(vim.get_text()) end)");
    lua.reload();
    buffer("hello vim", 0);
    lua.execute("echo", "");
    assertTrue(_receiver.lastStatus().startsWith("hello vim"));
  }

  @Test
  public void send_inserts_text_at_cursor()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('ins', function(t) vim.send(t) end)");
    lua.reload();
    buffer("abz", 2);
    lua.execute("ins", "XY");
    assertEquals("abXYz", _conn.text());
  }

  @Test
  public void set_sel_moves_the_cursor()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('jump', function() vim.set_sel(2, 2) end)");
    lua.reload();
    buffer("abcde", 4);
    lua.execute("jump", "");
    assertEquals(2, _conn.selStart());
    assertEquals(2, _conn.selEnd());
  }

  @Test
  public void replace_rewrites_a_range()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('sub', function(s) vim.replace(1, 3, s) end)");
    lua.reload();
    buffer("abcdef", 5);
    lua.execute("sub", "Z");
    // replace(1, 3, "Z") -> delete chars [1,3), insert "Z".
    assertEquals("aZdef", _conn.text());
    // Replacement must not rely on deleteSurroundingText: commitText over the
    // selection replaces it, which works even when the editor ignores the
    // after length (e.g. Termux).
    assertTrue(_conn.deletions.isEmpty());
    assertEquals(2, _conn.selStart());
    assertEquals(2, _conn.selEnd());
  }

  @Test
  public void save_and_delete_scripts()
  {
    new_engine();
    assertEquals(0, lua.count_commands());
    lua.save_script("mycmd",
        "vim.register('mycmd', function() vim.status('saved!') end)");
    assertEquals(1, lua.count_commands());
    lua.execute("mycmd", "");
    assertTrue(_receiver.lastStatus().startsWith("saved!"));
    lua.delete_script("mycmd");
    assertEquals(0, lua.count_commands());
  }

  @Test
  public void save_script_adds_lua_suffix()
  {
    new_engine();
    lua.save_script("other", "vim.register('other', function() end)");
    assertTrue(new File(dir, "other.lua").exists());
  }

  @Test
  public void delete_missing_script_flashes()
  {
    new_engine();
    lua.delete_script("nope");
    assertTrue(_receiver.lastStatus().contains("no script"));
  }

  @Test
  public void execute_unknown_command_returns_false()
  {
    new_engine();
    assertFalse(lua.execute("unknown", ""));
  }

  @Test
  public void script_error_flashes_message()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('boom', function() error('kaboom') end)");
    lua.reload();
    assertTrue(lua.execute("boom", ""));
    assertTrue(_receiver.lastStatus().startsWith("lua: "));
  }

  @Test
  public void scripts_load_in_filename_order()
  {
    new_engine();
    write_script("b.lua", "vim.register('q', function() vim.status('b') end)");
    write_script("a.lua", "vim.register('p', function() vim.status('a') end)");
    lua.reload();
    assertArrayEquals(new String[] { "a", "b", "p", "q" }, lua.command_names());
  }

  @Test
  public void get_sel_exposed_to_scripts()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('sel', function() local s, e = vim.get_sel() vim.status(s .. ',' .. e) end)");
    lua.reload();
    buffer("hello vim", 5, 5);
    lua.execute("sel", "");
    assertTrue(_receiver.lastStatus().startsWith("5,5"));
  }

  /** A receiver whose current input connection is always [null]. */
  static final class NoConnReceiver implements KeyEventHandler.IReceiver
  {
    final List<String> statuses = new ArrayList<String>();
    @Override public android.view.inputmethod.InputConnection getCurrentInputConnection() { return null; }
    @Override public void handle_event_key(KeyValue.Event ev) {}
    @Override public void set_shift_state(boolean s, boolean l) {}
    @Override public void set_compose_pending(boolean p) {}
    @Override public void selection_state_changed(boolean s) {}
    @Override public android.os.Handler getHandler() { return new android.os.Handler(android.os.Looper.getMainLooper()); }
    @Override public android.content.Context getApplicationContext() { return null; }
    @Override public void set_vim_status(String text, int color) { statuses.add(text + "\u0000" + color); }
    @Override public boolean is_float_open() { return false; }
    @Override public void toggle_float_panel(String url) {}
    @Override public void close_float_panel() {}
    @Override public void open_help() {}
    @Override public void open_page(String title, String html) {}
    @Override public void set_suggestions(juloo.keyboard2.suggestions.Suggestions s) {}
    String lastStatus() { return statuses.isEmpty() ? null : statuses.get(statuses.size() - 1); }
  }

  @Test
  public void get_sel_without_connection_returns_none()
  {
    NoConnReceiver recv = new NoConnReceiver();
    _handler = new KeyEventHandler(recv, null);
    new_engine();
    write_script("a.lua",
        "vim.register('sel', function() vim.status(vim.get_sel()) end)");
    lua.reload();
    lua.execute("sel", "");
    assertTrue(recv.lastStatus().startsWith("nil"));
  }

  @Test
  public void get_text_without_connection_returns_empty()
  {
    NoConnReceiver recv = new NoConnReceiver();
    _handler = new KeyEventHandler(recv, null);
    new_engine();
    write_script("a.lua",
        "vim.register('txt', function() vim.status('[' .. vim.get_text() .. ']') end)");
    lua.reload();
    lua.execute("txt", "");
    assertTrue(recv.lastStatus().startsWith("[]"));
  }

  @Test
  public void replace_with_invalid_range_is_ignored()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('rep', function() vim.replace(3, 1, 'Z') end)");
    lua.reload();
    buffer("abcdef", 5);
    lua.execute("rep", "");
    // end < start: the replacement is rejected.
    assertEquals("abcdef", _conn.text());
  }

  @Test
  public void empty_register_name_is_ignored()
  {
    new_engine();
    write_script("a.lua", "vim.register('', function() vim.status('x') end)");
    lua.reload();
    // The empty [vim.register] name is ignored; only the file command remains.
    assertEquals(1, lua.count_commands());
    assertArrayEquals(new String[] { "a" }, lua.command_names());
  }

  @Test
  public void register_non_function_argument_is_ignored()
  {
    new_engine();
    write_script("a.lua", "vim.register('notfunc', 42)");
    lua.reload();
    assertEquals(1, lua.count_commands());
    assertArrayEquals(new String[] { "a" }, lua.command_names());
  }

  @Test
  public void file_name_is_a_command()
  {
    new_engine();
    write_script("greet.lua", "local a = ... vim.status('greet ' .. tostring(a))");
    lua.reload();
    assertTrue(lua.execute("greet", "world"));
    assertTrue(_receiver.lastStatus().startsWith("greet world"));
  }

  @Test
  public void file_command_without_arguments()
  {
    new_engine();
    write_script("greet.lua", "local a = ... vim.status('greet ' .. tostring(a))");
    lua.reload();
    assertTrue(lua.execute("greet", ""));
    assertTrue(_receiver.lastStatus().startsWith("greet nil"));
  }

  @Test
  public void command_name_drops_lua_suffix_and_lowercases()
  {
    new_engine();
    write_script("MyScript.lua", "vim.status('ran')");
    lua.reload();
    assertArrayEquals(new String[] { "myscript" }, lua.command_names());
    assertTrue(lua.execute("myscript", ""));
    assertTrue(_receiver.lastStatus().startsWith("ran"));
  }

  @Test
  public void script_registered_command_takes_precedence_over_file_name()
  {
    new_engine();
    write_script("cmd.lua",
        "vim.register('cmd', function() vim.status('registered') end)");
    lua.reload();
    assertTrue(lua.execute("cmd", ""));
    assertTrue(_receiver.lastStatus().startsWith("registered"));
  }

  @Test
  public void top_level_code_runs_at_startup()
  {
    new_engine();
    write_script("boot.lua", "vim.status('booted')");
    lua.reload();
    assertTrue(_receiver.lastStatus().startsWith("booted"));
  }

  @Test
  public void reload_with_missing_directory_is_safe()
  {
    new_engine();
    // lua dir has a valid listing already; loading from a deleted dir is safe.
    File gone = new File(dir, "gone");
    dir.delete();
    LuaEngine e = new LuaEngine(_handler, new File(gone, "sub"));
    assertEquals(0, e.count_commands());
  }

  @Test
  public void save_script_creates_directory()
  {
    File nested = new File(dir, "a/b");
    new_engine();
    lua = new LuaEngine(_handler, nested);
    lua.save_script("x", "vim.register('x', function() end)");
    assertEquals(1, lua.count_commands());
  }

  @Test
  public void save_script_flashes_when_directory_cannot_be_made()
  {
    File blocker = new File(dir, "blocker");
    try { new java.io.FileOutputStream(blocker).close(); }
    catch (Exception e) { throw new RuntimeException(e); }
    // "blocker/script" can't be created because "blocker" is a file.
    lua = new LuaEngine(_handler, new File(blocker, "sub"));
    lua.save_script("x", "vim.register('x', function() end)");
    assertTrue(_receiver.lastStatus().contains("lua:"));
  }

  @Test
  public void save_script_flashes_when_target_is_a_directory()
  {
    new_engine();
    // "adir.lua" is the requested target (suffix added), but it's a directory.
    new File(dir, "adir.lua").mkdir();
    lua.save_script("adir", "vim.register('x', function() end)");
    assertTrue(_receiver.lastStatus().contains("lua:"));
  }

  @Test
  public void page_shows_text_escaped_in_a_browser_page()
  {
    new_engine();
    write_script("a.lua",
        "vim.register('show', function() vim.page('cpu <amd> & \"ram\"') end)");
    lua.reload();
    lua.execute("show", "");
    assertEquals(1, _receiver.pages.size());
    String shown = _receiver.pages.get(0);
    assertTrue(shown.startsWith("out\u0000"));
    assertTrue(shown.contains("cpu &lt;amd&gt; &amp; &quot;ram&quot;"));
    assertTrue(shown.contains("<pre>"));
  }

  @Test
  public void page_shows_plain_text_when_content_is_simple()
  {
    new_engine();
    write_script("a.lua", "vim.register('cat', function() vim.page('hello out') end)");
    lua.reload();
    lua.execute("cat", "");
    assertEquals(1, _receiver.pages.size());
    assertTrue(_receiver.pages.get(0).contains("hello out"));
  }

  @Test
  public void replace_with_null_extract_returns_base_minus_one()
  {
    new_engine();
    _handler = new KeyEventHandler(new NoConnReceiver(), null);
    lua = new LuaEngine(_handler, dir);
    lua.save_script("b", "vim.register('rep', function() vim.replace(1, 3, 'Z') end)");
    lua.execute("rep", "");
    // replace(1, 3, "Z") with base=-1: the guard rejects the replacement.
  }
}