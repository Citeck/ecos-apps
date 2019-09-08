package ru.citeck.ecos.apps.app.queue;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.queue.EcosAppQueue;
import ru.citeck.ecos.apps.queue.EcosAppQueues;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class RabbitMqConfiguration {



    /*@Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        return rabbitTemplate;
    }*/

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public List<Queue> initQueues() {



        return EcosAppQueues.ALL
            .stream()
            .map(this::createQueue)
            .collect(Collectors.toList());
    }

    private Queue createQueue(EcosAppQueue queue) {
        return new Queue(queue.getName());
    }
}
