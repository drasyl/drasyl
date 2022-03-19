/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.cli.node.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Represents a JSON-RPC 2.0 request object.
 */
public class JsonRpc2Request {
    public static final String VERSION = "2.0";
    private final String jsonrpc;
    private final String method;
    private final Object params;
    private final Object id;

    @JsonCreator
    public JsonRpc2Request(@JsonProperty("jsonrpc") final String jsonrpc,
                           @JsonProperty("method") final String method,
                           @JsonProperty("params") final Object params,
                           @JsonProperty("id") final Object id) {
        if (!VERSION.equals(jsonrpc)) {
            throw new IllegalArgumentException("jsonrpc value must be '2.0'.");
        }
        this.jsonrpc = jsonrpc;
        this.method = requireNonNull(method);
        if (params != null && !(params instanceof List) && !(params instanceof Map)) {
            throw new IllegalArgumentException("params must contain an array or object.");
        }
        this.params = params;
        if (id != null && !(id instanceof String) && !(id instanceof Number)) {
            throw new IllegalArgumentException("id must be a string or number.");
        }
        this.id = id;
    }

    public JsonRpc2Request(final String method, final Object params, final String id) {
        this(VERSION, method, params, id);
    }

    public JsonRpc2Request(final String method, final Object params, final Number id) {
        this(VERSION, method, params, id);
    }

    public JsonRpc2Request(final String method, final Object params) {
        this(VERSION, method, params, UUID.randomUUID().toString());
    }

    public JsonRpc2Request(final String method) {
        this(method, null);
    }

    @Override
    public String toString() {
        return "JsonRpc2Request{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", method='" + method + '\'' +
                ", params=" + params +
                ", id=" + id +
                '}';
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public Object getParams() {
        return params;
    }

    public Object getId() {
        return id;
    }

    public <T> T getParam(final int position) {
        if (!(params instanceof List)) {
            return null;
        }
        try {
            return (T) ((List) params).get(position);
        }
        catch (final ClassCastException e) {
            return null;
        }
    }

    public <T> T getParam(final String name) {
        if (!(params instanceof Map)) {
            return null;
        }
        try {
            return (T) ((Map) params).get(name);
        }
        catch (final ClassCastException e) {
            return null;
        }
    }
}
