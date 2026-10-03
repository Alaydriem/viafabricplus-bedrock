/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.protocoltranslator.network;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;
import org.cloudburstmc.netty.channel.nethernet.signaling.IceServerInfo;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetClientSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;

/**
 * HTTP signaling that probes with {@link NetherNetHttpProbe} and then hands the connection to
 * the upstream {@link NetherNetHTTPClientSignaling} with the scheme fixed, which skips the
 * upstream probe that turns away a vanilla BDS. One instance serves one connection.
 * <p>
 * The channel sets its handlers before it calls {@link #connect}, while the upstream signaling
 * only exists once the probe has answered, so the handlers are kept here and passed on.
 */
public final class NetherNetHttpProbingSignaling implements NetherNetClientSignaling {

    private NetherNetHTTPClientSignaling delegate;
    private String connectionId;
    private SignalHandler signalHandler;
    private FailureHandler failureHandler;
    private boolean closed;

    @Override
    public CompletableFuture<List<IceServerInfo>> connect(final SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress endpoint)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("HTTP signaling needs an InetSocketAddress, not " + remoteAddress));
        }
        return NetherNetHttpProbe.probe(NetherNetHttpProbe.DEFAULT_CLIENT, endpoint).thenCompose(result -> {
            final NetherNetHTTPClientSignaling signaling = new NetherNetHTTPClientSignaling(HttpSignalingSettings.DEFAULT.withScheme(result.scheme()));
            this.install(signaling);
            return signaling.connect(endpoint);
        });
    }

    private synchronized void install(final NetherNetHTTPClientSignaling signaling) {
        if (this.signalHandler != null) {
            signaling.setSignalHandler(this.connectionId, this.signalHandler);
        }
        if (this.failureHandler != null) {
            signaling.setFailureHandler(this.failureHandler);
        }
        if (this.closed) {
            signaling.close();
        }
        this.delegate = signaling;
    }

    private synchronized NetherNetHTTPClientSignaling connected() {
        if (this.delegate == null) {
            throw new IllegalStateException("The signaling has not connected yet");
        }
        return this.delegate;
    }

    @Override
    public boolean usesTrickleIce() {
        return false;
    }

    @Override
    public void sendDescription(final String targetNetworkId, final String sdp) {
        this.connected().sendDescription(targetNetworkId, sdp);
    }

    @Override
    public synchronized void setSignalHandler(final String connectionId, final SignalHandler handler) {
        this.connectionId = connectionId;
        this.signalHandler = handler;
        if (this.delegate != null) {
            this.delegate.setSignalHandler(connectionId, handler);
        }
    }

    @Override
    public synchronized void removeSignalHandler(final String connectionId) {
        if (connectionId.equals(this.connectionId)) {
            this.signalHandler = null;
        }
        if (this.delegate != null) {
            this.delegate.removeSignalHandler(connectionId);
        }
    }

    @Override
    public synchronized void setFailureHandler(final FailureHandler handler) {
        this.failureHandler = handler;
        if (this.delegate != null) {
            this.delegate.setFailureHandler(handler);
        }
    }

    @Override
    public String getLocalNetworkId() {
        return this.connected().getLocalNetworkId();
    }

    @Override
    public synchronized boolean isActive() {
        return !this.closed;
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        if (this.delegate != null) {
            this.delegate.close();
        }
    }

}
