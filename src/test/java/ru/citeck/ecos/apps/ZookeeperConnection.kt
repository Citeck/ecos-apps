package ru.citeck.ecos.apps

import ecos.org.apache.curator.RetryPolicy
import ecos.org.apache.curator.framework.CuratorFrameworkFactory
import ecos.org.apache.curator.retry.RetryForever
import ecos.org.apache.curator.test.TestingServer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.zookeeper.EcosZooKeeper

@Configuration
class ZookeeperConnection {

    @Bean
    fun ecosZooKeeper(): EcosZooKeeper {

        val zkServer = TestingServer()

        val retryPolicy: RetryPolicy = RetryForever(7_000)

        val client = CuratorFrameworkFactory
            .newClient(zkServer.connectString, retryPolicy)
        client.start()

        return EcosZooKeeper(client).withNamespace("ecos")
    }
}
