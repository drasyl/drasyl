package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

@JsonTypeInfo(use = NAME)
@JsonSubTypes({
        @Type(value = VmHeartbeat.class),
        @Type(value = OffloadTask.class),
        @Type(value = ResourceRequest.class),
        @Type(value = ResourceResponse.class),
        @Type(value = ReturnResult.class)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface TaskletMessage {
}
