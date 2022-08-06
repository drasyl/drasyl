# CYCLON Membership Management

This example implements the CYCLON membership protocol.

The protocol is described in:
> Voulgaris, S., Gavidia, D. & van Steen, M. CYCLON: Inexpensive Membership Management for
> Unstructured P2P Overlays. J Netw Syst Manage 13, 197–217 (2005)
> . https://doi.org/10.1007/s10922-005-4441-x

## Usage

1. Start a bootstrap
   peer: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.cyclon.CyclonMembershipManagement"`
2. Wait for identity creation and then copy the peer address (`My address = ...`).
3. Start as many additional peers as you want. Replace `N` with a given unique number (e.g.,
   2, 3, 4, ...): `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.cyclon.CyclonMembershipManagement" -Didentity=cyclon-N.identity -Dexec.args="<bootstrap_address>"`
4. You will soon see that every peer's neighbor view will get populated.
