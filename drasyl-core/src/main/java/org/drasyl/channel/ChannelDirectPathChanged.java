package org.drasyl.channel;

/**
 * Signals that the path to the remote peer has been changed from direct to relayed or vice versa.
 * Actual path type can be retrieved by calling {@link DrasylChannel#isDirectPathPresent()}.
 */
public final class ChannelDirectPathChanged {
    public static final ChannelDirectPathChanged INSTANCE = new ChannelDirectPathChanged();

    private ChannelDirectPathChanged() {
        // singleton
    }
}
