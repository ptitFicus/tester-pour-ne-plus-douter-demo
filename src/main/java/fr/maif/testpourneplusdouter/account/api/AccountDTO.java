package fr.maif.testpourneplusdouter.account.api;

import java.math.BigDecimal;

import fr.maif.testpourneplusdouter.account.error.Error;

public class AccountDTO {
    public String id;
    public String customer;
    public BigDecimal balance;
    public boolean closed;
    public String error;

    static AccountDTO error(Error error) {
        AccountDTO dto = new AccountDTO();
        dto.error = error.message;

        return dto;
    }
}
