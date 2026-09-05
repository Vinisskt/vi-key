package juloo.keyboard2;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
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
    assertEquals(1, lua.count_commands());
    assertArrayEquals(new String[] { "hello" }, lua.command_names());
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
    assertArrayEquals(new String[] { "alpha", "zeta" }, lua.command_names());
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
    assertArrayEquals(new String[] { "p", "q" }, lua.command_names());
  }
}