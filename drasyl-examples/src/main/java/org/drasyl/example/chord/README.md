# Chord - A Distributed Hash Table

```bash
mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordNode" -Didentity=/Users/heiko/Development/drasyl-non-public/Identities/drasyl-1.identity -Dexec.args=""

for i in {2..10}; do tmux new-session -d -s drasyl-node-$i "mvn compile exec:java -Dexec.mainClass=\"org.drasyl.example.chord.ChordNode\" -Didentity=/Users/heiko/Development/drasyl-non-public/Identities/drasyl-${i}.identity -Dexec.args=\"023dad452b3dbb223fddd8a72324bd1018f2b4af900180e3748877fefbe216e7\""; sleep 60; done

tmux new-session -d -s drasyl-node-0 "mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordNode" -Didentity=drasyl-1.identity -Dexec.args=""" && for i in {2..100}; do; echo $i; tmux new-session -d -s drasyl-node-$i "mvn compile exec:java -Dexec.mainClass=\"org.drasyl.example.chord.ChordNode\" -Didentity=/Users/heiko/Development/drasyl-non-public/Identities/drasyl-${i}.identity -Dexec.args=\"023dad452b3dbb223fddd8a72324bd1018f2b4af900180e3748877fefbe216e7\""; sleep 1; done
```
