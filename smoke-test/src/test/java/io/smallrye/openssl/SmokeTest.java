package io.smallrye.openssl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SelfSignedCertificate;
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
     * With OpenSSL 3.5+ and TLS 1.3, the default key exchange is x25519mlkem768 (post-quantum).
     * A successful handshake and round-trip request proves the PQC stack works end-to-end.
     */
    @Test
    void postQuantumTlsRoundTrip(Vertx vertx, VertxTestContext ctx) {
        SelfSignedCertificate cert = SelfSignedCertificate.create();

        HttpServerOptions serverOpts = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(cert.keyCertOptions())
                .setOpenSslEngineOptions(new OpenSSLEngineOptions())
                .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"));

        HttpClientOptions clientOpts = new HttpClientOptions()
                .setSsl(true)
                .setTrustOptions(cert.trustOptions())
                .setOpenSslEngineOptions(new OpenSSLEngineOptions())
                .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"));

        vertx.createHttpServer(serverOpts)
                .requestHandler(req -> req.response().end("OK"))
                .listen(0)
                .compose(server -> vertx.createHttpClient(clientOpts)
                        .request(HttpMethod.GET, server.actualPort(), "localhost", "/")
                        .compose(HttpClientRequest::send)
                        .compose(resp -> {
                            ctx.verify(() -> assertEquals(200, resp.statusCode()));
                            return resp.body();
                        })
                        .onSuccess(body -> {
                            ctx.verify(() -> assertEquals("OK", body.toString()));
                            ctx.completeNow();
                        }))
                .onFailure(ctx::failNow);
    }
}
