package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

@AutoValue
@SuppressWarnings("java:S1118")
public abstract class CyclonShuffleResponse implements CyclonMessage {
    public static CyclonShuffleResponse of(final CyclonView view) {
        return new AutoValue_CyclonShuffleResponse(view);
    }
}
