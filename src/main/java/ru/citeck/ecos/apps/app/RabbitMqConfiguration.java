package ru.citeck.ecos.apps.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.module.type.ModuleTypesRegistry;
import ru.citeck.ecos.apps.queue.EcosAppQueue;
import ru.citeck.ecos.apps.queue.EcosAppQueues;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    public Queue deployStatusQueue() {
        return createQueue(EcosAppQueues.PUBLISH_STATUS).getAmpqQueue();
    }

    @Bean
    public Queue uploadAppQueue() {
        return createQueue(EcosAppQueues.ECOS_APPS_UPLOAD).getAmpqQueue();
    }

    @Bean
    public List<EcosAppQueue> ecosAppsQueues(ModuleTypesRegistry registry) {
        return registry.getAll()
            .stream()
            .map(t -> EcosAppQueues.getQueueForType(t.getId()))
            .collect(Collectors.toList());
    }

    @Bean
    public List<Queue> initQueues(List<QueueInfo> queues) {
        return queues.stream()
            .map(QueueInfo::getAmpqQueue)
            .collect(Collectors.toList());
    }

    @Bean
    public List<QueueInfo> initQueuesInfo(List<EcosAppQueue> queues) {
        return queues.stream()
            .map(this::createQueue)
            .collect(Collectors.toList());
    }

    @Bean
    public DirectExchange moduleTypePublishExchange() {
        return new DirectExchange(EcosAppQueues.MODULES_EXCHANGE_ID);
    }

    @Bean
    public List<Binding> ecosAppsQueueBindings(DirectExchange exchange, List<QueueInfo> queues) {
        return queues.stream().map(q ->
            BindingBuilder.bind(q.getAmpqQueue())
                .to(exchange)
                .with(q.getQueue().getType())
        ).collect(Collectors.toList());
    }

    private QueueInfo createQueue(EcosAppQueue queue) {
        return new QueueInfo(new Queue(queue.getName()), queue);
    }

    @Data
    @AllArgsConstructor
    private static class QueueInfo {
        private Queue ampqQueue;
        private EcosAppQueue queue;
    }
}
