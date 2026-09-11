package com.vinisskt.vikey;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.List;

/** A test double for [KeyEventHandler.IReceiver]. */
public final class FakeReceiver implements KeyEventHandler.IReceiver
{
  public final FakeInputConnection conn = new FakeInputConnection();
  public final List<KeyValue.Event> events = new ArrayList<KeyValue.Event>();
  public final List<String> shiftStates = new ArrayList<String>();
  public final List<Boolean> composePending = new ArrayList<Boolean>();
  public final List<Boolean> selectionStates = new ArrayList<Boolean>();
  public final List<String> vimStatuses = new ArrayList<String>();
  public boolean floatOpen = false;
  public String floatUrl = null;
  public int helpOpened = 0;
  public int themeChanges = 0;
  public final List<String> pages = new ArrayList<String>();

  @Override public void handle_event_key(KeyValue.Event ev) { events.add(ev); }
  @Override public void set_shift_state(boolean state, boolean lock)
  { shiftStates.add((state ? "on:" : "off:") + lock); }
  @Override public void set_compose_pending(boolean pending) { composePending.add(pending); }
  @Override public void selection_state_changed(boolean ongoing) { selectionStates.add(ongoing); }
  @Override public InputConnection getCurrentInputConnection() { return conn; }
  @Override public Handler getHandler() { return new Handler(Looper.getMainLooper()); }
  @Override public Context getApplicationContext() { return null; }

  @Override
  public void set_vim_status(String text, int color)
  { vimStatuses.add(text + "\u0000" + color); }

  @Override public boolean is_float_open() { return floatOpen; }
  @Override public void toggle_float_panel(String url)
  { floatOpen = !floatOpen; floatUrl = url; }
  @Override public void close_float_panel() { floatOpen = false; }
  @Override public void open_help() { helpOpened++; }
  @Override public void open_page(String title, String html)
  { pages.add(title + "\u0000" + html); }
  @Override public void theme_changed() { themeChanges++; }

  public String lastStatus() { return vimStatuses.isEmpty() ? null : vimStatuses.get(vimStatuses.size() - 1); }
  public boolean lastStatusIn(String s) { return s.contains(s); }

  public static boolean is_normal(String lastStatus)
  { return lastStatus != null && lastStatus.startsWith("NORMAL"); }

  public static boolean is_insert(String lastStatus)
  { return lastStatus != null && lastStatus.startsWith("INSERT"); }
}