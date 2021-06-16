package fr.maif.testpourneplusdouter.account.api;

import fr.maif.testpourneplusdouter.account.error.Error;

public class TransferResultDTO {
    public AccountDTO source;
    public AccountDTO target;
    public String error;

    static TransferResultDTO error(Error error) {
        TransferResultDTO dto = new TransferResultDTO();
        dto.error = error.message;

        return dto;
    }
}
