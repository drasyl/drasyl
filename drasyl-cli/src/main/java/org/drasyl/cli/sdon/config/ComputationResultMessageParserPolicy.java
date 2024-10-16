package org.drasyl.cli.sdon.config;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ComputationResultMessageParserPolicy extends Policy {
    private static final Logger LOG = LoggerFactory.getLogger(ComputationResultMessageParserPolicy.class);
    public static final String HANDLER_NAME = StringUtil.simpleClassName(ComputationResultMessageParserPolicy.class);

    public void addPolicy(final ChannelPipeline pipeline) {
    }

    @Override
    public void removePolicy(final ChannelPipeline pipeline) {
    }

    @Override
    public String toString() {
        return "ComputationResultMessageParserPolicy{" +
                '}';
    }
}
