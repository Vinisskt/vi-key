package com.vinisskt.vikey;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Lua scripting support for the Vim command line.
    [.lua] files dropped in the [keyboard-lua] directory of the user visible
    storage (or in the app private directory [files/lua] when that folder is
    not accessible) are loaded at startup (or when the [reload] command is
    run). Scripts can also be kept in the [keyboard-lua/plugins] subfolder,
    which is scanned as well. Every script can be run from the command line
    as [:<name>] where [name] is the file name without the [.lua] extension;
    the command line arguments are passed to the script as the Lua vararg
    [...]. Scripts can also register named commands through
    [vim.register("name", function(args) ...)]. Import of new scripts can
    also be done from the command line through [addlua] (which saves the
    content of the system clipboard as a script) and [rmlua].

    API exposed to scripts in the global table [vim]:
      - [vim.register(name, fn)]: register a command reachable as [:<name>]
      - [vim.get_text()]: the full text of the current editor
      - [vim.get_sel()]: start and end of the selection, relative to the text
      - [vim.set_sel(start, end)]: move the cursor/selection (relative)
      - [vim.replace(start, end, text)]: replace a range (relative)
      - [vim.send(text)]: insert text at the cursor
      - [vim.copy(text)] / [vim.paste()] / [vim.clipboard()]: system clipboard
      - [vim.status(text)]: show a transient message in the keyboard status bar
      - [vim.page(text)]: open (or update) a browser page showing [text] as
        plain text, styled like the help page
      - [vim.interval(ms, fn)]: call [fn] on the keyboard queue every [ms]
        milliseconds (non blocking); returns a handle to pass to
        [vim.clear_interval]. Timers are cancelled by [reload]. [fn] must be
        fast (short file reads, updates); it runs on the keyboard main thread.
      - [vim.clear_interval(handle)]: cancel a timer created by
        [vim.interval].
      - [vim.set_mode(mode)]: switch the keyboard mode ("insert", "normal",
        "scroll"); "scroll" sends DPAD events for j/k, useful to scroll lists.
    Positions are relative to the beginning of the text returned by
    [vim.get_text()]. */
final class LuaEngine
{
  final KeyEventHandler _handler;
  File _lua_dir;
  final Globals _globals;
  final Map<String, LuaValue> _commands = new HashMap<String, LuaValue>();
  final Map<Integer, TimerHandle> _intervals = new HashMap<Integer, TimerHandle>();
  int _next_interval_id = 1;

  LuaEngine(KeyEventHandler handler, Context appCtx)
  {
    this(handler, pick_lua_dir(handler, appCtx));
  }

  /** Directory of the Lua scripts in the user visible storage, or the app
      private directory when the user storage is not accessible. */
  static File pick_lua_dir(KeyEventHandler handler, Context appCtx)
  {
    File user = user_lua_dir();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && !Environment.isExternalStorageManager())
    {
      flash_no_access(handler);
      return new File(appCtx.getApplicationContext().getFilesDir(), "lua");
    }
    if (!ensure_user_lua_dir())
      return new File(appCtx.getApplicationContext().getFilesDir(), "lua");
    return user;
  }

  /** [Storage]/keyboard-lua: the scripts folder of the user visible storage. */
  static File user_lua_dir()
  {
    return new File(Environment.getExternalStorageDirectory(), "keyboard-lua");
  }

  static void flash_no_access(KeyEventHandler handler)
  {
    if (handler != null)
      handler._vim.flash_status("grant All files access to use /sdcard/keyboard-lua",
          VimEngine.STATUS_COLOR_CMD);
  }

  /** Create the scripts folder of the user storage if it does not exist yet.
      Requires All files access on Android 11+; silently fails otherwise. */
  static boolean ensure_user_lua_dir()
  {
    File dir = user_lua_dir();
    return dir.isDirectory() || dir.mkdirs();
  }

  LuaEngine(KeyEventHandler handler, File luaDir)
  {
    _handler = handler;
    _lua_dir = luaDir;
    _globals = JsePlatform.standardGlobals();
    if (_globals.compiler == null)
      LuaC.install(_globals);
    setup_vim_api();
    setup_package_path();
    reload();
  }

  /** Reload every script in the lua directory. The previously registered
      commands are cleared. When All files access has been granted since the
      engine was created, switch to the user storage directory. */
  void reload()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && Environment.isExternalStorageManager()
        && !_lua_dir.equals(user_lua_dir()))
    {
      _lua_dir = user_lua_dir();
      ensure_user_lua_dir();
      setup_package_path();
    }
    _commands.clear();
    clear_intervals();
    File init_file = new File(_lua_dir, "init.lua");
    if (init_file.exists() && init_file.isFile())
    {
      // [init.lua] is the entry point of the plugins: it require()s the
      // modules of the [plugins] subfolder ([package.path] is set by
      // [setup_package_path]). Reset the require cache so that a reload
      // re-runs every module.
      _globals.get("package").set("loaded", new LuaTable());
      load_script(init_file);
      return;
    }
    List<File> files = new ArrayList<File>();
    add_files(files, _lua_dir);
    add_files(files, new File(_lua_dir, "plugins"));
    Collections.sort(files);
    for (File f : files)
      load_script(f);
  }

  /** Extend [package.path] so that Lua modules loaded from [init.lua] can be
      required from the script folder itself and from its [plugins]
      subfolder: [require("name")] resolves to [dir/name.lua] and
      [dir/plugins/name.lua]. */
  void setup_package_path()
  {
    String root = _lua_dir.getPath();
    String base = _globals.get("package").get("path").tojstring();
    _globals.get("package").set("path",
        base + ";" + root + "/?.lua;" + root + "/plugins/?.lua");
  }

  /** Append the [.lua] script files of [dir] (when it exists) to [out]. */
  static void add_files(List<File> out, File dir)
  {
    File[] files = (dir == null) ? null : dir.listFiles();
    if (files == null)
      return;
    Arrays.sort(files);
    for (File f : files)
      if (f.isFile() && f.getName().endsWith(".lua"))
        out.add(f);
  }

  /** Path of the directory the scripts are loaded from. */
  String current_dir()
  {
    return _lua_dir.getPath();
  }

  void load_script(File file)
  {
    try
    {
      byte[] data = new byte[(int)file.length()];
      FileInputStream in = new FileInputStream(file);
      try
      {
        int off = 0;
        while (off < data.length)
        {
          int n = in.read(data, off, data.length - off);
          if (n < 0)
            break;
          off += n;
        }
      }
      finally
      {
        in.close();
      }
      LuaValue chunk = _globals.load(new ByteArrayInputStream(data),
          file.getName(), "t", _globals);
      chunk.call();
      String name = command_name_of_file(file.getName());
      if (!name.isEmpty() && !name.equals("init") && !_commands.containsKey(name))
        _commands.put(name, new ScriptCommand(chunk));
    }
    catch (IOException ex)
    {
      flash("lua: cannot read " + file.getName());
    }
    catch (Exception ex)
    {
      flash("lua: " + ex.getMessage());
    }
  }

  /** The command name of a script file: its file name without the [.lua]
      extension, lowercased. */
  static String command_name_of_file(String fileName)
  {
    String name = fileName;
    if (name.toLowerCase(Locale.ROOT).endsWith(".lua"))
      name = name.substring(0, name.length() - 4);
    return name.toLowerCase(Locale.ROOT);
  }

  /** A command that re-runs the script chunk with the command line arguments
      as the Lua vararg [...]. */
  static final class ScriptCommand extends VarArgFunction
  {
    final LuaValue _chunk;

    ScriptCommand(LuaValue chunk)
    {
      _chunk = chunk;
    }

    @Override public Varargs invoke(Varargs args)
    {
      return _chunk.invoke(args);
    }
  }

  /** Run the command [name] with the arguments [args]. Returns [false] when
      no script registered this command. */
  boolean execute(String name, String args)
  {
    LuaValue fn = _commands.get(name);
    if (fn == null)
      return false;
    try
    {
      if (args.isEmpty())
        fn.call();
      else
        fn.call(LuaValue.valueOf(args));
      return true;
    }
    catch (Exception ex)
    {
      flash("lua: " + ex.getMessage());
      return true;
    }
  }

  /** Save [content] as a script named [name] (with the .lua suffix added if
      missing) and reload all scripts. */
  void save_script(String name, String content)
  {
    if (!name.endsWith(".lua"))
      name += ".lua";
    File file = new File(_lua_dir, name);
    try
    {
      if (!_lua_dir.isDirectory() && !_lua_dir.mkdirs())
        throw new IOException("mkdir failed");
      FileOutputStream out = new FileOutputStream(file);
      try
      {
        out.write(content.getBytes("UTF-8"));
      }
      finally
      {
        out.close();
      }
      reload();
      flash("saved " + name);
    }
    catch (IOException ex)
    {
      flash("lua: " + ex.getMessage());
    }
  }

  void delete_script(String name)
  {
    if (!name.endsWith(".lua"))
      name += ".lua";
    File file = new File(_lua_dir, name);
    if (file.exists() && file.delete())
    {
      reload();
      flash("deleted " + name);
    }
    else
      flash("no script " + name);
  }

  /** Names of the currently registered commands, sorted. */
  String[] command_names()
  {
    ArrayList<String> names = new ArrayList<String>(_commands.keySet());
    Collections.sort(names);
    return names.toArray(new String[names.size()]);
  }

  int count_commands()
  {
    return _commands.size();
  }

  void flash(String text)
  {
    _handler._vim.flash_status(text, VimEngine.STATUS_COLOR_CMD);
  }

  // ---- The 'vim' table exposed to Lua scripts ---------------------------

  void setup_vim_api()
  {
    LuaTable vim = new LuaTable();
    _globals.set("vim", vim);
    vim.set("register", new VarArgFunction() {
      @Override public Varargs invoke(Varargs args) {
        String name = args.arg1().tojstring().toLowerCase();
        LuaValue fn = args.arg(2);
        if (name.isEmpty() || !fn.isfunction())
          return LuaValue.NONE;
        _commands.put(name, fn);
        return LuaValue.NONE;
      }
    });
    vim.set("get_sel", new VarArgFunction() {
      @Override public Varargs invoke(Varargs args) {
        ExtractedText et = current_extracted();
        if (et == null)
          return LuaValue.NONE;
        return LuaValue.varargsOf(new LuaValue[] {
          LuaValue.valueOf(et.selectionStart - et.startOffset),
          LuaValue.valueOf(et.selectionEnd - et.startOffset)
        });
      }
    });
    vim.set("set_sel", new VarArgFunction() {
      @Override public Varargs invoke(Varargs args) {
        InputConnection conn = current_conn();
        if (conn != null)
        {
          int base = current_base();
          if (base >= 0)
            conn.setSelection(base + args.arg1().toint(), base + args.arg(2).toint());
        }
        return LuaValue.NONE;
      }
    });
    vim.set("get_text", new ZeroArgFunction() {
      @Override public LuaValue call() {
        ExtractedText et = current_full_text();
        if (et == null)
          return LuaValue.valueOf("");
        return LuaValue.valueOf(et.text.toString());
      }
    });
    vim.set("send", new OneArgFunction() {
      @Override public LuaValue call(LuaValue s) {
        _handler.send_text(s.tojstring());
        return LuaValue.NONE;
      }
    });
    vim.set("replace", new VarArgFunction() {
      @Override public Varargs invoke(Varargs args) {
        int start = args.arg1().toint();
        int end = args.arg(2).toint();
        String text = args.arg(3).tojstring();
        int base = current_base();
        if (end >= start && base >= 0)
          _handler.replace_surrounding_text_abs(base + start, end - start, text);
        return LuaValue.NONE;
      }
    });
    vim.set("copy", new OneArgFunction() {
      @Override public LuaValue call(LuaValue s) {
        _handler.set_clipboard_text(s.tojstring());
        return LuaValue.NONE;
      }
    });
    vim.set("paste", new ZeroArgFunction() {
      @Override public LuaValue call() {
        _handler.vim_paste_clipboard();
        return LuaValue.NONE;
      }
    });
    vim.set("clipboard", new ZeroArgFunction() {
      @Override public LuaValue call() {
        Context ctx = _handler._recv.getApplicationContext();
        if (ctx != null)
        {
          ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
          if (cm != null && cm.hasPrimaryClip())
          {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0)
            {
              CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
              if (cs != null)
                return LuaValue.valueOf(cs.toString());
            }
          }
        }
        return LuaValue.valueOf("");
      }
    });
    vim.set("status", new OneArgFunction() {
      @Override public LuaValue call(LuaValue s) {
        flash(s.tojstring());
        return LuaValue.NONE;
      }
    });
    vim.set("page", new OneArgFunction() {
      @Override public LuaValue call(LuaValue s) {
        _handler.open_page("out", s.tojstring());
        return LuaValue.NONE;
      }
    });
    vim.set("set_mode", new OneArgFunction() {
      @Override public LuaValue call(LuaValue arg) {
        String mode = arg.tojstring();
        int m;
        switch (mode) {
          case "insert": m = VimEngine.MODE_INSERT; break;
          case "normal": m = VimEngine.MODE_NORMAL; break;
          case "scroll": m = VimEngine.MODE_SCROLL; break;
          default:
            _handler._vim.flash_status("modo: " + mode + " (use insert/normal/scroll)",
                VimEngine.STATUS_COLOR_CMD);
            return LuaValue.NONE;
        }
        _handler._vim.set_mode(m);
        return LuaValue.NONE;
      }
    });
    vim.set("interval", new VarArgFunction() {
      @Override public Varargs invoke(Varargs args) {
        long ms = args.arg1().tolong();
        LuaValue fn = args.arg(2);
        if (ms <= 0 || !fn.isfunction())
          return LuaValue.NONE;
        int id = _next_interval_id++;
        TimerHandle t = new TimerHandle(id, ms, fn);
        _intervals.put(id, t);
        _handler.get_handler().postDelayed(t, ms);
        return LuaValue.valueOf(id);
      }
    });
    vim.set("clear_interval", new OneArgFunction() {
      @Override public LuaValue call(LuaValue id) {
        TimerHandle t = _intervals.remove(id.toint());
        if (t != null)
          t.cancel();
        return LuaValue.NONE;
      }
    });
  }

  /** Cancel every running timer (called by [reload]). */
  void clear_intervals()
  {
    for (TimerHandle t : _intervals.values())
      t.cancel();
    _intervals.clear();
  }

  /** A timer scheduled through [vim.interval]: runs [fn] on the keyboard main
      queue every [periodMs] milliseconds and reschedules itself until
      cancelled (by [vim.clear_interval] or [reload]). Errors cancel the
      timer to avoid error loops. */
  final class TimerHandle implements Runnable
  {
    final int _id;
    final long _periodMs;
    final LuaValue _fn;
    boolean _cancelled;

    TimerHandle(int id, long periodMs, LuaValue fn)
    {
      _id = id;
      _periodMs = periodMs;
      _fn = fn;
    }

    @Override public void run()
    {
      if (_cancelled)
        return;
      try
      {
        _fn.call();
      }
      catch (Exception ex)
      {
        cancel();
        LuaEngine.this.flash("lua interval: " + ex.getMessage());
        return;
      }
      if (!_cancelled)
        _handler.get_handler().postDelayed(this, _periodMs);
    }

    void cancel()
    {
      _cancelled = true;
    }
  }

  InputConnection current_conn()
  {
    return _handler._recv.getCurrentInputConnection();
  }

  ExtractedText current_extracted()
  {
    InputConnection conn = current_conn();
    return (conn == null) ? null : _handler.get_cursor_pos(conn);
  }

  ExtractedText current_full_text()
  {
    InputConnection conn = current_conn();
    return (conn == null) ? null : _handler.get_full_text(conn);
  }

  /** Absolute position where the extracted text starts inside the document,
      or -1 when it can't be determined. Positions used by the [vim.get_sel],
      [vim.set_sel] and [vim.replace] API are relative to this base. */
  int current_base()
  {
    ExtractedText et = current_extracted();
    return (et == null) ? -1 : et.startOffset;
  }
}