package ru.citeck.ecos.apps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsServiceFactory;

import javax.annotation.PostConstruct;

@Configuration
public class TestConfiguration {

    @Autowired
    private CommandsServiceFactory commandsServiceFactory;

    @PostConstruct
    void init() {
        commandsServiceFactory.getRemoteCommandsService();
    }
}
