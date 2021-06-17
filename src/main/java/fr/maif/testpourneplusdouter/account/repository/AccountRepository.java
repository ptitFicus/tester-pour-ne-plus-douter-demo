package fr.maif.testpourneplusdouter.account.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import fr.maif.testpourneplusdouter.account.error.Error;
import fr.maif.testpourneplusdouter.account.model.Account;
import io.vavr.control.Either;

@Repository
public class AccountRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountRepository.class);
    private final DataSource dataSource;

    public AccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @PostConstruct
    public void setupDBIfNeeded() throws SQLException {
        try(final PreparedStatement preparedStatement = dataSource.getConnection().prepareStatement("""
            CREATE TABLE IF NOT EXISTS account (
                id varchar(100) PRIMARY KEY,
                customer varchar(100),
                balance money,
                closed boolean
            );
            """
        )) {
            preparedStatement.execute();
        }
    }

    public Either<Error, Account> save(Account account) {
        try {
            return save(account, dataSource.getConnection());
        } catch (SQLException exception) {
            LOGGER.error("SQL error while accessing DB", exception);
            return Either.left(Error.DB_ERROR);
        }
    }

    public Either<Error, Account> save(Account account, Connection connection) {
        try (final PreparedStatement preparedStatement = connection
                .prepareStatement("""
                        INSERT INTO account(id, customer, balance, closed)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE
                            SET customer = EXCLUDED.customer, balance = EXCLUDED.balance, closed = EXCLUDED.closed
                        RETURNING id, customer, balance, closed
                    """)
        ) {


            preparedStatement.setString(1, account.id());
            preparedStatement.setString(2, account.customer());
            preparedStatement.setBigDecimal(3, account.balance());
            preparedStatement.setBoolean(4, account.closed());

            final ResultSet result = preparedStatement.executeQuery();

            return mapDBResult(result);
        } catch (SQLException exception) {
            LOGGER.error("SQL error while accessing DB", exception);
            return Either.left(Error.DB_ERROR);
        }
    }

    public Either<Error, Account> read(String accountId) {
        try {
            return read(accountId, dataSource.getConnection());
        } catch (SQLException exception) {
            LOGGER.error("SQL error while accessing DB", exception);
            return Either.left(Error.DB_ERROR);
        }
    }

    public Either<Error, Account> read(String accountId, Connection connection) {
        try(final PreparedStatement preparedStatement = connection.prepareStatement("""
            SELECT * FROM account WHERE account.id = ?
            """)) {
            preparedStatement.setString(1, accountId);

            final ResultSet resultSet = preparedStatement.executeQuery();

            if(!resultSet.isBeforeFirst()) {
                return Either.left(Error.ACCOUNT_NOT_FOUND);
            }

            return mapDBResult(resultSet);
        } catch (SQLException exception) {
            LOGGER.error("SQL error while accessing DB", exception);
            return Either.left(Error.DB_ERROR);
        }
    }

    public Either<Error, Optional<Account>> searchAccountForCustomer(String customerId) {
        try(final PreparedStatement preparedStatement = dataSource.getConnection().prepareStatement("""
            SELECT * FROM account WHERE account.customer = ?
            """)) {
            preparedStatement.setString(1, customerId);

            final ResultSet resultSet = preparedStatement.executeQuery();

            if(!resultSet.isBeforeFirst()) {
                return Either.right(Optional.empty());
            }

            return mapDBResult(resultSet).map(Optional::ofNullable);
        } catch (SQLException exception) {
            LOGGER.error("SQL error while accessing DB", exception);
            return Either.left(Error.DB_ERROR);
        }
    }

    static Either<Error, Account> mapDBResult(ResultSet resultSet) {
        try {
            resultSet.next();
            final String id = resultSet.getString("id");
            final String customer = resultSet.getString("customer");
            final BigDecimal balance = resultSet.getBigDecimal("balance");
            final boolean closed = resultSet.getBoolean("closed");

            return Either.right(new Account(id, customer, balance, closed));
        } catch (SQLException exception) {
            LOGGER.error("SQL error while accessing DB", exception);
            return Either.left(Error.DB_ERROR);
        }
    }
}
