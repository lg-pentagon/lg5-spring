package com.lg5.spring.testcontainer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.lg5.spring.testcontainer.util.Constant.WIREMOCK_GUI_V_3_10_9;
import static com.lg5.spring.testcontainer.util.Constant.WIREMOCK_NETWORK_ALIAS;
import static com.lg5.spring.testcontainer.util.Constant.network;
import static java.lang.Integer.parseInt;


@TestConfiguration
@ConditionalOnProperty(name = "testcontainers.wiremockui.enabled", havingValue = "true", matchIfMissing = true)
public abstract class WiremockUIContainerCustomConfig extends BaseContainerCustomConfig {

    @Value("${wiremock.config.folder:wiremock/placeholder/template.json}")
    protected String wireMockConfigFolderResource;

    @Value("${wiremock.config.url:third.jsonplaceholder.url}")
    protected String wireMockConfigUrl;

    @Value("${wiremock.config.port:7070}")
    protected String wireMockPortBind;

    @Bean
    @Order(4)
    public WireMockContainer wireMockContainer(Environment environment) {
        final WireMockContainer wireMockContainer = new WireMockContainer(WIREMOCK_GUI_V_3_10_9)
                .withExposedPorts(8080)
                .withMappingFromResource("placeholder", wireMockConfigFolderResource)
                .withNetwork(network)
                .withNetworkAliases(WIREMOCK_NETWORK_ALIAS)
                .withReuse(dockerContainerReuse);
        wireMockContainer.setPortBindings(List.of(wireMockPortBind + ":8080"));

        wireMockContainer.start();

        final String wireMockContainerBaseUrl = wireMockContainer.getBaseUrl();
        configureFor("localhost", parseInt(wireMockPortBind));

        if (environment instanceof StandardEnvironment) {
            MutablePropertySources propertySources = ((StandardEnvironment) environment).getPropertySources();
            Map<String, Object> map = new HashMap<>();
            map.put(wireMockConfigUrl, wireMockContainerBaseUrl);
            propertySources.addFirst(new MapPropertySource("wiremockProperties", map));
        }
        return wireMockContainer;
    }
}
