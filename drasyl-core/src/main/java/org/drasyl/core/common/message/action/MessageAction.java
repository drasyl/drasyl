package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.Message;

/**
 * This class describes how a client or server has to respond when receiving a {@link Message} of
 * type <code>T</code>.
 *
 * @param <T>
 */
public interface MessageAction<T extends Message> {
}
