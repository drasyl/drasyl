package org.drasyl.handler.membership.cyclon;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.TextNode;
import org.drasyl.identity.IdentityPublicKey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class SocketAddressDeserializer extends JsonDeserializer<SocketAddress> {
    @Override
    public SocketAddress deserialize(final JsonParser p,
                                     final DeserializationContext ctxt) throws IOException {
        final TreeNode treeNode = p.getCodec().readTree(p);
        if (treeNode instanceof TextNode) {
            final TextNode textNode = (TextNode) treeNode;
            final String value = textNode.textValue();
            if (value.length() < IdentityPublicKey.KEY_LENGTH_AS_STRING) {
                // expect InetSocketAddress
                // https://github.com/FasterXML/jackson-databind/blob/aebeedfdc22e0068051a45c318b9ece838958a33/src/main/java/com/fasterxml/jackson/databind/deser/std/FromStringDeserializer.java#L328-L349
                // bracketed IPv6 (with port number)
                if (value.startsWith("[")) {
                    // bracketed IPv6 (with port number)

                    final int i = value.lastIndexOf(']');
                    if (i == -1) {
                        throw new InvalidFormatException(ctxt.getParser(),
                                "Bracketed IPv6 address must contain closing bracket",
                                value, InetSocketAddress.class);
                    }

                    final int j = value.indexOf(':', i);
                    final int port = j > -1 ? Integer.parseInt(value.substring(j + 1)) : 0;
                    return new InetSocketAddress(value.substring(0, i + 1), port);
                }
                final int ix = value.indexOf(':');
                if (ix >= 0 && value.indexOf(':', ix + 1) < 0) {
                    // host:port
                    final int port = Integer.parseInt(value.substring(ix + 1));
                    return new InetSocketAddress(value.substring(0, ix), port);
                }
                // host or unbracketed IPv6, without port number
                return new InetSocketAddress(value, 0);
            }
            else {
                // expect IdentityPublicKey
                try {
                    return IdentityPublicKey.of(value);
                }
                catch (final IllegalArgumentException e) {
                    throw new IOException("Invalid publix key string:", e);
                }
            }
        }
        else {
            throw new IOException("Unexpected Tree Node: " + treeNode.getClass().toString());
        }
    }
}
