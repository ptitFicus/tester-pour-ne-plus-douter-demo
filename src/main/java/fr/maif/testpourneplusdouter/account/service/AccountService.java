package fr.maif.testpourneplusdouter.account.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import fr.maif.testpourneplusdouter.account.model.Account;
import fr.maif.testpourneplusdouter.account.repository.AccountRepository;
import fr.maif.testpourneplusdouter.account.error.Error;
import fr.maif.testpourneplusdouter.account.model.TransferResult;
import io.vavr.control.Either;

@Service
public class AccountService {
    private final AccountRepository repository;
    private final CustomerService customerService;

    public AccountService(AccountRepository repository, CustomerService customerService) {
        this.repository = repository;
        this.customerService = customerService;
    }

    public Either<Error, Account> withdraw(String accountId, BigDecimal amount) {
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Either.left(Error.NEGATIVE_WITHDRAW);
        }
        return repository.read(accountId)
            .flatMap(account -> account.withdraw(amount));
    }

    public Either<Error, Account> deposit(String accountId, BigDecimal amount) {
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Either.left(Error.NEGATIVE_DEPOSIT);
        }
        return repository.read(accountId)
                .flatMap(account -> account.deposit(amount));
    }

    public Either<Error, TransferResult> transfer(String from, String to, BigDecimal amount) {
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Either.left(Error.NEGATIVE_TRANSFER);
        }
        return repository.read(from).flatMap(sourceAccount ->
            repository.read(to).flatMap(targetAccount -> doTransfer(sourceAccount, targetAccount, amount))
                    .flatMap(result ->
                        repository.save(result.source())
                                .flatMap(updatedSource -> repository.save(result.target())
                                        .map(updatedTarget -> new TransferResult(updatedSource, updatedTarget))
                                )
                    )
        );
    }

    static Either<Error, TransferResult> doTransfer(Account source, Account target, BigDecimal amount) {
        return source.withdraw(amount).flatMap(newSource ->
                target.deposit(amount).map(newTarget -> new TransferResult(newSource, newTarget))
        );
    }

    public CompletableFuture<Either<Error, Account>> open(String customerId, BigDecimal initialBalance) {
        if(initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            return CompletableFuture.completedFuture(Either.left(Error.NEGATIVE_BALANCE_AT_ACCOUNT_OPENING));
        }
        return customerService.fetchCustomer(customerId)
            .thenApply(eitherCustomer -> eitherCustomer.flatMap(
                    customer -> {
                        if(customer.banned()) {
                            return Either.left(Error.BANNED_CUSTOMER);
                        }

                        return repository.searchAccountForCustomer(customerId)
                                .flatMap(maybeAccout -> {
                                    if(maybeAccout.isPresent()) {
                                        return Either.left(Error.ACCOUNT_ALREADY_EXISTS);
                                    }
                                    return Either.right(null);
                                })
                        .flatMap(useless -> repository.save(
                                    new Account(UUID.randomUUID().toString(), customerId, initialBalance, false)
                            )
                        );
                    }
            ));
    }

    public CompletableFuture<Either<Error, Account>> open(String customerId) {
        return open(customerId, BigDecimal.ZERO);
    }

    public Either<Error, Account> close(String accountId) {
        return repository.read(accountId)
            .flatMap(account -> {
                if(account.balance().compareTo(BigDecimal.ZERO) != 0) {
                    return Either.left(Error.BALANCE_NOT_NULL);
                } else {
                    return repository.save(new Account(account.id(), account.customer(), account.balance(), true));
                }
            });
    }


    public Either<Error, Account> read(String accountId) {
        return repository.read(accountId);
    }
}
