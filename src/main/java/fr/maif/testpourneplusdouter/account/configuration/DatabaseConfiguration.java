package fr.maif.testpourneplusdouter.account.configuration;

import java.util.Arrays;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfiguration {

    @Bean
    public DataSource dataSource(
            @Value("${account.db.host}") String host,
            @Value("${account.db.database}") String database,
            @Value("${account.db.user}") String user,
            @Value("${account.db.password}") String password,
            @Value("${account.db.port}") int port
    ) {
        final PGSimpleDataSource dataSource = new PGSimpleDataSource();

        dataSource.setServerNames(new String[]{host});
        dataSource.setDatabaseName(database);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setPortNumbers(new int[]{port});

        return dataSource;
    }
}
