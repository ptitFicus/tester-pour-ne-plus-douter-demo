package fr.maif.testpourneplusdouter.account.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.maif.testpourneplusdouter.account.model.Customer;
import fr.maif.testpourneplusdouter.account.error.Error;
import io.vavr.control.Either;

@Service
public class CustomerService {
    private final HttpClient client = HttpClient.newHttpClient();
    private final String customerApiUrl;
    private final ObjectMapper mapper;

    public CustomerService(@Value("${api.customer.url}") String customerApiUrl, ObjectMapper mapper) {
        this.customerApiUrl = customerApiUrl;
        this.mapper = mapper;
    }

    public CompletableFuture<Either<Error, Customer>> fetchCustomer(String id) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(customerApiUrl + "/customers/" + id))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if(response.statusCode() == 404) {
                        return Either.left(Error.CUSTOMER_DOES_NOT_EXISTS);
                    } else if(response.statusCode() >= 400) {
                        return Either.left(Error.CUSTOMER_FETCH_ERROR);
                    } else {
                        try {
                            return Either.right(mapper.readValue(response.body(), Customer.class));
                        } catch (JsonProcessingException e) {
                            return Either.left(Error.CUSTOMER_FETCH_ERROR);
                        }
                    }
                });
    }
}
