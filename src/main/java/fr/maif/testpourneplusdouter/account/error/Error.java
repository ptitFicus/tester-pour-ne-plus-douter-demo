package fr.maif.testpourneplusdouter.account.error;

import org.springframework.http.HttpStatus;

public enum Error {

    INSUFFICIENT_BALANCE("Solde insuffisant", HttpStatus.BAD_REQUEST),
    ACCOUNT_CLOSED("Le compte est cloturé", HttpStatus.BAD_REQUEST),
    DB_ERROR("Erreur lors de l'accès à la base", HttpStatus.INTERNAL_SERVER_ERROR),
    BALANCE_NOT_NULL("Le solde du compte doit être à 0 pour pouvoir le cloturer", HttpStatus.BAD_REQUEST),
    BANNED_CUSTOMER("Ce client est interdit banquaire", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND("Ce compte n'existe pas", HttpStatus.BAD_REQUEST),
    CUSTOMER_FETCH_ERROR("Echec lors de la récupération du client", HttpStatus.INTERNAL_SERVER_ERROR),
    CUSTOMER_DOES_NOT_EXISTS("Le client n'existe pas", HttpStatus.BAD_REQUEST),
    ACCOUNT_ALREADY_EXISTS("Un seul compte autorisé par client", HttpStatus.BAD_REQUEST),
    NEGATIVE_BALANCE_AT_ACCOUNT_OPENING("Impossible d'ouvrir un compte avec un solde négatif", HttpStatus.BAD_REQUEST),
    NEGATIVE_WITHDRAW("Impossible de retirer un montant négatif", HttpStatus.BAD_REQUEST),
    NEGATIVE_DEPOSIT("Impossible de déposer un montant négatif", HttpStatus.BAD_REQUEST),
    NEGATIVE_TRANSFER("Impossible de transférer un montant négatif", HttpStatus.BAD_REQUEST);;

    public final String message;
    public final HttpStatus status;

    Error(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
