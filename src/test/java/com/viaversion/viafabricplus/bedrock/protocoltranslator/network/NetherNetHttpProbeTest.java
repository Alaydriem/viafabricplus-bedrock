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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings.Scheme;
import org.junit.jupiter.api.Test;

class NetherNetHttpProbeTest {

    private static final long TIMEOUT_SECONDS = 15;

    /** A vanilla BDS answers 200 with no body. Requiring a parseable status turns it away. */
    @Test
    void acceptsEmptyBodyOn200() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(200, "")) {
            final NetherNetHttpProbe.Result result = probe(server.address());

            assertEquals(Scheme.HTTP, result.scheme());
            assertEquals("", result.body());
        }
    }

    /** The server list reads the status from the body, so the probe must hand it over. */
    @Test
    void returnsBodyOn200() throws Exception {
        final String status = "{\"name\":\"Server\",\"protocol\":1000,\"players\":1,\"maxPlayers\":10}";
        try (StubSignalingServer server = StubSignalingServer.statusOnly(200, status)) {
            assertEquals(status, probe(server.address()).body());
        }
    }

    /** A body that is not a status does not change the answer; only the status code does. */
    @Test
    void acceptsNonJsonBodyOn200() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(200, "<html>not json</html>")) {
            assertEquals(Scheme.HTTP, probe(server.address()).scheme());
        }
    }

    /** The server list hands over a host name it has not resolved, so the probe must not need an address. */
    @Test
    void acceptsUnresolvedAddress() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(200, "")) {
            final InetSocketAddress unresolved = InetSocketAddress.createUnresolved("127.0.0.1",server.address().getPort());

            assertEquals(Scheme.HTTP, probe(unresolved).scheme());
        }
    }

    @Test
    void rejects404() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(404, "Not Found")) {
            final ConnectException cause = probeFailure(server.address());

            assertTrue(cause.getMessage().contains("does not serve NetherNet"), cause.getMessage());
        }
    }

    @Test
    void rejectsOtherNon2xx() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(503, "busy")) {
            final ConnectException cause = probeFailure(server.address());

            assertTrue(cause.getMessage().contains("503"), cause.getMessage());
        }
    }

    @Test
    void failsWhenNothingListens() throws Exception {
        final InetSocketAddress closed;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closed = (InetSocketAddress) socket.getLocalSocketAddress();
        }

        final ConnectException cause = probeFailure(closed);

        assertTrue(cause.getMessage().contains("could not be reached"), cause.getMessage());
    }

    private static NetherNetHttpProbe.Result probe(final InetSocketAddress address) throws Exception {
        return NetherNetHttpProbe.probe(NetherNetHttpProbe.DEFAULT_CLIENT, address).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static ConnectException probeFailure(final InetSocketAddress address) {
        final ExecutionException failure = assertThrows(ExecutionException.class,
            () -> NetherNetHttpProbe.probe(NetherNetHttpProbe.DEFAULT_CLIENT, address).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return assertInstanceOf(ConnectException.class, failure.getCause());
    }

}
