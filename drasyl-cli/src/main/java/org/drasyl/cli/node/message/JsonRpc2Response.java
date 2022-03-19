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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.drasyl.cli.node.message.JsonRpc2Request.VERSION;

/**
 * Represents a JSON-RPC 2.0 response object.
 */
public class JsonRpc2Response {
    private final String jsonrpc;
    @JsonInclude(NON_NULL)
    private final Object result;
    @JsonInclude(NON_NULL)
    private final JsonRpc2Error error;
    private final Object id;

    @JsonCreator
    public JsonRpc2Response(@JsonProperty("jsonrpc") final String jsonrpc,
                            @JsonProperty("result") final Object result,
                            @JsonProperty("error") final JsonRpc2Error error,
                            @JsonProperty("id") final Object id) {
        if (!VERSION.equals(jsonrpc)) {
            throw new IllegalArgumentException("jsonrpc value must be '2.0'.");
        }
        this.jsonrpc = jsonrpc;
        if (result == null && error == null) {
            throw new IllegalArgumentException("result and error cannot be both null at the same time.");
        }
        else if (result != null && error != null) {
            throw new IllegalArgumentException("result and error cannot be both set at the same time.");
        }
        this.result = result;
        this.error = error;
        if (!(id instanceof String) && !(id instanceof Number)) {
            throw new IllegalArgumentException("id must be a string or number.");
        }
        this.id = id;
    }

    public JsonRpc2Response(final Object result, final JsonRpc2Error error, final Object id) {
        this(VERSION, result, error, id);
    }

    public JsonRpc2Response(final Object result, final Object id) {
        this(result, null, id);
    }

    public JsonRpc2Response(final JsonRpc2Error error, final Object id) {
        this(null, error, id);
    }

    @Override
    public String toString() {
        return "JsonRpc2Response{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", result=" + result +
                ", error=" + error +
                ", id=" + id +
                '}';
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public Object getResult() {
        return result;
    }

    public JsonRpc2Error getError() {
        return error;
    }

    public Object getId() {
        return id;
    }

    @JsonIgnore
    public boolean isSuccess() {
        return result != null;
    }

    @JsonIgnore
    public boolean isError() {
        return !isSuccess();
    }
}
