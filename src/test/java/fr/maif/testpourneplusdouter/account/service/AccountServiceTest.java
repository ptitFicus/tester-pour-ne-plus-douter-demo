package fr.maif.testpourneplusdouter.account.service;

import static org.assertj.core.api.AssertionsForClassTypes.anyOf;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import fr.maif.testpourneplusdouter.account.error.Error;
import fr.maif.testpourneplusdouter.account.model.Account;
import fr.maif.testpourneplusdouter.account.model.TransferResult;
import fr.maif.testpourneplusdouter.account.repository.AccountRepository;
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


    @Test
    public void transferShouldWorkProperly() {
        final CustomerService customerService = Mockito.mock(CustomerService.class);
        final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
        String accountId1 = UUID.randomUUID().toString();
        String accountId2 = UUID.randomUUID().toString();

        String customer1 = "customer1";
        String customer2 = "customer2";


        Mockito.when(accountRepository.read(accountId1)).thenAnswer(
                __ -> Either.right(new Account(accountId1, customer1, new BigDecimal("30"), false))
        );
        Mockito.when(accountRepository.read(accountId2)).thenAnswer(
                __ -> Either.right(new Account(accountId2, customer2, new BigDecimal("30"), false))
        );
        Mockito.when(accountRepository.save(new Account(accountId1, customer1, new BigDecimal("20"), false))).thenAnswer(
                p -> Either.right((Account)p.getArguments()[0])
        );
        Mockito.when(accountRepository.save(new Account(accountId2, customer2, new BigDecimal("40"), false))).thenAnswer(
                p -> Either.right((Account)p.getArguments()[0])
        );

        AccountService service = new AccountService(accountRepository, customerService);
        final Either<Error, TransferResult> transferResult = service.transfer(accountId1, accountId2, new BigDecimal("10"));

        assertThat(transferResult.isRight()).isTrue();
        final TransferResult result = transferResult.get();
        assertThat(result.source().balance()).isEqualByComparingTo("20");
        assertThat(result.target().balance()).isEqualByComparingTo("40");
    }

    @Test
    public void transferShouldWorkProperlyFineGrain() {
        final Either<Error, TransferResult> transferResults = AccountService.doTransfer(
                new Account("foo", "customer1", new BigDecimal("10"), false),
                new Account("bar", "customer2", new BigDecimal("10"), false),
                new BigDecimal("5")
        );

        assertThat(transferResults.isRight()).isTrue();
        final TransferResult result = transferResults.get();
        assertThat(result.source().balance()).isEqualByComparingTo("5");
        assertThat(result.target().balance()).isEqualByComparingTo("15");
    }

    @Test
    public void transferShouldFailOnInsufficientBalance() {
        final Either<Error, TransferResult> transferResults = AccountService.doTransfer(
                new Account("foo", "cu1", new BigDecimal("10"), false),
                new Account("bar", "cu2", new BigDecimal("10"), false),
                new BigDecimal("15")
        );

        assertThat(transferResults.isLeft()).isTrue();
        assertThat(transferResults.getLeft()).isEqualTo(Error.INSUFFICIENT_BALANCE);
    }

    @Test
    public void transferShouldFailIfSourceAccountIsClosed() {
        final Either<Error, TransferResult> transferResults = AccountService.doTransfer(
                new Account("foo", "cu1", new BigDecimal("10"), true),
                new Account("bar", "cu2", new BigDecimal("10"), false),
                new BigDecimal("1")
        );

        assertThat(transferResults.isLeft()).isTrue();
        assertThat(transferResults.getLeft()).isEqualTo(Error.ACCOUNT_CLOSED);
    }

    @Test
    public void transferShouldFailIfTargetAccountIsClosed() {
        final Either<Error, TransferResult> transferResults = AccountService.doTransfer(
                new Account("foo", "cu1", new BigDecimal("10"), false),
                new Account("bar", "cu2", new BigDecimal("10"), true),
                new BigDecimal("5")
        );

        assertThat(transferResults.isLeft()).isTrue();
        assertThat(transferResults.getLeft()).isEqualTo(Error.ACCOUNT_CLOSED);
    }
}
