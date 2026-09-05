package juloo.keyboard2;

import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;

/** Incremental in-text search used in VIM mode.
    The '/' key starts a search: typed characters are appended to the query,
    backspace erases the last character and the matches are highlighted live
    as the query is edited. Enter leaves the search and [n] and [N] then
    navigate between the matches. The current match is selected to make it
    visible in the editor. */
final class SearchBar
{
  final VimEngine _vim;
  final StringBuilder _query = new StringBuilder();
  boolean _active = false;
  /** The last search has no match. The query can still be edited. */
  boolean _no_match = false;
  ArrayList<Integer> _matches;
  int _match_i = -1;
  int _base = 0;
  int _query_len = 0;
  /** Absolute position of the cursor when the search started. */
  int _origin = -1;

  SearchBar(VimEngine vim)
  {
    _vim = vim;
  }

  String query()
  {
    return _query.toString();
  }

  boolean active()
  {
    return _active;
  }

  boolean no_match()
  {
    return _active && _no_match;
  }

  void reset()
  {
    _active = false;
    _no_match = false;
    _query.setLength(0);
  }

  void begin()
  {
    reset();
    _active = true;
    _matches = null;
    _match_i = -1;
    _origin = -1;
  }

  void type(char c)
  {
    if (!_active)
      return;
    _query.append(c);
    find_now();
    _vim.update_status();
  }

  void backspace()
  {
    if (!_active)
      return;
    int n = _query.length();
    if (n > 0)
    {
      _query.setLength(n - 1);
      find_now();
      _vim.update_status();
    }
  }

  void cancel()
  {
    _matches = null;
    _match_i = -1;
    reset();
    _vim.set_mode(VimEngine.MODE_NORMAL);
  }

  void commit()
  {
    if (!_active)
      return;
    if (find_now() == 0)
    {
      // No match for this query, get back to normal mode.
      cancel();
      return;
    }
    reset();
    _vim.set_mode(VimEngine.MODE_NORMAL);
  }

  void next()
  {
    if (_matches != null && _matches.size() > 0)
    {
      _match_i = (_match_i + 1) % _matches.size();
      select_current();
    }
  }

  void prev()
  {
    if (_matches != null && _matches.size() > 0)
    {
      _match_i = (_match_i - 1 + _matches.size()) % _matches.size();
      select_current();
    }
  }

  /** Run the search for the current query and select the first match at or
      after the cursor. Returns the number of matches found (0 = none). */
  int find_now()
  {
    _matches = null;
    _match_i = -1;
    _no_match = false;
    String q = _query.toString();
    if (q.isEmpty())
      return 0;
    InputConnection conn = _vim.get_conn();
    if (conn == null)
      return 0;
    ExtractedText et = _vim._handler.get_full_text(conn);
    if (et == null || et.text == null || et.selectionStart < 0)
      // The editor can't provide its full text, abort the search.
      return 0;
    String text = et.text.toString();
    int base = et.startOffset;
    if (_origin < 0)
      _origin = base + et.selectionStart;
    String lower = text.toLowerCase();
    String lq = q.toLowerCase();
    ArrayList<Integer> matches = new ArrayList<Integer>();
    int from = 0;
    while (true)
    {
      int idx = lower.indexOf(lq, from);
      if (idx < 0)
        break;
      matches.add(idx);
      if (idx + 1 >= lower.length())
        break;
      from = idx + 1;
    }
    if (matches.isEmpty())
    {
      _no_match = true;
      return 0;
    }
    _matches = matches;
    _query_len = lq.length();
    _base = base;
    // Start from the first match located at or after the search origin.
    _match_i = 0;
    for (int i = 0; i < matches.size(); i++)
    {
      if (base + matches.get(i) >= _origin)
      {
        _match_i = i;
        break;
      }
    }
    select_current();
    return matches.size();
  }

  void select_current()
  {
    if (_matches == null || _match_i < 0 || _match_i >= _matches.size())
      return;
    InputConnection conn = _vim.get_conn();
    if (conn == null)
      return;
    int start = _base + _matches.get(_match_i);
    conn.setSelection(start, start + _query_len);
  }
}