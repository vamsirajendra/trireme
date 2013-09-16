/**
 * Copyright (C) 2013 Apigee Corp. and other Noderunner contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.apigee.noderunner.core.internal;

import java.nio.charset.Charset;
import java.util.HashMap;

public class Charsets
{
    public static final String DEFAULT_ENCODING = "utf8";

    public static final Charset UTF8 = Charset.forName("UTF8");
    public static final Charset UCS2 = Charset.forName("UTF-16LE");
    public static final Charset UTF16BE = Charset.forName("UTF-16BE");
    public static final Charset UTF32LE = Charset.forName("UTF-32LE");
    public static final Charset UTF32BE = Charset.forName("UTF-32BE");
    public static final Charset BASE64 = Charset.forName("Node-Base64");
    public static final Charset ASCII = Charset.forName("US-ASCII");
    public static final Charset NODE_HEX = Charset.forName("Node-Hex");
    public static final Charset NODE_BINARY = Charset.forName("Node-Binary");
    public static final Charset DEFAULT = UTF8;

    private static final Charsets charsets = new Charsets();

    private final HashMap<String, Charset> encodings = new HashMap<String, Charset>();

    private Charsets()
    {
        encodings.put("undefined", DEFAULT);
        encodings.put("utf8", UTF8);
        encodings.put("UTF8", UTF8);
        encodings.put("utf-8", UTF8);
        encodings.put("UTF-8", UTF8);
        encodings.put("utf16le", UCS2);
        encodings.put("UTF16LE", UCS2);
        encodings.put("utf-16le", UCS2);
        encodings.put("UTF-16LE", UCS2);
        encodings.put("utf16be", UTF16BE);
        encodings.put("UTF16BE", UTF16BE);
        encodings.put("utf-16be", UTF16BE);
        encodings.put("UTF-16BE", UTF16BE);
        encodings.put("utf32be", UTF32BE);
        encodings.put("UTF32BE", UTF32BE);
        encodings.put("utf-32be", UTF32BE);
        encodings.put("UTF-32BE", UTF32BE);
        encodings.put("utf32le", UTF32LE);
        encodings.put("UTF32LE", UTF32LE);
        encodings.put("utf-32le", UTF32LE);
        encodings.put("UTF-32LE", UTF32LE);
        encodings.put("ucs2", UCS2);
        encodings.put("UCS2", UCS2);
        encodings.put("ucs-2", UCS2);
        encodings.put("UCS-2", UCS2);
        encodings.put("ascii", ASCII);
        encodings.put("ASCII", ASCII);
        // These are implemented in NodeCharsetEncoder
        encodings.put("binary", NODE_BINARY);
        encodings.put("raw", NODE_BINARY);
        encodings.put("hex", NODE_HEX);
        encodings.put("base64", BASE64);
    }

    public static final Charsets get()
    {
        return charsets;
    }

    public Charset resolveCharset(String name)
    {
        if ((name == null) || name.isEmpty()) {
            return DEFAULT;
        }
        return encodings.get(name);
    }

    public Charset getCharset(String name)
    {
        return encodings.get(name);
    }
}
