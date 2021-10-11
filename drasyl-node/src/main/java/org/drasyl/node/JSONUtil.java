/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.node.identity.serialization.IdentityMixin;
import org.drasyl.node.identity.serialization.IdentityPublicKeyMixin;
import org.drasyl.node.identity.serialization.IdentitySecretKeyMixin;
import org.drasyl.node.identity.serialization.KeyAgreementPublicKeyMixin;
import org.drasyl.node.identity.serialization.KeyAgreementSecretKeyMixin;
import org.drasyl.node.identity.serialization.KeyPairMixin;
import org.drasyl.node.identity.serialization.ProofOfWorkMixin;

/**
 * Holder for the JSON serializer and JSON deserializer.
 */
public final class JSONUtil {
    public static final ObjectMapper JACKSON_MAPPER;
    public static final ObjectWriter JACKSON_WRITER;
    public static final ObjectReader JACKSON_READER;

    static {
        JACKSON_MAPPER = new ObjectMapper();
        JACKSON_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        JACKSON_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
        JACKSON_MAPPER.addMixIn(IdentitySecretKey.class, IdentitySecretKeyMixin.class);
        JACKSON_MAPPER.addMixIn(KeyAgreementPublicKey.class, KeyAgreementPublicKeyMixin.class);
        JACKSON_MAPPER.addMixIn(KeyAgreementSecretKey.class, KeyAgreementSecretKeyMixin.class);
        JACKSON_MAPPER.addMixIn(Identity.class, IdentityMixin.class);
        JACKSON_MAPPER.addMixIn(ProofOfWork.class, ProofOfWorkMixin.class);
        JACKSON_MAPPER.addMixIn(KeyPair.class, KeyPairMixin.class);

        JACKSON_WRITER = JACKSON_MAPPER.writer();
        JACKSON_READER = JACKSON_MAPPER.reader();
    }

    private JSONUtil() {
        // util class
    }
}
