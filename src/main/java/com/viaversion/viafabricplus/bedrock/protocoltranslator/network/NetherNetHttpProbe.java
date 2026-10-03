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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import javax.net.ssl.SSLException;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings.Scheme;

/**
 * Asks a NetherNet HTTP signaling endpoint whether it serves NetherNet, the way the retail client
 * does: HTTPS first, plaintext when the host answered without TLS.
 * <p>
 * Only the status code decides. A vanilla BDS answers {@code GET /v1/join} with 200 and no body,
 * which the upstream probe in {@code NetherNetHTTPClientSignaling} turns away because it requires
 * a parseable status. The body is handed back as is for whoever wants to show it.
 */
public final class NetherNetHttpProbe {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Built like the upstream signaling client, so both reach a server the same way. */
    public static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    /**
     * @param scheme {@link Scheme#HTTPS} or {@link Scheme#HTTP}, whichever answered
     * @param body   The status body, empty when the server sent none
     */
    public record Result(Scheme scheme, String body) {
    }

    private NetherNetHttpProbe() {
    }

    /**
     * @return The scheme that answered with a 2xx, or a failure around a {@link ConnectException}
     * saying why the endpoint does not serve NetherNet
     */
    public static CompletableFuture<Result> probe(final HttpClient http, final InetSocketAddress address) {
        return get(http, address, Scheme.HTTPS).handle((response, error) -> {
            if (error == null) {
                // An answer over HTTPS is final, whatever it says
                return CompletableFuture.completedFuture(probed(address, response, Scheme.HTTPS));
            }
            final Throwable cause = unwrap(error);
            if (!(cause instanceof SSLException)) {
                // Nothing answered, so plaintext would only wait through the same failure again
                throw refused(address + " could not be reached: " + describe(cause));
            }
            // Something answered and refused TLS, which is the one case plaintext is for
            return get(http, address, Scheme.HTTP).thenApply(fallback -> probed(address, fallback, Scheme.HTTP));
        }).thenCompose(Function.identity());
    }

    private static CompletableFuture<HttpResponse<String>> get(final HttpClient http, final InetSocketAddress address, final Scheme scheme) {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(address, scheme) + "/v1/join"))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Result probed(final InetSocketAddress address, final HttpResponse<String> response, final Scheme scheme) {
        final int code = response.statusCode();
        if (code / 100 == 2) {
            return new Result(scheme, response.body() == null ? "" : response.body());
        }
        if (code == 404) {
            throw refused(address + " does not serve NetherNet (HTTP 404)");
        }
        throw refused(address + " answered HTTP " + code + " to the probe");
    }

    private static String baseUrl(final InetSocketAddress address, final Scheme scheme) {
        // TLS needs the name the certificate is issued for, so the host is kept as given
        final String host = address.getHostString();
        return (scheme == Scheme.HTTPS ? "https://" : "http://") + (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + address.getPort();
    }

    private static CompletionException refused(final String message) {
        return new CompletionException(new ConnectException(message));
    }

    private static Throwable unwrap(final Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /** The JDK client's exceptions often carry no message, so the type has to say what happened. */
    private static String describe(final Throwable cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

}
