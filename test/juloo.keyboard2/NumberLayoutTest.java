package com.vinisskt.vikey;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumberLayoutTest
{
  @Test public void of_string_number() { assertEquals(NumberLayout.NUMBER, NumberLayout.of_string("number")); }
  @Test public void of_string_normal() { assertEquals(NumberLayout.NORMAL, NumberLayout.of_string("normal")); }
  @Test public void of_string_pin() { assertEquals(NumberLayout.PIN, NumberLayout.of_string("pin")); }
  @Test public void of_string_pin_is_default() { assertEquals(NumberLayout.PIN, NumberLayout.of_string("anything")); }
  @Test public void of_string_empty_is_pin() { assertEquals(NumberLayout.PIN, NumberLayout.of_string("")); }
}