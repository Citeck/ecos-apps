package ru.citeck.ecos.apps.config;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;

import java.io.File;

public class AppManualDeployTest {

    //@Test
    public void test() {

        /*CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("admin");
        factory.setPassword("admin");
        Connection connection = factory.createConnection();
        Channel channel = connection.createChannel(false);

        EappsFactory appsFactory = new EappsFactory();

        EappsRabbitApi api = new EappsRabbitApi(appsFactory, channel);

        EcosApp app = appsFactory.getEcosAppIO().read(new File("C:/eapps/eapps.zip"));

        api.getAppApi().deployApp("test-source", app);*/
    }
}
