package com.vinisskt.vikey;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExtraKeysTest
{
  static KeyValue key(String name) { return KeyValue.getKeyByName(name); }

  static ExtraKeys.ExtraKey extra(String str) { return ExtraKeys.ExtraKey.parse(str, "s"); }

  static Map<KeyValue, KeyboardData.PreferredPos> dst()
  {
    return new HashMap<KeyValue, KeyboardData.PreferredPos>();
  }

  static ExtraKeys.Query query(String script, KeyValue... present)
  {
    Set<KeyValue> ps = new HashSet<KeyValue>();
    for (KeyValue kv : present) ps.add(kv);
    return new ExtraKeys.Query(script, ps);
  }

  // ---- compute -----------------------------------------------------------

  @Test public void compute_adds_key_of_same_script_at_default_pos()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    extra("a").compute(dst, query("s"));
    assertEquals(1, dst.size());
    assertEquals(key("a"), dst.keySet().iterator().next());
    assertNull(dst.get(key("a")).next_to);
  }

  @Test public void compute_skips_key_of_other_script()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    extra("a").compute(dst, query("other"));
    assertEquals(0, dst.size());
  }

  @Test public void compute_adds_scriptless_key_to_any_script()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    ExtraKeys.ExtraKey k = ExtraKeys.ExtraKey.parse("a", null);
    k.compute(dst, query("other"));
    assertEquals(1, dst.size());
  }

  @Test public void compute_takes_alternative_when_only_one_and_key_absent()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    extra("a:b").compute(dst, query("s"));
    assertEquals(1, dst.size());
    assertEquals(key("b"), dst.keySet().iterator().next());
  }

  @Test public void compute_keeps_key_when_file_contain_the_alternative()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    // The alternative is present anyway so the alternative is not needed.
    extra("a:b").compute(dst, query("s", key("b")));
    assertEquals(0, dst.size());
  }

  @Test public void compute_adds_key_with_alternatives_when_not_all_present()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    extra("a:b:c").compute(dst, query("s", key("b")));
    assertEquals(1, dst.size());
    assertEquals(key("a"), dst.keySet().iterator().next());
  }

  @Test public void compute_sets_next_to_position()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    extra("a@shift").compute(dst, query("s"));
    assertEquals(1, dst.size());
    assertEquals(key("shift"), dst.get(key("a")).next_to);
  }

  @Test public void compute_noop_on_empty_extras()
  {
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    ExtraKeys.EMPTY.compute(dst, query("s"));
    assertEquals(0, dst.size());
  }

  // ---- parse -------------------------------------------------------------

  @Test public void parse_plain_key()
  {
    ExtraKeys.ExtraKey k = extra("a");
    assertEquals(key("a"), k.kv);
    assertEquals("s", k.script);
    assertEquals(Collections.emptyList(), k.alternatives);
    assertNull(k.next_to);
  }

  @Test public void parse_key_with_alternatives()
  {
    ExtraKeys.ExtraKey k = extra("a:b:c");
    assertEquals(key("a"), k.kv);
    assertEquals(Arrays.asList(key("b"), key("c")), k.alternatives);
    assertNull(k.next_to);
  }

  @Test public void parse_key_next_to()
  {
    ExtraKeys.ExtraKey k = extra("a@space");
    assertEquals(key("space"), k.next_to);
    assertEquals(Collections.emptyList(), k.alternatives);
  }

  @Test public void parse_key_with_alternatives_and_next_to()
  {
    ExtraKeys.ExtraKey k = extra("a:b@space");
    assertEquals(key("a"), k.kv);
    assertEquals(Arrays.asList(key("b")), k.alternatives);
    assertEquals(key("space"), k.next_to);
  }

  @Test public void parse_split_on_pipe()
  {
    ExtraKeys ks = ExtraKeys.parse("s", "a|b|c");
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    ks.compute(dst, query("s"));
    assertEquals(3, dst.size());
  }

  @Test public void parse_and_compute_script_is_used()
  {
    ExtraKeys ks = ExtraKeys.parse("other", "a");
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    ks.compute(dst, query("s"));
    assertEquals(0, dst.size());
  }

  // ---- merge -------------------------------------------------------------

  static ExtraKeys single(String str, String script)
  {
    return new ExtraKeys(Arrays.asList(ExtraKeys.ExtraKey.parse(str, script)));
  }

  @Test public void merge_distinct_keys()
  {
    ExtraKeys m = ExtraKeys.merge(Arrays.asList(single("a", "s"), single("b", "s")));
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    m.compute(dst, query("s"));
    assertEquals(2, dst.size());
  }

  @Test public void merge_same_script_kept()
  {
    ExtraKeys m = ExtraKeys.merge(Arrays.asList(single("a", "s"), single("a", "s")));
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    m.compute(dst, query("t"));
    assertEquals(0, dst.size()); // script still "s" after merge
    m.compute(dst, query("s"));
    assertEquals(1, dst.size());
  }

  @Test public void merge_conflicting_scripts_generalized()
  {
    ExtraKeys m = ExtraKeys.merge(Arrays.asList(single("a", "s"), single("a", "t")));
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    m.compute(dst, query("other"));
    assertEquals(1, dst.size());
  }

  @Test public void merge_one_null_keeps_the_other_script()
  {
    ExtraKeys m = ExtraKeys.merge(Arrays.asList(single("a", "s"), single("a", null)));
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    m.compute(dst, query("other"));
    assertEquals(0, dst.size()); // script still "s"
    m.compute(dst, query("s"));
    assertEquals(1, dst.size());
  }

  @Test public void merge_concatenates_alternatives()
  {
    ExtraKeys m = ExtraKeys.merge(Arrays.asList(single("a:b", "s"), single("a:c", "s")));
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    m.compute(dst, query("s", key("b"), key("c")));
    assertEquals(0, dst.size()); // all two alternatives present now
  }

  @Test public void merge_next_to_kept_when_equal()
  {
    ExtraKeys m = ExtraKeys.merge(Arrays.asList(single("a@space", "s"), single("a@space", "s")));
    Map<KeyValue, KeyboardData.PreferredPos> dst = dst();
    m.compute(dst, query("s"));
    assertEquals(key("space"), dst.get(key("a")).next_to);
  }
}