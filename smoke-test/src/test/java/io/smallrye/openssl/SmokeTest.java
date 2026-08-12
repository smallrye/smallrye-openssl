package io.smallrye.openssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SmokeTest {

    private static final String ARCH = System.getProperty("os.arch").equals("aarch64") ? "aarch_64" : "x86_64";
    private static final String NATIVE_LIB_ENTRY = "META-INF/native/libnetty_tcnative_linux_" + ARCH + ".so";

    @BeforeAll
    static void loadNativeLibrary(@TempDir Path tempDir) throws Exception {
        String jarPath = System.getProperty("native.jar");
        assertNotNull(jarPath, "System property 'native.jar' must point to the built native JAR");

        Path lib = tempDir.resolve("libnetty_tcnative_linux_" + ARCH + ".so");
        try (JarFile jar = new JarFile(jarPath)) {
            JarEntry entry = jar.getJarEntry(NATIVE_LIB_ENTRY);
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, lib);
            }
        }
        // Loading triggers JNI_OnLoad which registers native methods on
        // io.netty.internal.tcnative.* — Vert.x/Netty then sees OpenSSL as available.
        System.load(lib.toAbsolutePath().toString());
    }

    @Test
    void jarContainsNativeLibrary() throws Exception {
        String jarPath = System.getProperty("native.jar");
        try (JarFile jar = new JarFile(jarPath)) {
            assertNotNull(jar.getJarEntry(NATIVE_LIB_ENTRY),
                    NATIVE_LIB_ENTRY + " not found in JAR — artifact may be corrupted or built for the wrong platform");
        }
    }

    /**
     * Starts a Vert.x HTTPS server and client, both using our native OpenSSL library.
     * A successful handshake and round-trip request proves the PQC stack works end-to-end.
     */
    @Test
    void postQuantumTlsRoundTrip(Vertx vertx, VertxTestContext ctx) {

        System.out.println("OpenSSL available: " + OpenSSLEngineOptions.isAvailable());
        System.out.println("OpenSSL PQC available: " + OpenSSLEngineOptions.isPqcAvailable());

        ServerSSLOptions serverOpts = new ServerSSLOptions()
                .setKeyExchangeGroups(List.of("x25519mlkem768"))
                .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"))
                .setKeyCertOptions(new PemKeyCertOptions()
                        .setCertPath("src/main/resources/certs/test-cert.pem")
                        .setKeyPath("src/main/resources/certs/test-key.pem"));

        ClientSSLOptions clientOpts = new ClientSSLOptions()
                .setKeyExchangeGroups(List.of("x25519mlkem768"))
                .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"))
                .setTrustAll(true);

        HttpClient client = vertx.httpClientBuilder()
                .with(clientOpts)
                .with(new OpenSSLEngineOptions())
                .with(new HttpClientConfig().setSsl(true))
                .build();

        vertx.httpServerBuilder()
                .with(new HttpServerConfig())
                .with(new OpenSSLEngineOptions())
                .with(serverOpts)
                .build()
                .requestHandler(req -> req.response().end("OK"))
                .listen(0)
                .compose(server -> {
                    System.out.println("Server port: " + server.actualPort());
                    return client.request(HttpMethod.GET, server.actualPort(), "localhost", "/");
                })
                .compose(HttpClientRequest::send)
                .compose(resp -> {
                    ctx.verify(() -> assertEquals(200, resp.statusCode()));
                    return resp.body();
                })
                .onSuccess(body -> {
                    ctx.verify(() -> assertEquals("OK", body.toString()));
                    ctx.completeNow();
                })
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.failNow(err);
                });
    }
}
