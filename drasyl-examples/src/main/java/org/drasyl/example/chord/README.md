# Chord Distributed Lookup Protocol

This example implements the Chord distributed lookup protocol.

The protocol is described in:
> I. Stoica et al., "Chord: a scalable peer-to-peer lookup protocol for Internet applications," in IEEE/ACM Transactions on Networking, vol. 11, no. 1, pp. 17-32, Feb. 2003.
> 
> [https://doi.org/10.1109/TNET.2002.808407](https://doi.org/10.1109/TNET.2002.808407)

## Usage

1. Start a bootstrap peer: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordCircleNode"`
2. Wait for identity creation and then copy the peer address (`My address = ...`).
3. Start as many additional peers as you want. Replace `N` with a given unique number (e.g.,
   2, 3, 4, ...): `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordCircleNode" -Didentity=chord-N.identity -Dexec.args="<bootstrap_address>"`
4. Now you can perform chord lookups: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.chord.ChordLookupNode"`
