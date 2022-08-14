# Chord Distributed Lookup Protocol

This example implements the Chord distributed lookup protocol.

The protocol is described in:

> I. Stoica et al., "Chord: a scalable peer-to-peer lookup protocol for Internet applications," in IEEE/ACM Transactions on Networking, vol. 11, no. 1, pp. 17-32, Feb. 2003. https://doi.org/10.1109/TNET.2002.808407

```bash
mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordCircleNode" -Didentity=/root/Identities/drasyl-1.identity -Dexec.args=""

for i in {2..10}; do tmux new-session -d -s drasyl-node-$i "mvn compile exec:java -Dexec.mainClass=\"org.drasyl.example.chord.ChordCircleNode\" -Didentity=/Users/heiko/Development/drasyl-non-public/Identities/drasyl-${i}.identity -Dexec.args=\"023dad452b3dbb223fddd8a72324bd1018f2b4af900180e3748877fefbe216e7\""; sleep 60; done

tmux new-session -d -s drasyl-node-0 "mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordCircleNode" -Didentity=drasyl-1.identity -Dexec.args=""" && for i in {2..100}; do; echo $i; tmux new-session -d -s drasyl-node-$i "mvn compile exec:java -Dexec.mainClass=\"org.drasyl.example.chord.ChordCircleNode\" -Didentity=/Users/heiko/Development/drasyl-non-public/Identities/drasyl-${i}.identity -Dexec.args=\"023dad452b3dbb223fddd8a72324bd1018f2b4af900180e3748877fefbe216e7\""; sleep 1; done
```
