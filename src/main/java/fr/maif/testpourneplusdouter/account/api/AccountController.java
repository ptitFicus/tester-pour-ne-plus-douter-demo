package fr.maif.testpourneplusdouter.account.api;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import fr.maif.testpourneplusdouter.account.error.Error;
import fr.maif.testpourneplusdouter.account.model.Account;
import fr.maif.testpourneplusdouter.account.model.TransferResult;
import fr.maif.testpourneplusdouter.account.service.AccountService;
import io.vavr.control.Either;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    public CompletableFuture<ResponseEntity<AccountDTO>> openAccount(@RequestBody AccountDTO account) {
        return accountService.open(account.customer, account.balance)
            .thenApply(AccountController::toResponse);
    }

    @PostMapping("/accounts/{id}/_withdraw")
    public ResponseEntity<AccountDTO> withdraw(
            @PathVariable("id") String id,
            @RequestBody BalanceModificationRequest request
    ) {
        return toResponse(accountService.withdraw(id, request.amount));
    }

    @PostMapping("/accounts/{id}/_deposit")
    public ResponseEntity<AccountDTO> deposit(
            @PathVariable("id") String id,
            @RequestBody BalanceModificationRequest request
    ) {
        return toResponse(accountService.deposit(id, request.amount));
    }

    @PostMapping("/accounts/{from}/{to}/_transfer")
    public ResponseEntity<TransferResultDTO> deposit(
            @PathVariable("from") String from,
            @PathVariable("to") String to,
            @RequestBody BalanceModificationRequest request
    ) {
        return toTransferResponse(accountService.transfer(from, to, request.amount));
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<AccountDTO> close(
            @PathVariable("id") String id
    ) {
        return toResponse(accountService.close(id));
    }


    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountDTO> read(
            @PathVariable("id") String id
    ) {
        return toResponse(accountService.read(id));
    }


    static ResponseEntity<TransferResultDTO> toTransferResponse(Either<Error, TransferResult> maybeTransferResult) {
        return maybeTransferResult.fold(
                error -> new ResponseEntity<>(TransferResultDTO.error(error), error.status),
                account -> new ResponseEntity<>(toDTO(account), HttpStatus.OK)
        );
    }

    static ResponseEntity<AccountDTO> toResponse(Either<Error, Account> maybeAccount) {
        return maybeAccount.fold(
                error -> new ResponseEntity<>(AccountDTO.error(error), error.status),
                account -> new ResponseEntity<>(toDTO(account), HttpStatus.OK)
        );
    }

    static AccountDTO toDTO(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.balance = account.balance();
        dto.id = account.id();
        dto.customer = account.customer();
        dto.closed = account.closed();

        return dto;
    }


    static TransferResultDTO toDTO(TransferResult result) {
        TransferResultDTO dto = new TransferResultDTO();

        dto.target = toDTO(result.target());
        dto.source = toDTO(result.source());

        return dto;
    }
}
