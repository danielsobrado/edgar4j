package org.jds.edgar4j.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.runtime.Network;
import de.flapdoodle.reverse.Transition;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import org.jds.edgar4j.repository.MasterIndexEntryRepository;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

@Configuration
@Profile("test")
public class EmbeddedMongoConfig {

    @Bean(destroyMethod = "close")
    public TransitionWalker.ReachedState<RunningMongodProcess> embeddedMongod() throws IOException {
        int port = Network.getFreeServerPort();
        Net net = Net.of("localhost", port, Network.localhostIsIPv6());
        Transition<Net> netTransition = Start.to(Net.class).initializedWith(net);
        Mongod mongod = Mongod.builder().net(netTransition).build();
        return mongod.start(Version.V7_0_7);
    }

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(TransitionWalker.ReachedState<RunningMongodProcess> embeddedMongod) {
        String host = embeddedMongod.current().getServerAddress().getHost();
        int port = embeddedMongod.current().getServerAddress().getPort();
        return MongoClients.create(String.format("mongodb://%s:%d", host, port));
    }

    @Bean
    @ConditionalOnMissingBean(MasterIndexEntryRepository.class)
    public MasterIndexEntryRepository masterIndexEntryRepository() {
        return Mockito.mock(MasterIndexEntryRepository.class);
    }
}
