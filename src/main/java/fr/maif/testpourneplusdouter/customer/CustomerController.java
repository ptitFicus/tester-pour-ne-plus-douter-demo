package fr.maif.testpourneplusdouter.customer;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import fr.maif.testpourneplusdouter.account.model.Customer;

@RestController
public class CustomerController {

    public static final Set<String> UNBANNED_CUSTOMERS = Set.of("bcavy", "sdaviet");
    public static final Set<String> BANNED_CUSTOMERS = Set.of("cdirand", "fvenere");

    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> readCustomer(@PathVariable("id") String id) {
        if(UNBANNED_CUSTOMERS.contains(id.toLowerCase())) {
            return ResponseEntity.ok(new Customer(id, false));
        } else if(BANNED_CUSTOMERS.contains(id.toLowerCase())) {
            return ResponseEntity.ok(new Customer(id, true));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
