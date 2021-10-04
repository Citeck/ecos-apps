package ru.citeck.ecos.apps;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Slf4j
@Configuration
public class EmbeddedPgDataSourceConfig {

    public final static String DB_NAME = "eapps";
    public final static String USER_NAME = "eapps";

    @Bean(destroyMethod = "close")
    @SneakyThrows
    public HikariDataSource dataSource() {

        EmbeddedPostgres pg = EmbeddedPostgres.start();
        DataSource database = pg.getPostgresDatabase();

        try (Connection conn = database.getConnection()) {

            try (PreparedStatement stmt = conn.prepareStatement(
                "CREATE DATABASE " + DB_NAME + ";" +
                    "CREATE USER " + USER_NAME + " WITH ENCRYPTED PASSWORD '';" +
                    "GRANT ALL ON DATABASE " + DB_NAME + " TO " + USER_NAME + ";"
            )) {
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("SELECT version()")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    log.info("Setup embedded PostgreSQL DB: " + rs.getString(1));
                }
            }
        }

        HikariConfig config = new HikariConfig();
        config.setDataSource(pg.getDatabase(USER_NAME, DB_NAME));
        config.setAutoCommit(false);

        return new HikariDataSource(config);
    }
}
