package com.vinisskt.vikey;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyValueAllNamesTest
{
  /** Every special key name handled by [KeyValue.getSpecialKeyByName]. */
  static final String[] NAMES = {
    "\\?", "\\#", "\\@", "\\\\", "shift", "ctrl",
    "alt", "accent_aigu", "accent_caron", "accent_cedille", "accent_circonflexe", "accent_grave",
    "accent_macron", "accent_ring", "accent_tilde", "accent_trema", "accent_ogonek", "accent_dot_above",
    "accent_double_aigu", "accent_slash", "accent_arrow_right", "accent_breve", "accent_bar", "accent_dot_below",
    "accent_horn", "accent_hook_above", "accent_double_grave", "accent_small_caps", "superscript", "subscript",
    "ordinal", "arrows", "box", "fn", "meta", "combining_dot_above",
    "combining_double_aigu", "combining_slash", "combining_arrow_right", "combining_breve", "combining_bar", "combining_aigu",
    "combining_caron", "combining_cedille", "combining_circonflexe", "combining_grave", "combining_macron", "combining_ring",
    "combining_tilde", "combining_trema", "combining_ogonek", "combining_dot_below", "combining_horn", "combining_hook_above",
    "combining_vertical_tilde", "combining_inverted_breve", "combining_pokrytie", "combining_slavonic_psili", "combining_slavonic_dasia", "combining_payerok",
    "combining_titlo", "combining_vzmet", "combining_arabic_v", "combining_arabic_inverted_v", "combining_shaddah", "combining_sukun",
    "combining_fatha", "combining_dammah", "combining_kasra", "combining_hamza_above", "combining_hamza_below", "combining_alef_above",
    "combining_fathatan", "combining_kasratan", "combining_dammatan", "combining_alef_below", "combining_kavyka", "combining_palatalization",
    "config", "switch_text", "switch_numeric", "switch_emoji", "switch_back_emoji", "switch_clipboard",
    "switch_back_clipboard", "switch_forward", "switch_backward", "switch_greekmath", "change_method", "change_method_prev",
    "change_method_next", "action", "capslock", "voice_typing", "voice_typing_chooser", "complete_first",
    "complete_second", "complete_third", "complete_emoji", "hide_self", "change_dictionary", "esc",
    "enter", "up", "right", "down", "left", "page_up",
    "page_down", "home", "end", "delete", "insert", "f1",
    "f2", "f3", "f4", "f5", "f6", "f7",
    "f8", "f9", "f10", "f11", "f12", "tab",
    "menu", "scroll_lock", "\\t", "\\n", "space", "nbsp",
    "nnbsp", "lrm", "rlm", "b(", "b)", "b[",
    "b]", "b{", "b}", "blt", "bgt", "qamats",
    "patah", "sheva", "dagesh", "hiriq", "segol", "tsere",
    "holam", "qubuts", "hataf_patah", "hataf_qamats", "hataf_segol", "shindot",
    "shindot_placeholder", "sindot", "sindot_placeholder", "geresh", "gershayim", "maqaf",
    "rafe", "ole", "ole_placeholder", "meteg", "meteg_placeholder", "zwj",
    "zwnj", "halfspace", "backspace", "copy", "paste", "cut",
    "selectAll", "shareText", "pasteAsPlainText", "undo", "redo", "delete_word",
    "forward_delete_word", "cursor_left", "cursor_right", "cursor_up", "cursor_down", "selection_cancel",
    "selection_cursor_left", "selection_cursor_right", "replaceText", "textAssist", "autofill", "compose",
    "compose_cancel", "removed", "f11_placeholder", "f12_placeholder", "\u3131", "\u3132",
    "\u3134", "\u3137", "\u3138", "\u3139", "\u3141", "\u3142",
    "\u3143", "\u3145", "\u3146", "\u3147", "\u3148", "\u3149",
    "\u314a", "\u314b", "\u314c", "\u314d", "\u314e", "\u0b94",
    "\u0ba8", "\u0bb2", "\u0bb4", "\u0bef", "\u0b95", "\u0bb7",
    "\u0bc7", "\u0be8", "\u0b9c", "\u0b99", "\u0ba9", "\u0be6",
    "\u0bc8", "\u0bc2", "\u0bae", "\u0b86", "\u0bed", "\u0bea",
    "\u0bbe", "\u0bb6", "\u0bec", "\u0bb5", "\u0bb8", "\u0bee",
    "\u0b9f", "\u0baa", "\u0b88", "\u0be9", "\u0b92", "\u0bcc",
    "\u0b89", "\u0beb", "\u0baf", "\u0bb0", "\u0bc1", "\u0b87",
    "\u0bcb", "\u0b93", "\u0b83", "\u0bb1", "\u0ba4", "\u0be7",
    "\u0ba3", "\u0b8f", "\u0b8a", "\u0bca", "\u0b9e", "\u0b85",
    "\u0b8e", "\u0b9a", "\u0bc6", "\u0b90", "\u0bbf", "\u0bf9",
    "\u0bb3", "\u0bb9", "\u0bf0", "\u0bd0", "\u0bf1", "\u0bf2",
    "\u0bf3", "\u0d85", "\u0d86", "\u0d87", "\u0d88", "\u0d89",
    "\u0d8a", "\u0d8b", "\u0d8c", "\u0d8d", "\u0d8e", "\u0d8f",
    "\u0d90", "\u0d91", "\u0d92", "\u0d93", "\u0d94", "\u0d95",
    "\u0d96", "\u0d9a", "\u0d9b", "\u0d9c", "\u0d9d", "\u0d9e",
    "\u0d9f", "\u0da0", "\u0da1", "\u0da2", "\u0da3", "\u0da4",
    "\u0da5", "\u0da6", "\u0da7", "\u0da8", "\u0da9", "\u0daa",
    "\u0dab", "\u0dac", "\u0dad", "\u0dae", "\u0daf", "\u0db0",
    "\u0db1", "\u0db3", "\u0db4", "\u0db5", "\u0db6", "\u0db7",
    "\u0db8", "\u0db9", "\u0dba", "\u0dbb", "\u0dbd", "\u0dc0",
    "\u0dc1", "\u0dc2", "\u0dc3", "\u0dc4", "\u0dc5", "\u0dc6",
    "\u0de6", "\u0de7", "\u0de8", "\u0de9", "\u0dea", "\u0deb",
    "\u0dec", "\u0ded", "\u0dee", "\u0def", "\u0df2", "\u0df3",
    "\\u0d81", "\\u0d82", "\\u0d83", "\\u0dca", "\\u0dcf", "\\u0dd0",
    "\\u0dd1", "\\u0dd2", "\\u0dd3", "\\u0dd4", "\\u0dd6", "\\u0dd8",
    "\\u0dd9", "\\u0dda", "\\u0ddb", "\\u0ddc", "\\u0ddd", "\\u0dde",
    "\\u0ddf", "\ud804\udde1", "\ud804\udde2", "\ud804\udde3", "\ud804\udde4", "\ud804\udde5",
    "\ud804\udde6", "\ud804\udde7", "\ud804\udde8", "\ud804\udde9", "\ud804\uddea", "\ud804\uddeb",
    "\ud804\uddec", "\ud804\udded", "\ud804\uddee", "\ud804\uddef", "\ud804\uddf0", "\ud804\uddf1",
    "\ud804\uddf2", "\ud804\uddf3", "\ud804\uddf4", "\u0df4", "\u20a8", "selection_mode",
  };

  @Test public void all_special_keys_resolve()
  {
    for (String name : NAMES)
    {
      KeyValue k = KeyValue.getSpecialKeyByName(name);
      if (k != null)
        assertNotNull(k.toString());
    }
  }

  @Test public void unknown_special_name_is_null()
  {
    assertNull(KeyValue.getSpecialKeyByName("definitely_not_a_key_name"));
  }

  @Test public void getKeyByName_falls_back_to_parsing()
  {
    // "hello" has no special meaning: parsed as a string key
    assertEquals("hello", KeyValue.getKeyByName("hello").getString());
  }
}
