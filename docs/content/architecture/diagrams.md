# Sequence Diagrams

## Node Events

The state diagram below shows which events occur in which order during the lifetime of a drasyl node:

<div class="mermaid">
stateDiagram-v2
    state started <<fork>>
        [*] --> started
        started --> NodeUpEvent
        NodeUpEvent --> NodeDownEvent
        NodeUpEvent --> NodeOnlineEvent
        NodeOnlineEvent --> NodeOfflineEvent
        NodeOfflineEvent --> NodeOnlineEvent
        NodeOfflineEvent --> NodeDownEvent
        NodeOfflineEvent --> NodeIdentityCollisionEvent
        NodeUpEvent --> NodeIdentityCollisionEvent
        NodeIdentityCollisionEvent --> NodeOnlineEvent
        NodeIdentityCollisionEvent --> NodeDownEvent
        NodeDownEvent --> NodeNormalTerminationEvent
        started --> NodeUnrecoverableErrorEvent

    state join <<join>>
        NodeNormalTerminationEvent --> join
        NodeUnrecoverableErrorEvent --> join
        join --> [*]
</div>

## Client Server Communication

<div class="mermaid">
sequenceDiagram
  Note over Client,Server: Session State: New
  par Session Request
    Client->>Server: JoinMessage
    alt Session Created
        Server-->>Client: WelcomeMessage
      alt Session Confirmed
        Client-->>Server: StatusMessage.OK

        Note over Client,Server: Session State: Established

        alt Serverside Close
            Server->>Client: QuitMessage.$REASON
        else Clientside Close
            Client->>Server: QuitMessage.$REASON
        end

        Note over Client,Server: Session State: Terminated
      else Session Declined
        Client-->>Server: ???
      end  
    else Session Declined
        Server-->>Client: ???
    end
  and Healtcheck
    loop Every n Seconds
      Server->>Client: PingMessage
      Client-->>Server: PongMessage

      opt Ping Timeout
        Server->>Client: ConnectionExceptionMessage.PING_PONG 
      end
    end
  end
</div>

## Peer Session States

<div class="mermaid">
stateDiagram
	[*] --> Initialization
	Initialization --> Errored
	Initialization --> Active
	Active --> Errored
	Active --> Terminated
	Terminated --> [*]
	Errored --> [*]
</div>