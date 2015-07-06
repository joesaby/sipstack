package io.sipstack.transactionuser;

import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipRequest;
import io.pkts.packet.sip.SipResponse;
import io.sipstack.transaction.Transaction;
import io.sipstack.transaction.TransactionUser;
import io.sipstack.transaction.Transactions;

/**
 * @author jonas@jonasborjesson.com
 */
public class DefaultTransactionUser implements TransactionUser {

    private final Transactions transactions;

    public DefaultTransactionUser(final Transactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public void onRequest(Transaction transaction, SipRequest request) {
        if (!request.isAck()) {
            // transaction.send(request.createResponse(200));
            transactions.send(request.createResponse(200));

            Transaction t2 = transactions.send(request.clone());
            // tie together in a UA object
        }
    }

    @Override
    public void onResponse(Transaction transaction, SipResponse response) {

    }

    @Override
    public void onTransactionTerminated(Transaction transaction) {

    }

    @Override
    public void onIOException(Transaction transaction, SipMessage msg) {

    }
}