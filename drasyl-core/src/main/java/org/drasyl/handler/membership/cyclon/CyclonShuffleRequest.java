package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

@AutoValue
@SuppressWarnings("java:S1118")
public abstract class CyclonShuffleRequest implements CyclonMessage {
    public static CyclonShuffleRequest of(final CyclonView view) {
        return new AutoValue_CyclonShuffleRequest(view);
    }
}
