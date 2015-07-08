package io.sipstack.transactionuser;

import java.util.function.Consumer;

import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipRequest;
import io.pkts.packet.sip.address.URI;

/**
 * @author ajansson@twilio.com
 */
public interface UA {

    void send(SipMessage message);

    void addHandler(Consumer<SipMessage> handler);

    interface Builder {

        Builder withTarget(URI target);

        Builder withRequest(SipRequest request);

        UA build();
    }
}
