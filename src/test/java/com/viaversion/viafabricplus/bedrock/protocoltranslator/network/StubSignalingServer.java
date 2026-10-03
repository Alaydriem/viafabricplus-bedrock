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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A NetherNet HTTP signaling endpoint on loopback that behaves like a vanilla BDS: a TLS
 * ClientHello gets a fatal handshake_failure alert, and plaintext HTTP gets fixed replies.
 */
final class StubSignalingServer implements AutoCloseable {

    private static final int TLS_HANDSHAKE_RECORD = 0x16;
    /** What tbs8.nodecraft.gg:19132 sends back to a ClientHello: a fatal handshake_failure alert. */
    private static final byte[] TLS_HANDSHAKE_FAILURE = {0x15, 0x03, 0x01, 0x00, 0x02, 0x02, 0x28};

    private final ServerSocket socket;
    private final int statusCode;
    private final String statusBody;
    private final int joinCode;
    private final String joinBody;

    private StubSignalingServer(final ServerSocket socket, final int statusCode, final String statusBody, final int joinCode, final String joinBody) {
        this.socket = socket;
        this.statusCode = statusCode;
        this.statusBody = statusBody;
        this.joinCode = joinCode;
        this.joinBody = joinBody;
    }

    /**
     * @param statusCode The status of {@code GET /v1/join}
     * @param statusBody Its body, empty for what a vanilla BDS sends
     * @param joinCode   The status of {@code POST /v1/join/{networkId}}
     * @param joinBody   Its body, the SDP answer on success
     */
    static StubSignalingServer start(final int statusCode, final String statusBody, final int joinCode, final String joinBody) throws IOException {
        final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final StubSignalingServer server = new StubSignalingServer(socket, statusCode, statusBody, joinCode, joinBody);
        Thread.ofVirtual().start(server::acceptLoop);
        return server;
    }

    static StubSignalingServer statusOnly(final int statusCode, final String statusBody) throws IOException {
        return start(statusCode, statusBody, 404, "");
    }

    InetSocketAddress address() {
        return (InetSocketAddress) this.socket.getLocalSocketAddress();
    }

    private void acceptLoop() {
        while (!this.socket.isClosed()) {
            try {
                final Socket client = this.socket.accept();
                Thread.ofVirtual().start(() -> this.serve(client));
            } catch (IOException ignored) {
                return; // Closed
            }
        }
    }

    private void serve(final Socket client) {
        try (client) {
            final InputStream in = new BufferedInputStream(client.getInputStream());
            final OutputStream out = client.getOutputStream();
            in.mark(1);
            if (in.read() == TLS_HANDSHAKE_RECORD) {
                out.write(TLS_HANDSHAKE_FAILURE);
                out.flush();
                return;
            }
            in.reset();

            final String requestLine = readLine(in);
            int contentLength = 0;
            for (String header = readLine(in); !header.isEmpty(); header = readLine(in)) {
                final String lower = header.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(lower.substring("content-length:".length()).trim());
                }
            }
            in.readNBytes(contentLength);

            if (requestLine.startsWith("GET /v1/join ")) {
                reply(out, this.statusCode, "application/json", this.statusBody);
            } else if (requestLine.startsWith("POST /v1/join/")) {
                reply(out, this.joinCode, "application/sdp", this.joinBody);
            } else {
                reply(out, 404, "text/plain", "Not Found");
            }
        } catch (IOException ignored) {
            // The client went away, which is its business
        }
    }

    private static String readLine(final InputStream in) throws IOException {
        final ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int b = in.read(); b != -1 && b != '\n'; b = in.read()) {
            if (b != '\r') {
                line.write(b);
            }
        }
        return line.toString(StandardCharsets.US_ASCII);
    }

    private static void reply(final OutputStream out, final int code, final String contentType, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        final String head = "HTTP/1.1 " + code + " Stub\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + bytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }

}
