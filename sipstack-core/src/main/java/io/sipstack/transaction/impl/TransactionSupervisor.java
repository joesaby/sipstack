/**
 * 
 */
package io.sipstack.transaction.impl;

import static io.sipstack.actor.ActorUtils.safePostStop;
import static io.sipstack.actor.ActorUtils.safePreStart;
import io.pkts.packet.sip.SipMessage;
import io.sipstack.actor.Actor;
import io.sipstack.actor.ActorContext;
import io.sipstack.actor.ActorRef;
import io.sipstack.actor.Supervisor;
import io.sipstack.config.TransactionLayerConfiguration;
import io.sipstack.event.Event;
import io.sipstack.event.SipEvent;
import io.sipstack.transaction.Transaction;
import io.sipstack.transaction.TransactionId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jonas@jonasborjesson.com
 * 
 */
public class TransactionSupervisor implements Actor, Supervisor {

    private final Logger logger = LoggerFactory.getLogger(TransactionSupervisor.class);

    private final Map<TransactionId, TransactionActor> transactions = new HashMap<>(100, 0.75f);

    private final TransactionLayerConfiguration config;

    /**
     * 
     */
    public TransactionSupervisor(final TransactionLayerConfiguration config) {
        this.config = config;
    }

    /**
     * Get the {@link Transaction} for the given {@link TransactionId}.
     * 
     * Note: this can ONLY be called from the thread that normally handles transactions for this
     * supervisor. Calling this from another thread is not safe. Hence, ONLY the internal
     * implementation to sipstack should be using this method.
     * 
     * @param id
     * @return
     */
    public Transaction getTransaction(final TransactionId id) {
        final TransactionActor t = this.transactions.get(id);
        if (t != null) {
            return t.getTransaction();
        }

        return null;
    }

    public TransactionLayerConfiguration getConfig() {
        return this.config;
    }

    private TransactionActor ensureTransaction(final TransactionId id, final SipEvent event) {
        final TransactionActor t = this.transactions.get(id);
        if (t != null) {
            return t;
        }

        // if this is an ACK and we didn't find a transaction for this
        // ACK that can only mean that this is an ACK to a 2xx response
        // and therefore this ACK doesn't really have a transaction (an
        // ACK goes in its own transaction for 2xx responses but ACK doesn't
        // expect a response so therefore we will not actually create a new
        // transaction for it)
        if (event.getSipMessage().isAck()) {
            return null;
        }

        final TransactionActor newTransaction = TransactionActor.create(this, id, event);
        final Optional<Throwable> exception = safePreStart(newTransaction);
        if (exception.isPresent()) {
            // TODO: do something about it... such as do not put it in the transactions table
            throw new RuntimeException("The actor threw an exception in PostStop and I havent coded that up yet",
                    exception.get());
        }
        this.transactions.put(id, newTransaction);
        return newTransaction;
    }

    @Override
    public void onEvent(final ActorContext ctx, final Event event) {
        if (event.isSipEvent()) {
            final SipEvent sipEvent = event.toSipEvent();
            final SipMessage msg = sipEvent.getSipMessage();
            final TransactionId id = TransactionId.create(msg);

            // because of the msg.getMethod()
            // if (this.logger.isDebugEnabled()) {
            // this.logger.debug("[{}] Processing SIP event for transaction {} for an {}", this, id,
            // msg.getMethod());
            // }

            final TransactionActor t = ensureTransaction(id, sipEvent);
            if (t != null) {
                ctx.replace(t);
            }
            ctx.forward(event);
        }
    }

    @Override
    public Supervisor getSupervisor() {
        // we are a supervisor so we don't have one ourselves.
        return null;
    }

    @Override
    public void killChild(final Actor actor) {
        try {
            // can only be a TransactionActor
            final TransactionId id = ((TransactionActor) actor).getTransactionId();
            final TransactionActor transaction = this.transactions.remove(id);
            final Optional<Throwable> exception = safePostStop(transaction);
            if (exception.isPresent()) {
                // TODO: do something about it.
                throw new RuntimeException("The actor threw an exception in PostStop and I havent coded that up yet",
                        exception.get());
            }
        } catch (final ClassCastException e) {
            // strange...
            throw e;
        }
    }

    @Override
    public ActorRef self() {
        // TODO Auto-generated method stub
        return null;
    }

}
