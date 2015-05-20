/**
 * 
 */
package io.sipstack.transport;

import io.hektor.core.Actor;
import io.hektor.core.ActorRef;
import io.hektor.core.Props;
import io.sipstack.config.TransportLayerConfiguration;
import io.sipstack.event.Event;
import io.sipstack.event.IOReadEvent;
import io.sipstack.event.InitEvent;
import io.sipstack.netty.codec.sip.ConnectionId;
import io.sipstack.netty.codec.sip.SipMessageEvent;

import java.util.Optional;

/**
 * The {@link TransportSupervisor} is responsible for creating and maintaining
 * 
 * @author jonas@jonasborjesson.com
 */
public class TransportSupervisor implements Actor {

    private ActorRef upstreamActor;

    private final TransportLayerConfiguration config;

    public TransportSupervisor(final TransportLayerConfiguration config) {
        this.config = config;
    }

    @Override
    public void onReceive(final Object msg) {

        // Anything that are raw SipMessageEvent is only coming from
        // the sip support of netty, i.e., they are inbound so find
        // a flow and send the message to it.
        if (SipMessageEvent.class.isAssignableFrom(msg.getClass())) {
            final SipMessageEvent sipEvent = (SipMessageEvent)msg;
            final ConnectionId id = sipEvent.getConnection().id();
            final String idStr = id.encodeAsString();
            final Optional<ActorRef> child = ctx().child(idStr);
            final ActorRef flow = child.orElseGet(() ->  {
                final Props props = Props.forActor(FlowActor.class)
                        .withConstructorArg(upstreamActor)
                        .withConstructorArg(sipEvent.getConnection())
                        .withConstructorArg(config.getFlow())
                        .build();
                return ctx().actorOf(idStr, props);
            });

            flow.tell(IOReadEvent.create(sipEvent), self());

            // System.err.println(Thread.currentThread().getName() + " " + this);
        } else if (Event.class.isAssignableFrom(msg.getClass())) {
            final Event event = (Event)msg;
            if (event.isInitEvent()) {
                upstreamActor = ((InitEvent)event).upstreamSupervisor;
            }
        } else {
            System.err.println("[TransportSupervisor] No clue what I got!!! ");
        }
    }

}
