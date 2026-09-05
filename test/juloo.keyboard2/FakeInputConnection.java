package juloo.keyboard2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.List;

/** A test double for [InputConnection]: keeps a text buffer, a selection,
    records key events, context-menu actions and editing operations. */
public final class FakeInputConnection implements InputConnection
{
  StringBuilder _text = new StringBuilder();
  int _sel_s = 0;
  int _sel_e = 0;
  /** Key events sent through [sendKeyEvent], as [code]"+"[action] strings. */
  public final List<String> keyEvents = new ArrayList<String>();
  /** Context menu action ids (e.g. [android.R.id.undo]). */
  public final List<Integer> contextActions = new ArrayList<Integer>();
  /** [deleteSurroundingText] calls, as before+":"+after. */
  public final List<String> deletions = new ArrayList<String>();
  /** [commitText] calls. */
  public final List<String> commits = new ArrayList<String>();
  int _batch = 0;

  public FakeInputConnection() {}

  public void set_text(String text, int sel_s, int sel_e)
  {
    _text.setLength(0);
    _text.append(text);
    _sel_s = Math.max(0, Math.min(text.length(), sel_s));
    _sel_e = Math.max(0, Math.min(text.length(), sel_e));
    if (_sel_e < _sel_s)
    {
      int t = _sel_e;
      _sel_e = _sel_s;
      _sel_s = t;
    }
  }

  public String text() { return _text.toString(); }
  public int selStart() { return _sel_s; }
  public int selEnd() { return _sel_e; }

  @Override public boolean beginBatchEdit() { _batch++; return true; }
  @Override public boolean endBatchEdit() { _batch--; return true; }
  @Override public boolean clearMetaKeyStates(int s) { return true; }
  @Override public void closeConnection() {}
  @Override public boolean commitCompletion(CompletionInfo c) { return true; }
  @Override public boolean commitCorrection(CorrectionInfo c) { return true; }

  @Override
  public boolean commitText(CharSequence text, int newCursorPosition)
  {
    commits.add(text.toString());
    int from = Math.min(_sel_s, _sel_e);
    int to = Math.max(_sel_s, _sel_e);
    _text.replace(from, to, text.toString());
    _sel_s = _sel_e = from + text.length();
    return true;
  }

  @Override
  public boolean deleteSurroundingText(int beforeLength, int afterLength)
  {
    deletions.add(beforeLength + ":" + afterLength);
    int s = Math.max(0, _sel_s - beforeLength);
    int e = Math.min(_text.length(), _sel_s + afterLength);
    _text.delete(s, e);
    int newSel = Math.max(0, _sel_s - beforeLength);
    _sel_s = _sel_e = newSel;
    return true;
  }

  @Override
  public boolean deleteSurroundingTextInCodePoints(int before, int after)
  {
    return deleteSurroundingText(before, after);
  }

  @Override public boolean finishComposingText() { return true; }

  @Override
  public int getCursorCapsMode(int reqModes) { return 0; }

  @Override
  public ExtractedText getExtractedText(ExtractedTextRequest request, int flags)
  {
    ExtractedText et = new ExtractedText();
    et.text = _text.toString();
    et.startOffset = 0;
    et.selectionStart = _sel_s;
    et.selectionEnd = _sel_e;
    et.flags = 0;
    return et;
  }

  @Override
  public CharSequence getSelectedText(int flags)
  {
    int s = Math.min(_sel_s, _sel_e);
    int e = Math.max(_sel_s, _sel_e);
    if (s == e)
      return null;
    return _text.substring(s, e);
  }

  @Override public CharSequence getTextAfterCursor(int n, int flags)
  {
    int from = _sel_e;
    int to = Math.min(_text.length(), from + n);
    if (to <= from) return null;
    return _text.substring(from, to);
  }

  @Override public CharSequence getTextBeforeCursor(int n, int flags)
  {
    int from = Math.max(0, _sel_s - n);
    int to = _sel_s;
    if (to <= from) return null;
    return _text.substring(from, to);
  }

  @Override public Handler getHandler() { return new Handler(Looper.getMainLooper()); }

  @Override
  public boolean performContextMenuAction(int id)
  {
    contextActions.add(id);
    return true;
  }

  @Override public boolean performEditorAction(int actionCode) { return true; }
  @Override public boolean performPrivateCommand(String action, Bundle data) { return true; }
  @Override public boolean requestCursorUpdates(int cursorUpdateMode) { return true; }
  @Override public boolean reportFullscreenMode(boolean fullScreen) { return true; }
  @Override public boolean commitContent(android.view.inputmethod.InputContentInfo info, int flags, Bundle opts) { return true; }

  @Override
  public boolean sendKeyEvent(KeyEvent event)
  {
    int code = event.getKeyCode();
    int action = event.getAction() == KeyEvent.ACTION_DOWN ? 1 : 0;
    keyEvents.add(code + "+" + action);
    return true;
  }

  @Override public boolean setComposingRegion(int start, int end) { return true; }
  @Override public boolean setComposingText(CharSequence text, int newCursorPosition) { return true; }

  @Override
  public boolean setSelection(int start, int end)
  {
    _sel_s = Math.max(0, Math.min(_text.length(), start));
    _sel_e = Math.max(0, Math.min(_text.length(), end));
    return true;
  }
}