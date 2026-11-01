package com.yamltestkit.infrastructure.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.RequestMatcher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One WireMock server per service declared in a {@code _context.yaml}, on a dynamic port bound to {@code localhost}
 * only, with response templating
 * available to stubs that ask for it. Resolves the {@code ${wiremock.<service>.url|host|port}} placeholders of
 * context properties, registers {@code http} stubs and resets stubs and journals between cases.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireMockServices implements AutoCloseable {

    private static final String HOST = "localhost";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{wiremock\\.([^}]*)}");
    private static final Pattern PLACEHOLDER_KEY = Pattern.compile("^(.+)\\.(url|host|port)$");

    private final String label;
    private final Map<String, WireMockServer> servers;
    private final AtomicInteger scenarios = new AtomicInteger();

    /**
     * Starts a server for each service.
     *
     * @param label the declaring {@code _context.yaml}, named in errors
     */
    public static WireMockServices start(Iterable<String> services, String label) {
        Map<String, WireMockServer> servers = new LinkedHashMap<>();
        WireMockServices started = new WireMockServices(label, Collections.unmodifiableMap(servers));
        try {
            for (String service : services) {
                WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort()
                        .bindAddress(HOST));
                servers.put(service, server);
                server.start();
            }
        } catch (RuntimeException e) {
            started.close();
            throw e;
        }
        return started;
    }

    /**
     * Replaces each {@code ${wiremock.<service>.url|host|port}} in a property value; other placeholders stay for
     * Spring to resolve.
     *
     * @throws CaseDefinitionException for a placeholder naming an undeclared service or an unknown part
     */
    public String resolve(String value, String where) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(placeholder(matcher.group(1), where)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /**
     * @param where the YAML location of the stub, named in errors
     * @throws CaseDefinitionException when the stub's service is not declared
     */
    public void register(HttpStub stub, String where) {
        WireMockServer server = server(stub.service(), where);
        WireMockStubMapper.mappings(stub, "yamlit-" + scenarios.incrementAndGet()).forEach(server::stubFor);
    }

    /**
     * The service's recorded requests that match, in the order received.
     *
     * @throws CaseDefinitionException when the service is not declared
     */
    public List<LoggedRequest> find(String service, RequestMatcher matcher, String where) {
        return server(service, where).findAll(WireMockStubMapper.requestPattern(matcher));
    }

    /**
     * Every request the service recorded, in the order received.
     *
     * @throws CaseDefinitionException when the service is not declared
     */
    public List<LoggedRequest> received(String service, String where) {
        return server(service, where).findAll(RequestPatternBuilder.allRequests());
    }

    /** Removes every stub, scenario state and recorded request. */
    public void resetAll() {
        servers.values().forEach(WireMockServer::resetAll);
    }

    /** Removes recorded requests, keeping stubs. */
    public void resetRequests() {
        servers.values().forEach(WireMockServer::resetRequests);
    }

    /**
     * @throws CaseDefinitionException naming the declared services when {@code service} is not one of them
     */
    public WireMockServer server(String service, String where) {
        WireMockServer server = servers.get(service);
        if (server == null) {
            throw new CaseDefinitionException(where + ": unknown service '" + service + "'; " + label + " declares "
                    + servers.keySet());
        }
        return server;
    }

    @Override
    public void close() {
        servers.values().stream().filter(WireMockServer::isRunning).forEach(WireMockServer::stop);
    }

    private String placeholder(String key, String where) {
        Matcher matcher = PLACEHOLDER_KEY.matcher(key);
        if (!matcher.matches()) {
            throw new CaseDefinitionException(where + ": invalid placeholder ${wiremock." + key
                    + "}; use ${wiremock.<service>.url}, .host or .port");
        }
        int port = server(matcher.group(1), where).port();
        return switch (matcher.group(2)) {
            case "url" -> "http://" + HOST + ":" + port;
            case "host" -> HOST;
            default -> String.valueOf(port);
        };
    }
}
