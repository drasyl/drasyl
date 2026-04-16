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
package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.drasyl.jtasklet.util.SourceUtil.minifySource;

public class OffloadTask implements TaskletMessage {
    private final String token;
    private final String source;
    private final String session;
    private final List<String> clientIds;
    private final String ciphertexts;
    private final java.util.List<java.util.List<Integer>> weights;

    @JsonCreator
    public OffloadTask(@JsonProperty("token") final String token,
                       @JsonProperty("source") final String source,
                       @JsonProperty("session") final String session,
                       @JsonProperty("clientIds") final List<String> clientIds,
                       @JsonProperty("ciphertexts") final String ciphertexts,
                       @JsonProperty("weights") final List<List<Integer>> weights) {
        this.token = requireNonNull(token);
        this.source = requireNonNull(source);
        this.session = requireNonNull(session);
        this.clientIds = requireNonNull(clientIds);
        this.ciphertexts = requireNonNull(ciphertexts);
        this.weights = requireNonNull(weights);
    }

    public OffloadTask(final String token,
                       final String source,
                       final Object[] input) {
        this(token, source, "", List.of(), String.valueOf(input), List.of());
    }

    @Override
    public String toString() {
        return "OffloadTask{" +
                "token=" + token +
                ", source='" + minifySource(source) + '\'' +
                ", session='" + session + '\'' +
                ", clientIds=List[" + clientIds.size() + "]" +
                ", ciphertexts=***" +
                ", weights=List[" + weights.size() + "]}";
    }

    public String getToken() {
        return token;
    }

    public String getSource() {
        return source;
    }

    public String getSession() {
        return session;
    }

    public List<String> getClientIds() {
        return clientIds;
    }

    public String getCiphertexts() {
        return ciphertexts;
    }

    public List<List<Integer>> getWeights() {
        return weights;
    }
}
