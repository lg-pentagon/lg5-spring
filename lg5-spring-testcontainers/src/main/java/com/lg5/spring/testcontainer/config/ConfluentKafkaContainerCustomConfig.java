package com.lg5.spring.testcontainer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static com.lg5.spring.testcontainer.util.Constant.CONFLUENTINC_CP_KAFKA_7_8_1;
import static com.lg5.spring.testcontainer.util.Constant.CONFLUENTINC_CP_SCHEMA_REGISTRY_7_8_1;
import static com.lg5.spring.testcontainer.util.Constant.KAFKA_NETWORK_ALIAS;
import static com.lg5.spring.testcontainer.util.Constant.SCHEMA_REGISTRY_NETWORK_ALIAS;
import static com.lg5.spring.testcontainer.util.Constant.network;

@TestConfiguration
@ConditionalOnProperty(name = "testcontainers.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ConfluentKafkaContainerCustomConfig extends BaseContainerCustomConfig implements MultiContainerConfig {

    public static final String BOOTSTRAP_SERVERS_CUSTOM = "BOOTSTRAP_SERVERS_CUSTOM";
    public static final String SCHEMA_REGISTRY_CUSTOM = "SCHEMA_REGISTRY_CUSTOM";
    public static final int KAFKA_INTERNAL_PORT_AS_9093 = 9093;
    public static final int KAFKA_INTERNAL_PORT_AS_9092 = 9092;

    @Value("${docker.container.reuse:false}")
    protected boolean dockerContainerReuse;

    @Bean
    @Order(1)
    public ConfluentKafkaContainer kafkaContainer(Environment environment) {

        final ConfluentKafkaContainer kafkaContainer = new ConfluentKafkaContainer(
                DockerImageName.parse(CONFLUENTINC_CP_KAFKA_7_8_1))
                .withExposedPorts(KAFKA_INTERNAL_PORT_AS_9092, KAFKA_INTERNAL_PORT_AS_9093)
                .withNetwork(network)
                .withNetworkAliases(KAFKA_NETWORK_ALIAS)
                .withEnv("BOOTSTRAP_SERVERS_CUSTOM", KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_AS_9092)
                .waitingFor(Wait.forListeningPort())
                .withReuse(dockerContainerReuse);
        kafkaContainer.start();

        final String kafkaBootstrapServers = kafkaContainer.getBootstrapServers();
        withBootstrapServersCustom(kafkaContainer);

        if (environment instanceof StandardEnvironment) {
            final MutablePropertySources propertySources = ((StandardEnvironment) environment).getPropertySources();
            final Map<String, Object> map = new HashMap<>();
            map.put("kafka-config.bootstrap-servers", kafkaBootstrapServers);
            propertySources.addFirst(new MapPropertySource("kafkaProperties", map));
        }

        return kafkaContainer;
    }

    @Bean
    @Order(2)
    @DependsOn({"kafkaContainer"})
    public GenericContainer<?> schemaRegistryContainer(Environment environment) {
        final GenericContainer<?> schemaRegistryContainer = new GenericContainer<>(
                DockerImageName.parse(CONFLUENTINC_CP_SCHEMA_REGISTRY_7_8_1))
                .withExposedPorts(8081)
                .withNetwork(network)
                .withNetworkAliases(SCHEMA_REGISTRY_NETWORK_ALIAS)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                        "PLAINTEXT://" + KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_AS_9092)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
                .waitingFor(Wait.forListeningPort())
                .withReuse(dockerContainerReuse);
        schemaRegistryContainer.start();

        final String schemaRegistryUrl = "http://" + schemaRegistryContainer.getHost() + ":"
                + schemaRegistryContainer.getMappedPort(8081);

        withSchemaRegistryCustom(schemaRegistryContainer);

        if (environment instanceof StandardEnvironment) {
            final MutablePropertySources propertySources = ((StandardEnvironment) environment).getPropertySources();
            final Map<String, Object> map = new HashMap<>();
            map.put("kafka-config.schema-registry-url", schemaRegistryUrl);
            propertySources.addFirst(new MapPropertySource("schemaRegistryProperties", map));
        }
        return schemaRegistryContainer;
    }


    public Map<String, String> initializeEnvVariables(GenericContainer<?> container1, GenericContainer<?> container2) {
        return ConfluentKafkaContainerCustomConfig.initManualConnectionPropertiesMap((ConfluentKafkaContainer) container1, container2);
    }

    @Override
    public Map<String, String> initializeEnvVariables(GenericContainer<?> container) {
        return Map.of();
    }

    public static Map<String, String> initManualConnectionPropertiesMap(ConfluentKafkaContainer kafkaContainer,
                                                                        GenericContainer<?> schemaRegistryContainer) {
        return Map.of(

                "KAFKA-CONFIG_BOOTSTRAP-SERVERS", kafkaContainer.getEnvMap().get(BOOTSTRAP_SERVERS_CUSTOM),
                "KAFKA-CONFIG_SCHEMA-REGISTRY-URL", schemaRegistryContainer.getEnvMap().get(SCHEMA_REGISTRY_CUSTOM)
        );
    }

    private static void withBootstrapServersCustom(ConfluentKafkaContainer kafkaContainer) {
        final String kafkaUrl = KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_AS_9092;
        kafkaContainer.withEnv(BOOTSTRAP_SERVERS_CUSTOM, kafkaUrl);
    }

    private static void withSchemaRegistryCustom(GenericContainer<?> schemaRegistryContainer) {
        final String kafkaUrl = String.format("http://%s:%s", schemaRegistryContainer.getNetworkAliases().getLast(),
                schemaRegistryContainer.getExposedPorts().getFirst());
        schemaRegistryContainer.withEnv(SCHEMA_REGISTRY_CUSTOM, kafkaUrl);
    }
}
