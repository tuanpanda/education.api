package com.education.base.config;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP / giao thức thật của client khi API chạy sau reverse proxy (Caddy -> nginx của UI -> Tomcat).
 * Dựng {@link RemoteIpValve} đúng như Spring Boot cấu hình từ {@code application.yml} rồi chạy request giả qua valve:
 * rate limit và nhật ký hệ thống đọc {@code getRemoteAddr()} nên chỉ đúng khi valve thay IP proxy bằng IP client,
 * và KHÔNG tin {@code X-Forwarded-For} do client gửi thẳng tới API (không qua proxy nội bộ).
 */
class ForwardedHeadersConfigurationTest {

    private static StandardEnvironment environment(Map<String, Object> env) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", env));
        for (String file : new String[] {"application.yml", "application-prod.yml"}) {
            for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                environment.getPropertySources().addAfter("test-env", source);
            }
        }
        return environment;
    }

    private static TomcatServletWebServerFactory customizedFactory(Map<String, Object> env) throws IOException {
        StandardEnvironment environment = environment(env);
        ServerProperties server = Binder.get(environment).bind("server", ServerProperties.class)
                .orElseGet(ServerProperties::new);
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new TomcatWebServerFactoryCustomizer(environment, server).customize(factory);
        return factory;
    }

    private static RemoteIpValve remoteIpValve() throws IOException {
        return customizedFactory(Map.of("JWT_SECRET", "x")).getEngineValves().stream()
                .filter(RemoteIpValve.class::isInstance)
                .map(RemoteIpValve.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("server.forward-headers-strategy=native phải bật RemoteIpValve"));
    }

    /** Kết quả request sau valve: IP, scheme, secure như filter / controller nhìn thấy. */
    private record Seen(String remoteAddr, String scheme, boolean secure) {
    }

    private static Seen pass(RemoteIpValve valve, String peerIp, String xForwardedFor, String xForwardedProto)
            throws Exception {
        AtomicReference<Seen> seen = new AtomicReference<>();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                seen.set(new Seen(request.getRemoteAddr(), request.getScheme(), request.isSecure()));
            }
        });
        Request request = new Request(new Connector());
        request.setCoyoteRequest(new org.apache.coyote.Request());
        request.setRemoteAddr(peerIp);
        request.setRemoteHost(peerIp);
        request.getCoyoteRequest().scheme().setString("http");
        if (xForwardedFor != null) {
            request.getCoyoteRequest().getMimeHeaders().addValue("X-Forwarded-For").setString(xForwardedFor);
        }
        if (xForwardedProto != null) {
            request.getCoyoteRequest().getMimeHeaders().addValue("X-Forwarded-Proto").setString(xForwardedProto);
        }
        valve.invoke(request, null);
        return seen.get();
    }

    @Test
    void nativeStrategy_isOnByDefault_andCanBeTurnedOff() throws IOException {
        assertThat(environment(Map.of()).getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(remoteIpValve()).isNotNull();

        Collection<Valve> valves = customizedFactory(Map.of("JWT_SECRET", "x", "SERVER_FORWARD_HEADERS_STRATEGY", "none"))
                .getEngineValves();
        assertThat(valves).noneMatch(RemoteIpValve.class::isInstance);
    }

    @Test
    void internalProxies_coverDockerNetworks_butNotPublicAddresses() throws IOException {
        RemoteIpValve valve = remoteIpValve();
        String internal = valve.getInternalProxies();
        // Mạng bridge / compose của Docker (172.17-172.31), Docker Desktop (192.168.65.x), 10.x, loopback.
        for (String ip : new String[] {"172.17.0.1", "172.18.0.4", "172.20.0.2", "172.31.255.254", "192.168.65.3",
                "10.0.0.2", "127.0.0.1"}) {
            assertThat(ip.matches(internal)).as("proxy nội bộ %s", ip).isTrue();
        }
        for (String ip : new String[] {"8.8.8.8", "203.0.113.7", "172.32.0.1", "172.15.0.1", "1.2.3.4"}) {
            assertThat(ip.matches(internal)).as("IP public %s", ip).isFalse();
        }
        assertThat(valve.getRemoteIpHeader()).isEqualToIgnoringCase("X-Forwarded-For");
        assertThat(valve.getProtocolHeader()).isEqualToIgnoringCase("X-Forwarded-Proto");
    }

    @Test
    void localDocker_uiNginxForwardsClientIp() throws Exception {
        // Trình duyệt -> nginx của UI (172.18.0.3) -> API: nginx gửi X-Forwarded-For = IP client.
        Seen seen = pass(remoteIpValve(), "172.18.0.3", "203.0.113.7", "http");

        assertThat(seen.remoteAddr()).isEqualTo("203.0.113.7");
        assertThat(seen.secure()).isFalse();
        assertThat(seen.scheme()).isEqualTo("http");
    }

    @Test
    void internetChain_caddyThenNginx_yieldsClientIpAndHttps() throws Exception {
        // Internet -> Caddy (172.18.0.5) -> nginx (172.18.0.3) -> API; nginx giữ X-Forwarded-Proto của Caddy.
        Seen seen = pass(remoteIpValve(), "172.18.0.3", "198.51.100.9, 172.18.0.5", "https");

        assertThat(seen.remoteAddr()).isEqualTo("198.51.100.9");
        assertThat(seen.secure()).isTrue();
        assertThat(seen.scheme()).isEqualTo("https");
    }

    @Test
    void directPublicClient_cannotSpoofIpOrProtocol() throws Exception {
        // Client gọi thẳng API (không qua proxy nội bộ) tự gửi header: bị bỏ qua.
        Seen seen = pass(remoteIpValve(), "203.0.113.50", "1.2.3.4", "https");

        assertThat(seen.remoteAddr()).isEqualTo("203.0.113.50");
        assertThat(seen.secure()).isFalse();
    }
}