package ru.citeck.ecos.apps;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.ConnectionFactory;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commands.spring.CommandsConnectionFactoryProvider;

import javax.annotation.PostConstruct;

@Configuration
public class TestConfiguration {

    @Autowired
    private CommandsServiceFactory commandsServiceFactory;

    @PostConstruct
    void init() {
        commandsServiceFactory.getRemoteCommandsService().init();
    }

    @Component
    public static class ComMockConnectionProvider implements CommandsConnectionFactoryProvider {

        private final MockConnectionFactory connectionFactory = new MockConnectionFactory();

        @Nullable
        @Override
        public ConnectionFactory getConnectionFactory() {
            return connectionFactory;
        }
    }
}
