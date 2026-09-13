package com.vinisskt.vikey;

import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** A minimal [XmlResourceParser] used to drive the XML parsing in unit tests.
    The mockable android.jar returns [null] from [android.util.Xml.newPullParser]
    and provides no StAX implementation, so this parser reads the document
    directly from a string. Only the subset of events used by [KeyboardData]
    is produced: [START_DOCUMENT], [START_TAG], [END_TAG] and
    [END_DOCUMENT]; text, comments and processing instructions are skipped. */
public final class FakeXmlPullParser implements XmlResourceParser
{
  String _src = "";
  int _pos = 0;
  int _eventType = XmlPullParser.START_DOCUMENT;
  String _name = null;
  LinkedHashMap<String, String> _attrs = null;
  List<String> _attrOrder = new ArrayList<String>();
  boolean _selfClose = false;
  boolean _closed = false;

  public FakeXmlPullParser() {}

  private boolean atEnd() { return _pos >= _src.length(); }

  private char cur()
  {
    return _src.charAt(_pos);
  }

  private boolean startsWith(String s)
  {
    return _src.startsWith(s, _pos);
  }

  private String readName()
  {
    int start = _pos;
    while (!atEnd())
    {
      char c = cur();
      if (Character.isWhitespace(c) || c == '>' || c == '/' || c == '=')
        break;
      _pos++;
    }
    return _src.substring(start, _pos);
  }

  private void skipWhitespace()
  {
    while (!atEnd() && Character.isWhitespace(cur()))
      _pos++;
  }

  private void skipText()
  {
    while (!atEnd() && cur() != '<')
      _pos++;
  }

  /** Skip whitespace, comments and processing instructions. */
  private void skipMisc() throws XmlPullParserException
  {
    while (true)
    {
      skipWhitespace();
      if (atEnd())
        return;
      if (startsWith("<!--"))
      {
        _pos += 4;
        while (!atEnd() && !startsWith("-->"))
          _pos++;
        _pos += 3;
        continue;
      }
      if (startsWith("<?"))
      {
        _pos += 2;
        while (!atEnd() && !startsWith("?>"))
          _pos++;
        _pos += 2;
        continue;
      }
      if (startsWith("<!DOCTYPE") || startsWith("<!"))
      {
        while (!atEnd() && cur() != '>')
          _pos++;
        _pos++;
        continue;
      }
      return;
    }
  }

  private void readAttributeValue(StringBuffer v) throws XmlPullParserException
  {
    skipWhitespace();
    if (atEnd() || (cur() != '"' && cur() != '\''))
      throw new XmlPullParserException("Expected attribute value");
    char q = cur();
    _pos++;
    while (!atEnd() && cur() != q)
    {
      if (cur() == '&')
      {
        int entityStart = _pos;
        _pos++;
        StringBuffer ent = new StringBuffer();
        while (!atEnd() && cur() != ';' && _pos - entityStart < 32)
        {
          ent.append(cur());
          _pos++;
        }
        if (!atEnd() && cur() == ';')
          _pos++;
        String body = ent.toString();
        if (body.equals("amp")) v.append('&');
        else if (body.equals("lt")) v.append('<');
        else if (body.equals("gt")) v.append('>');
        else if (body.equals("apos")) v.append('\'');
        else if (body.equals("quot")) v.append('"');
        else if (body.startsWith("#x") || body.startsWith("#X"))
        {
          try { v.append((char)Integer.parseInt(body.substring(2), 16)); }
          catch (NumberFormatException _e) {}
        }
        else if (body.startsWith("#"))
        {
          try { v.append((char)Integer.parseInt(body.substring(1))); }
          catch (NumberFormatException _e) {}
        }
        else if (body.length() > 0)
        {
          v.append(body);
        }
      }
      else
      {
        v.append(cur());
        _pos++;
      }
    }
    if (!atEnd())
      _pos++;
  }

  /** Parse a start tag, leaving [attrs] filled. */
  private int parseElement() throws XmlPullParserException
  {
    _pos++; // consume '<'
    _name = readName();
    _attrs = new LinkedHashMap<String, String>();
    _attrOrder = new ArrayList<String>();
    while (true)
    {
      skipWhitespace();
      if (atEnd())
        throw new XmlPullParserException("Unclosed tag <" + _name + ">");
      if (cur() == '/')
      {
        _pos++;
        if (!atEnd() && cur() == '>')
          _pos++;
        _selfClose = true;
        _eventType = XmlPullParser.START_TAG;
        return _eventType;
      }
      if (cur() == '>')
      {
        _pos++;
        _selfClose = false;
        _eventType = XmlPullParser.START_TAG;
        return _eventType;
      }
      String attrName = readName();
      skipWhitespace();
      StringBuffer value = new StringBuffer();
      if (!atEnd() && cur() == '=')
      {
        _pos++;
        readAttributeValue(value);
      }
      _attrs.put(attrName, value.toString());
      _attrOrder.add(attrName);
    }
  }

  @Override
  public int next() throws XmlPullParserException
  {
    if (_closed)
    {
      _eventType = XmlPullParser.END_DOCUMENT;
      return _eventType;
    }
    if (_eventType == XmlPullParser.END_DOCUMENT)
      return XmlPullParser.END_DOCUMENT;
    if (_selfClose)
    {
      _selfClose = false;
      _eventType = XmlPullParser.END_TAG;
      return _eventType;
    }
    skipMisc();
    if (atEnd())
    {
      _eventType = XmlPullParser.END_DOCUMENT;
      return _eventType;
    }
    if (cur() != '<')
    {
      skipText();
      return next();
    }
    if (startsWith("</"))
    {
      _pos += 2;
      _name = readName();
      skipWhitespace();
      if (!atEnd() && cur() == '>')
        _pos++;
      _eventType = XmlPullParser.END_TAG;
      return _eventType;
    }
    return parseElement();
  }

  @Override
  public String getPositionDescription()
  {
    int line = 1;
    int col = 1;
    for (int i = 0; i < _pos && i < _src.length(); i++)
    {
      if (_src.charAt(i) == '\n')
      {
        line++;
        col = 1;
      }
      else
      {
        col++;
      }
    }
    return "(file.xml, line " + line + ", col " + col + ")";
  }

  @Override
  public void close()
  {
    _closed = true;
  }

  @Override
  public String getAttributeValue(String namespace, String name)
  {
    if (_attrs == null)
      return null;
    if (namespace != null && !namespace.isEmpty())
      return null;
    return _attrs.get(name);
  }

  @Override
  public String getAttributeValue(int index)
  {
    if (_attrs == null)
      return null;
    String n = getAttributeName(index);
    return (n == null) ? null : _attrs.get(n);
  }

  @Override
  public int getAttributeCount()
  {
    return (_attrs == null) ? 0 : _attrs.size();
  }

  @Override
  public String getAttributeName(int index)
  {
    if (_attrs == null || index < 0 || index >= _attrOrder.size())
      return null;
    return _attrOrder.get(index);
  }

  @Override
  public String getName()
  {
    return _name;
  }

  @Override
  public int getEventType() throws XmlPullParserException
  {
    return _eventType;
  }

  @Override
  public void setInput(Reader in) throws XmlPullParserException
  {
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[4096];
    int n;
    try
    {
      while ((n = in.read(buf)) > 0)
        sb.append(buf, 0, n);
    }
    catch (IOException e)
    {
      throw new XmlPullParserException("Failed to read input", this, e);
    }
    _src = sb.toString();
    _pos = 0;
    _eventType = XmlPullParser.START_DOCUMENT;
    _selfClose = false;
  }

  @Override
  public void setInput(InputStream in, String encoding) throws XmlPullParserException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    try
    {
      while ((n = in.read(buf)) > 0)
        out.write(buf, 0, n);
    }
    catch (IOException e)
    {
      throw new XmlPullParserException("Failed to read input", this, e);
    }
    try
    {
      setInput(new InputStreamReader(
          new ByteArrayInputStream(out.toByteArray()), "UTF-8"));
    }
    catch (Exception e)
    {
      throw new XmlPullParserException("Unsupported encoding", this, e);
    }
  }

  /* Unused XmlPullParser members */

  @Override public void setFeature(String name, boolean state) {}
  @Override public boolean getFeature(String name) { return false; }
  @Override public void setProperty(String name, Object value) {}
  @Override public Object getProperty(String name) { return null; }
  @Override public String getInputEncoding() { return null; }
  @Override public void defineEntityReplacementText(String entityName, String replacementText) {}
  @Override public int getNamespaceCount(int depth) { return 0; }
  @Override public String getNamespacePrefix(int pos) { return null; }
  @Override public String getNamespaceUri(int pos) { return null; }
  @Override public String getNamespace(String prefix) { return null; }
  @Override public int getDepth() { return 0; }
  @Override public int getLineNumber() { return 0; }
  @Override public int getColumnNumber() { return 0; }
  @Override public boolean isWhitespace() { return false; }
  @Override public String getText() { return null; }
  @Override public char[] getTextCharacters(int[] holderForStartAndLength) { return null; }
  @Override public String getNamespace() { return null; }
  @Override public String getPrefix() { return null; }
  @Override public boolean isEmptyElementTag() { return false; }
  @Override public String getAttributeNamespace(int index) { return ""; }
  @Override public String getAttributePrefix(int index) { return null; }
  @Override public String getAttributeType(int index) { return "CDATA"; }
  @Override public boolean isAttributeDefault(int index) { return false; }
  @Override public int nextToken() throws XmlPullParserException { return next(); }
  @Override public void require(int type, String namespace, String name)
      throws XmlPullParserException
  {
    if (type != getEventType() || (name != null && !name.equals(getName())))
      throw new XmlPullParserException("Required element not found", this, null);
  }
  @Override public String nextText() throws XmlPullParserException
  {
    if (getEventType() != XmlPullParser.START_TAG)
      throw new XmlPullParserException("nextText must start from a text event");
    int pos = _pos;
    StringBuilder sb = new StringBuilder();
    while (!atEnd() && cur() != '<')
    {
      sb.append(cur());
      _pos++;
    }
    if (_eventType == XmlPullParser.START_TAG && _selfClose)
      return sb.toString();
    return sb.toString();
  }
  @Override public int nextTag() throws XmlPullParserException
  {
    int t;
    do
    {
      t = next();
    }
    while (t != XmlPullParser.START_TAG && t != XmlPullParser.END_TAG
        && t != XmlPullParser.END_DOCUMENT);
    return t;
  }

  /* Unused AttributeSet members */

  @Override public boolean getAttributeBooleanValue(int index, boolean defaultValue)
  { return defaultValue; }
  @Override public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue)
  { return defaultValue; }
  @Override public float getAttributeFloatValue(int index, float defaultValue)
  { return defaultValue; }
  @Override public float getAttributeFloatValue(String namespace, String attribute, float defaultValue)
  { return defaultValue; }
  @Override public int getAttributeIntValue(int index, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeListValue(int index, String[] options, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeListValue(String namespace, String attribute,
      String[] options, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeNameResource(int index) { return 0; }
  @Override public int getAttributeResourceValue(int index, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeResourceValue(String namespace, String attribute, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeUnsignedIntValue(int index, int defaultValue)
  { return defaultValue; }
  @Override public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue)
  { return defaultValue; }
  @Override public String getClassAttribute() { return null; }
  @Override public String getIdAttribute() { return null; }
  @Override public int getIdAttributeResourceValue(int defaultValue) { return defaultValue; }
  @Override public int getStyleAttribute() { return 0; }
}