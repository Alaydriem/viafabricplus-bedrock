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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.cloudburstmc.netty.channel.nethernet.signaling.IceServerInfo;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.junit.jupiter.api.Test;

class NetherNetHttpProbingSignalingTest {

    private static final long TIMEOUT_SECONDS = 15;
    private static final String ANSWER = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\ns=-\r\n";

    /**
     * Why this wrapper exists: the upstream signaling turns away a vanilla BDS. When this starts
     * failing, upstream accepts an empty status and the wrapper can go.
     */
    @Test
    void upstreamSignalingStillRejectsEmptyStatus() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(200, "")) {
            final CompletableFuture<List<IceServerInfo>> connect = new NetherNetHTTPClientSignaling().connect(server.address());

            final ExecutionException failure = assertThrows(ExecutionException.class, () -> connect.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("something other than a NetherNet status"), failure.getCause().getMessage());
        }
    }

    @Test
    void connectSucceedsAgainstEmptyStatus() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(200, "")) {
            final List<IceServerInfo> iceServers = new NetherNetHttpProbingSignaling().connect(server.address()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(List.of(), iceServers);
        }
    }

    @Test
    void connectFailsAgainst404() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.statusOnly(404, "Not Found")) {
            final CompletableFuture<List<IceServerInfo>> connect = new NetherNetHttpProbingSignaling().connect(server.address());

            final ExecutionException failure = assertThrows(ExecutionException.class, () -> connect.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertInstanceOf(ConnectException.class, failure.getCause());
        }
    }

    /** The channel sets its handler before it calls connect, so the handler must survive the probe. */
    @Test
    void signalHandlerSetBeforeConnectReceivesAnswer() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.start(200, "", 200, ANSWER)) {
            final NetherNetHttpProbingSignaling signaling = new NetherNetHttpProbingSignaling();
            final CompletableFuture<String> signal = new CompletableFuture<>();
            signaling.setSignalHandler("1234", signal::complete);

            signaling.connect(server.address()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            signaling.sendDescription("0", "v=0\r\n");

            final String received = signal.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(received.contains("1234"), received);
            assertTrue(received.contains("o=- 1 1 IN IP4 127.0.0.1"), received);
        }
    }

    @Test
    void failureHandlerSetBeforeConnectReceivesRefusal() throws Exception {
        try (StubSignalingServer server = StubSignalingServer.start(200, "", 500, "no")) {
            final NetherNetHttpProbingSignaling signaling = new NetherNetHttpProbingSignaling();
            final CompletableFuture<String> failure = new CompletableFuture<>();
            signaling.setFailureHandler(failure::complete);

            signaling.connect(server.address()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            signaling.sendDescription("0", "v=0\r\n");

            final String reason = failure.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(reason.contains("500"), reason);
        }
    }

}
