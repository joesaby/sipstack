package io.sipstack.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.pkts.packet.sip.address.SipURI;
import io.sipstack.config.NetworkInterfaceConfiguration;
import io.sipstack.netty.codec.sip.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.pkts.packet.sip.impl.PreConditions.assertNotNull;
import static io.pkts.packet.sip.impl.PreConditions.ensureNotNull;

/**
 *
 * @author jonas@jonasborjesson.com
 */
public final class NetworkInterface implements ChannelFutureListener {

    private final Logger logger = LoggerFactory.getLogger(NetworkInterface.class);

    private final String name;

    private CountDownLatch latch;
    private final Bootstrap udpBootstrap;

    private final ServerBootstrap tcpBootstrap;

    private final List<ListeningPoint> listeningPoints;


    private NetworkInterface(final String name, final Bootstrap udpBootstrap, final ServerBootstrap tcpBootstrap,
            final List<ListeningPoint> lps) {
        this.name = name;
        this.udpBootstrap = udpBootstrap;
        this.tcpBootstrap = tcpBootstrap;
        this.listeningPoints = lps;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Bring this interface up, as in start listening to its dedicated listening points.
     */
    public void up() {

        final CountDownLatch bindLatch = new CountDownLatch(this.listeningPoints.size());
        final List<ListeningPoint> errors = Collections.synchronizedList(new ArrayList<ListeningPoint>());
        this.listeningPoints.forEach(lp -> {
            final ListeningPoint listenPoint = lp;
            final SipURI listen = lp.getListenAddress();
            final Transport transport = getTransport(listen);
            final int port = getPort(listen.getPort(), transport);
            final SocketAddress address = new InetSocketAddress(listen.getHost().toString(), port);

            ChannelFuture future = null;
            if (transport == Transport.udp) {
                future = this.udpBootstrap.bind(address);
            } else if (transport == Transport.tcp) {
                future = this.tcpBootstrap.bind(address);
            } else {
                // should already have been ensured elsewhere but let's check again
                throw new IllegalTransportException("Can only do UDP and TCP for now");
            }

            future.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isDone() && future.isSuccess()) {
                        final Channel channel = future.channel();
                        listenPoint.setChannel(channel);
                        NetworkInterface.this.logger.info("Successfully bound to listening point: " + listenPoint);
                    } else if (future.isDone() && !future.isSuccess()) {
                        NetworkInterface.this.logger.info("Unable to bind to listening point: " + listenPoint);
                        errors.add(listenPoint);
                    }
                    bindLatch.countDown();
                }
            });
        });

        try {
            bindLatch.await();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Unable to bind to one or more listening points");
        }

    }

    public void down() {

    }

    private Transport getTransport(final SipURI uri) {
        return Transport.valueOf(uri.getTransportParam().toString());
    }

    private int getPort(final int port, final Transport transport) {
        if (port >= 0) {
            return port;
        }

        if (transport == Transport.tls) {
            return 5061;
        }

        if (transport == Transport.ws) {
            return 5062;
        }

        if (transport == Transport.sctp) {
            // TODO: not sure about this one but since
            // we currently do not support it then
            // let's leave it like this for now.
            return 5060;
        }

        return 5060;
    }

    /**
     * Use this {@link NetworkInterface} to connect to a remote address using the supplied
     * {@link Transport}.
     * 
     * Note, if the {@link Transport} is a connection less transport, such as UDP, then there isn't
     * a "connect" per se.
     * 
     * @param remoteAddress
     * @param transport
     * @return a {@link ChannelFuture} that, once completed, will contain the {@link Channel} that
     *         is connected to the remote address.
     * @throws IllegalTransportException in case the {@link NetworkInterface} isn't configured with
     *         the specified {@link Transport}
     */
    public ChannelFuture connect(final SocketAddress remoteAddress, final Transport transport)
            throws IllegalTransportException {
        return null;
    }

    static Builder with(final NetworkInterfaceConfiguration config) {
        assertNotNull(config);
        return new Builder(config);
    }


    public static class Builder {
        private final NetworkInterfaceConfiguration config;

        /**
         * Our netty boostrap for connection less protocols
         */
        private Bootstrap udpBootstrap;

        private ServerBootstrap tcpBootstrap;

        private CountDownLatch latch;


        private Builder(final NetworkInterfaceConfiguration config) {
            this.config = config;
        }

        public Builder latch(final CountDownLatch latch) {
            this.latch = latch;
            return this;
        }

        public Builder udpBootstrap(final Bootstrap bootstrap) {
            this.udpBootstrap = bootstrap;
            return this;
        }

        public Builder tcpBootstrap(final ServerBootstrap bootstrap) {
            this.tcpBootstrap = bootstrap;
            return this;
        }

        public NetworkInterface build() {
            ensureNotNull(this.latch, "Missing the latch");
            if (this.config.hasUDP()) {
                ensureNotNull(this.udpBootstrap, "You must configure a connectionless bootstrap");
            }

            if (this.config.hasTCP()) {
                ensureNotNull(this.tcpBootstrap, "You must configure a connection oriented bootstrap");
            }

            if (this.config.hasTLS() || this.config.hasWS() || this.config.hasSCTP()) {
                throw new IllegalTransportException("Sorry, can only do TCP and UDP for now");
            }

            final SipURI listenAddress = this.config.getListeningAddress();
            final SipURI vipAddress = this.config.getVipAddress();
            final List<ListeningPoint> lps = new ArrayList<>();
            this.config.getTransports().forEach(t -> {
                final SipURI listen = SipURI.withTemplate(listenAddress).withTransport(t.toString()).build();
                final SipURI vipClone = vipAddress != null ? vipAddress.clone() : null;
                lps.add(ListeningPoint.create(listen, vipClone));
            });

            return new NetworkInterface(this.config.getName(), this.udpBootstrap, this.tcpBootstrap,
                    Collections.unmodifiableList(lps));
        }

    }


    @Override
    public void operationComplete(final ChannelFuture future) throws Exception {
        // TODO Auto-generated method stub

    }

}
