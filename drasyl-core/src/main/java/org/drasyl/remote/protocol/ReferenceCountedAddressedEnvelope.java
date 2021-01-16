package org.drasyl.remote.protocol;

import io.netty.util.ReferenceCounted;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;

abstract class ReferenceCountedAddressedEnvelope<A extends Address, M extends ReferenceCounted> extends DefaultAddressedEnvelope<A, M> implements ReferenceCounted {
    protected ReferenceCountedAddressedEnvelope(final A sender, final A recipient, final M content) {
        super(sender, recipient, content);
    }

    @Override
    public int refCnt() {
        return getContent().refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        return getContent().retain();
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        return getContent().retain(increment);
    }

    @Override
    public ReferenceCounted touch() {
        return getContent().touch();
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        return getContent().touch(hint);
    }

    @Override
    public boolean release() {
        return getContent().release();
    }

    @Override
    public boolean release(final int decrement) {
        return getContent().release(decrement);
    }
}
