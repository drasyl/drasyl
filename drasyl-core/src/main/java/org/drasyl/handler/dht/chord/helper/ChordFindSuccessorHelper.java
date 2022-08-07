package org.drasyl.handler.dht.chord.helper;

import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordService;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.helper.ChordFindPredecessorHelper.findPredecessor;
import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * Helper class providing {@code n.find_predecessor(id)} functionality.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public final class ChordFindSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorHelper.class);

    private ChordFindSuccessorHelper() {
        // util class
    }

    public static FutureComposer<DrasylAddress> findSuccessor(final long id,
                                                              final ChordFingerTable fingerTable,
                                                              final RmiClientHandler client) {
        LOG.debug("Find successor of `{}`.", ChordUtil.chordIdHex(id));

        // initialize return value as this node's successor (might be null)
        final DrasylAddress ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", ChordUtil.chordIdHex(id));

        return findPredecessor(id, fingerTable, client)
                .chain(future -> {
                    final DrasylAddress pre = future.getNow();
                    // if other node found, ask it for its successor
                    if (!Objects.equals(pre, fingerTable.getLocalAddress())) {
                        final ChordService service = client.lookup("ChordService", ChordService.class, pre);
                        return composeFuture().chain(service.yourSuccessor());
                    }
                    else {
                        return composeFuture(ret);
                    }
                })
                .chain(future -> {
                    final DrasylAddress ret1 = future.getNow();
                    if (ret1 == null) {
                        return composeFuture(fingerTable.getLocalAddress());
                    }
                    return composeFuture(ret1);
                });
    }
}
