package fr.maif.testpourneplusdouter.account.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import fr.maif.testpourneplusdouter.account.error.Error;
import fr.maif.testpourneplusdouter.account.model.Account;
import fr.maif.testpourneplusdouter.account.repository.AccountRepository;
import fr.maif.testpourneplusdouter.account.service.AccountService;
import fr.maif.testpourneplusdouter.account.service.CustomerService;
import io.vavr.control.Either;

public class AccountServiceTest {
    @Test
    public void withdrawShouldNotWorkIfBalanceIsTooLow() {
        final CustomerService customerService = Mockito.mock(CustomerService.class);
        final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
        String accountId = UUID.randomUUID().toString();

        Mockito.when(accountRepository.read(accountId)).thenAnswer(
                __ -> Either.right(new Account(accountId, "customer", new BigDecimal("10"), false))
        );
        AccountService service = new AccountService(accountRepository, customerService);

        final Either<Error, Account> maybeAccount = service.withdraw(accountId, new BigDecimal("20"));
        assertThat(maybeAccount.isLeft()).isTrue();
        assertThat(maybeAccount.getLeft()).isEqualTo(Error.INSUFFICIENT_BALANCE);
    }

    @Test
    public void withdrawShouldNotWorkIfAccountDoesNotExist() {
        final CustomerService customerService = Mockito.mock(CustomerService.class);
        final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
        String accountId = UUID.randomUUID().toString();

        Mockito.when(accountRepository.read(accountId)).thenAnswer(
                __ -> Either.left(Error.ACCOUNT_NOT_FOUND));

        AccountService service = new AccountService(accountRepository, customerService);

        final Either<Error, Account> maybeAccount = service.withdraw(accountId, new BigDecimal("20"));
        assertThat(maybeAccount.isLeft()).isTrue();
        assertThat(maybeAccount.getLeft()).isEqualTo(Error.ACCOUNT_NOT_FOUND);
    }

    @Test
    public void withdrawShouldWorkIfBalanceIsHighEnough() {
        final CustomerService customerService = Mockito.mock(CustomerService.class);
        final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
        String accountId = UUID.randomUUID().toString();

        Mockito.when(accountRepository.read(accountId)).thenAnswer(
                __ -> Either.right(new Account(accountId, "customer", new BigDecimal("30"), false))
        );
        AccountService service = new AccountService(accountRepository, customerService);

        final Either<Error, Account> maybeAccount = service.withdraw(accountId, new BigDecimal("20"));
        assertThat(maybeAccount.isRight()).isTrue();
        assertThat(maybeAccount.get().balance()).isEqualByComparingTo("10");
    }

    @Test
    public void withdrawShouldNotWorkWithNegativeAmount() {
        final CustomerService customerService = Mockito.mock(CustomerService.class);
        final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
        String accountId = UUID.randomUUID().toString();

        Mockito.when(accountRepository.read(accountId)).thenAnswer(
                __ -> Either.right(new Account(accountId, "customer", new BigDecimal("30"), false))
        );
        AccountService service = new AccountService(accountRepository, customerService);

        final Either<Error, Account> maybeAccount = service.withdraw(accountId, new BigDecimal("-20"));
        assertThat(maybeAccount.isLeft()).isTrue();
        assertThat(maybeAccount.getLeft()).isEqualTo(Error.NEGATIVE_WITHDRAW);
    }
}
