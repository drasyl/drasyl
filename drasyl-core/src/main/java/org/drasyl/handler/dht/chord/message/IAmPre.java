package org.drasyl.handler.dht.chord.message;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class IAmPre implements ChordMessage {
    public static IAmPre of() {
        return new AutoValue_IAmPre();
    }
}
