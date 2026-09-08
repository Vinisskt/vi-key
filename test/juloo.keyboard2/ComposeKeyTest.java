package juloo.keyboard2;

import juloo.keyboard2.ComposeKey;
import juloo.keyboard2.ComposeKeyData;
import juloo.keyboard2.KeyValue;
import org.junit.Test;
import static juloo.keyboard2.TestUtils.*;
import static org.junit.Assert.*;

public class ComposeKeyTest
{
  public ComposeKeyTest() {}

  @Test
  public void composeEquals() throws Exception
  {
    // From Compose.pre
    assertEquals(apply("'e"), str("é"));
    assertEquals(apply("e'"), str("é"));
    // From extra.json
    assertEquals(apply("Vc"), str("Č"));
    assertEquals(apply("\\n"), key("\\n"));
    // From arabic.json
    assertEquals(apply("اا"), key("combining_alef_above"));
    assertEquals(apply("ل۷"), str("ڵ"));
    assertEquals(apply("۷ل"), str("ڵ"));
    // From cyrillic.json
    assertEquals(apply(",г"), str("ӻ"));
    assertEquals(apply("г,"), str("ӻ"));
    assertEquals(apply("ач"), key("combining_aigu"));
  }

  @Test
  public void fnEquals() throws Exception
  {
    int state = ComposeKeyData.fn;
    assertEquals(apply("<", state), str("«"));
    assertEquals(apply("{", state), str("‹"));
    // Named key
    assertEquals(apply("1", state), key("f1"));
    assertEquals(apply(" ", state), key("nbsp"));
    // Named 1-char key
    assertEquals(apply("ய", state), str("௰", KeyValue.FLAG_SMALLER_FONT));
  }

  @Test
  public void stringKeys() throws Exception
  {
    int state = ComposeKeyData.shift;
    assertEquals(apply("𝕨", state), str("𝕎"));
    assertEquals(apply("𝕩", state), str("𝕏"));
    state = ComposeKeyData.accent_small_caps;
    assertEquals(apply("œ", state), str("ɶ"));
    assertEquals(apply("Œ", state), str("ɶ"));
    assertEquals(apply("ɹ", state), str("ʁ"));
    assertEquals(apply("ɠ", state), str("ʛ"));
  }

  @Test
  public void spaceKey() throws Exception
  {
    int state = ComposeKeyData.compose;
    assertEquals(apply("- ", state), str("~"));
    assertEquals(apply(" -", state), str("~"));
    assertEquals(apply("  ", state), key("nbsp"));
    assertEquals(apply(apply(" "), key("space")), key("nbsp"));
  }

  @Test
  public void applyKeyValue() throws Exception
  {
    int fn = ComposeKeyData.fn;
    // Char keys are applied through their char value.
    assertEquals(key("f1"), ComposeKey.apply(fn, str("1")));
    // The space bar editing key behaves like a space.
    assertEquals(key("nbsp"), ComposeKey.apply(fn, key("space")));
    // Multi-char string keys are applied character by character.
    assertNull(ComposeKey.apply(fn, str("zzz")));
    // Editing keys other than the space bar are ignored.
    assertNull(ComposeKey.apply(fn, key("backspace")));
    // Other kinds are ignored.
    assertNull(ComposeKey.apply(fn, key("capslock")));
    assertNull(ComposeKey.apply(fn, key("shift")));
  }

  @Test
  public void transformChar() throws Exception
  {
    int fn = ComposeKeyData.fn;
    int shift = ComposeKeyData.shift;
    // Final char state: single-char substitutions from fn.
    assertEquals('\u00E6', transform_char(fn, 'a'));
    assertEquals('\u2039', transform_char(fn, '{'));
    // The space in fn resolves to the string key 'nbsp', not a char.
    assertEquals(0, transform_char(fn, ' '));
    // Intermediate state: not a final state yet.
    assertEquals(0, transform_char(ComposeKeyData.compose, 'q'));
    // String final state and no-match are not single-char substitutions.
    assertEquals(0, transform_char(fn, '1'));
    assertEquals(0, transform_char(fn, '\u00A0'));
  }

  @Test
  public void applyStringEdgeCases() throws Exception
  {
    int fn = ComposeKeyData.fn;
    // An empty sequence never matches.
    assertNull(ComposeKey.apply(fn, ""));
    // A final state reached before the end of the string stops the sequence.
    assertNull(apply("1,", fn));
    assertNull(apply(" ,", fn));
  }

  char transform_char(int state, char c)
  {
    return ComposeKey.transform_char(state, c);
  }

  KeyValue apply(String seq)
  {
    return ComposeKey.apply(ComposeKeyData.compose, seq);
  }

  KeyValue apply(String seq, int state)
  {
    return ComposeKey.apply(state, seq);
  }

  KeyValue apply(KeyValue prev, KeyValue next)
  {
    if (prev.getKind() != KeyValue.Kind.Compose_pending)
      return null;
    return ComposeKey.apply(prev.getPendingCompose(), next);
  }
}
