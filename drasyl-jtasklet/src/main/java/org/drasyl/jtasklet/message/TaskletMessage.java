package org.drasyl.jtasklet.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

@JsonTypeInfo(use = NAME)
@JsonSubTypes({
        // Provider registration process
        @Type(value = RegisterProvider.class),
        // Actual offloading process
        @Type(value = ResourceRequest.class),
        @Type(value = ResourceResponse.class),
        @Type(value = OffloadTask.class),
        @Type(value = ReturnResult.class),
        // Provider status updates
        @Type(value = TaskOffloaded.class),
        @Type(value = TaskExecuting.class),
        @Type(value = TaskExecuted.class),
        @Type(value = TaskResultReceived.class),
        @Type(value = TaskReset.class),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface TaskletMessage {
}
