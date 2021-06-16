package fr.maif.testpourneplusdouter.account.model;

import java.math.BigDecimal;

import fr.maif.testpourneplusdouter.account.error.Error;
import io.vavr.control.Either;

public record Account (String id, String customer, BigDecimal balance, boolean closed) {
    public Either<Error, Account> deposit(BigDecimal deposit) {
        if(closed) {
            return Either.left(Error.ACCOUNT_CLOSED);
        }

        return Either.right(new Account(id, customer, balance.add(deposit), false));
    }

    public Either<Error, Account> withdraw(BigDecimal withdraw) {
        if(closed) {
            return Either.left(Error.ACCOUNT_CLOSED);
        }

        BigDecimal newAmount = balance.subtract(withdraw);
        if (newAmount.compareTo(BigDecimal.ZERO) > 0) {
            return Either.right(new Account(id, customer, newAmount, false));
        } else {
            return Either.left(Error.INSUFFICIENT_BALANCE);
        }
    }
}
