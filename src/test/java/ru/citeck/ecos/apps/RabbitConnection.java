package ru.citeck.ecos.apps;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.ConnectionFactory;
import org.jetbrains.annotations.Nullable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.connection.SimpleConnection;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitConnection implements org.springframework.amqp.rabbit.connection.ConnectionFactory,
    RabbitMqConnProvider {

    private final ConnectionFactory impl = new MockConnectionFactory();

    @Override
    public Connection createConnection() throws AmqpException {
        try {
            return new SimpleConnection(impl.newConnection(), 10);
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public RabbitMqConn getConnection() {
        return new RabbitMqConn(impl);
    }

    @Override
    public String getHost() {
        return impl.getHost();
    }

    @Override
    public int getPort() {
        return impl.getPort();
    }

    @Override
    public String getVirtualHost() {
        return impl.getVirtualHost();
    }

    @Override
    public String getUsername() {
        return impl.getUsername();
    }

    @Override
    public void addConnectionListener(ConnectionListener connectionListener) {
    }

    @Override
    public boolean removeConnectionListener(ConnectionListener connectionListener) {
        return false;
    }

    @Override
    public void clearConnectionListeners() {
    }
}
