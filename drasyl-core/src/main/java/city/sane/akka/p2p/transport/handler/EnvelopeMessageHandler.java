package city.sane.akka.p2p.transport.handler;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.AddressFromURIString;
import akka.actor.ExtendedActorSystem;
import akka.actor.InternalActorRef;
import akka.protobuf.InvalidProtocolBufferException;
import akka.remote.SeqNo;
import akka.remote.WireFormats;
import akka.remote.WireFormats.SerializedMessage;
import akka.remote.transport.AkkaPduCodec;
import akka.remote.transport.AkkaPduCodec.Message$;
import akka.remote.transport.AkkaPduProtobufCodec;
import akka.serialization.Serialization$;
import akka.serialization.Serialization.Information;
import akka.util.OptionVal$;
import city.sane.akka.p2p.P2PActorRef;
import city.sane.akka.p2p.P2PActorRefProvider;
import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.OutboundMessageEnvelope;
import city.sane.akka.p2p.transport.direct.messages.AkkaMessage;
import city.sane.relay.common.handler.SimpleChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import scala.Function0;
import scala.Option$;

import static akka.remote.MessageSerializer.deserialize;
import static akka.remote.MessageSerializer.serialize;
import static akka.remote.WireFormats.AckAndEnvelopeContainer.parseFrom;

/**
 * This handler translates the message envelopes to AkkaMessages and vice versa.
 */
@ChannelHandler.Sharable
public class EnvelopeMessageHandler extends SimpleChannelDuplexHandler<AkkaMessage, OutboundMessageEnvelope> {
    private static final Address ALL_ADDRESS = new Address("bud", "ALL");
    private static final Address ANY_ADDRESS = new Address("bud", "ANY");

    private final ExtendedActorSystem system;
    private final P2PActorRefProvider provider;
    private final Address defaultAddress;

    public EnvelopeMessageHandler(ExtendedActorSystem system, P2PActorRefProvider provider, Address defaultAddress) {
        this.system = system;
        this.provider = provider;
        this.defaultAddress = defaultAddress;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, OutboundMessageEnvelope messageEnvelope) {
        // get information from message envelope
        Object message = messageEnvelope.getMessage();
        ActorRef sender = messageEnvelope.getSender();
        P2PActorRef recipient = messageEnvelope.getRecipient();

        // serialize the message so that it can be sent to the relay
        SerializedMessage serializedMessage = (SerializedMessage) Serialization$.MODULE$.currentTransportInformation().withValue(
                new Information(defaultAddress, system),
                (Function0<Object>) () -> serialize(system, message)
        );
        byte[] blob = constructMessage(serializedMessage, sender, recipient);
        String relayRecipientValue = recipient.path().root().address().system();

        // send message
        ctx.writeAndFlush(new AkkaMessage(blob, defaultAddress.system(), relayRecipientValue));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AkkaMessage akkaMessage) {
        // deserialize message so it can be sent to akka
        AkkaPduCodec.Message message = decodeMessage(akkaMessage.getBlob(), provider, defaultAddress);
        Object deserializedMessage = deserialize(system, message.serializedMessage());

        // collect information for message envelope
        ActorRef akkaSender = OptionVal$.MODULE$.getOrElse$extension(message.senderOption(), system.deadLetters());

        InternalActorRef akkaRecipient;
        Address akkaRecipientAddress;
        if (message.recipient().path().address().equals(ALL_ADDRESS) || message.recipient().path().address().equals(ANY_ADDRESS)) {
            akkaRecipient = provider.rootGuardian();
            akkaRecipientAddress = provider.rootGuardian().path().address();
        } else {
            akkaRecipient = message.recipient();
            akkaRecipientAddress = message.recipientAddress();
        }

        InboundMessageEnvelope messageEnvelope = new InboundMessageEnvelope(deserializedMessage, akkaSender, akkaRecipient, akkaRecipientAddress);
        ctx.fireChannelRead(messageEnvelope);
    }

    public static byte[] constructMessage(SerializedMessage serializedMessage, ActorRef sender, P2PActorRef recipient) {
        return AkkaPduProtobufCodec.constructMessage(
                recipient.getLocalAddressToUse(),
                recipient,
                serializedMessage,
                OptionVal$.MODULE$.apply(sender),
                Option$.MODULE$.apply(null),
                Option$.MODULE$.apply(null)
        ).toArray();
    }

    // from https://github.com/akka/akka/blob/6bf20f4117a8c27f8bd412228424caafe76a89eb/akka-remote/src/main/scala/akka/remote/transport/AkkaPduCodec.scala#L206-L235
    public static AkkaPduCodec.Message decodeMessage(byte[] blob, P2PActorRefProvider provider, Address localAddress) {
        try {
            WireFormats.AckAndEnvelopeContainer ackAndEnvelope = parseFrom(blob);
            WireFormats.RemoteEnvelope msgPdu = ackAndEnvelope.getEnvelope();

            InternalActorRef recipient = provider.resolveActorRefWithLocalAddress(msgPdu.getRecipient().getPath(), localAddress);
            Address recipientAddress = AddressFromURIString.apply(msgPdu.getRecipient().getPath());
            SerializedMessage serializedMessage = msgPdu.getMessage();

            ActorRef sender;
            if (msgPdu.hasSender()) {
                sender = provider.resolveActorRefWithLocalAddress(msgPdu.getSender().getPath(), localAddress);
            } else {
                sender = null;
            }

            SeqNo seq;
            if (msgPdu.hasSeq()) {
                seq = SeqNo.apply(msgPdu.getSeq());
            } else {
                seq = null;
            }

            return Message$.MODULE$.apply(recipient, recipientAddress, serializedMessage, OptionVal$.MODULE$.apply(sender), Option$.MODULE$.apply(seq));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

}