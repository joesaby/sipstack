/**
 * 
 */
package io.sipstack.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author jonas
 *
 */
public final class TransactionLayerConfiguration {
    
    /**
     * SIP specifications says that the Invite Server Transaction
     * should send 100 Trying if the TU won't within 200ms, however,
     * quite often  you may just want the transaction to do it right
     * away. In sipstack.io, it is the default behavior to send the 100 Trying
     * right away.
     */
    @JsonProperty
    private boolean send100TryingImmediately = true;

    @JsonProperty
    private TimersConfiguration timers = new TimersConfiguration();

    /**
     * @return the timers
     */
    public TimersConfiguration getTimers() {
        return this.timers;
    }

    /**
     * @param timers the timers to set
     */
    public void setTimers(final TimersConfiguration timers) {
        this.timers = timers;
    }

    /**
     * @return the send100TryingImmediately
     */
    public boolean isSend100TryingImmediately() {
        return this.send100TryingImmediately;
    }
    
    public void setSend100TryingImmediately(final boolean value) {
        this.send100TryingImmediately = value;
    }

}
