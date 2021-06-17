package fr.maif.testpourneplusdouter;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import fr.maif.testpourneplusdouter.account.AccountApplication;
import fr.maif.testpourneplusdouter.account.api.AccountDTO;
import fr.maif.testpourneplusdouter.account.api.TransferResultDTO;
import fr.maif.testpourneplusdouter.account.error.Error;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

@SpringBootTest(classes = AccountApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceTests {

	static EmbeddedPostgres postgres;
	static AtomicBoolean databaseClosed = new AtomicBoolean(false);

	static {
		try {
			postgres = EmbeddedPostgres.builder().start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Autowired
	TestRestTemplate restTemplate;
	static WireMockServer customerServer = new WireMockServer(new WireMockConfiguration().port(8888));


	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("account.db.port", () -> postgres.getPort());
	}

	@BeforeEach
	public void each() {
		customerServer.resetAll();
	}

	@BeforeAll
	public static void init() throws SQLException {
		customerServer.start();
		initDB();
	}

	@AfterEach
	public void tearDown() throws SQLException, IOException {
		if(databaseClosed.get()) {
			postgres = EmbeddedPostgres.builder().start();
			initDB();
		}
		try(final PreparedStatement statement = postgres.getDatabase("accountuser", "account").getConnection().prepareStatement("TRUNCATE account;")) {
			statement.execute();
		}
	}

	@Test
	void withdrawShouldWorkIfBalanceIsHighEnough() {
		String customer = "testcustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> creationResponse = create(customer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> response = withdraw(creationResponse.getBody().id, new BigDecimal("80"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().balance).isEqualByComparingTo("20");
	}

	@Test
	void withdrawShouldNotWorkWithNegativeAmount() {
		String customer = "testcustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> creationResponse = create(customer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> response = withdraw(creationResponse.getBody().id, new BigDecimal("-80"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.NEGATIVE_WITHDRAW.message);
	}

	@Test
	void withdrawShouldNotWorkIfBalanceIsTooLow() {
		String customer = "testcustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> creationResponse = create(customer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> response = withdraw(creationResponse.getBody().id, new BigDecimal("110"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.INSUFFICIENT_BALANCE.message);
	}

	@Test
	void withdrawShouldNotWorkIfAccountDoesNotExists() {
		String customer = "testcustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> response = withdraw(UUID.randomUUID().toString(), new BigDecimal("10"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.ACCOUNT_NOT_FOUND.message);
	}

	@Test
	void createAccountShouldWorkCorrectly() {
		final String customer = "tescustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> response = create(customer, BigDecimal.ZERO);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		final AccountDTO account = response.getBody();
		assertThat(account.customer).isEqualTo(customer);
		assertThat(account.id).isNotBlank();
		assertThat(account.balance).isEqualByComparingTo("0");
		assertThat(account.closed).isFalse();
	}

	@Test
	void createAccountShouldWorkCorrectlyWithCustomAmount() {
		final String customer = "tescustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> response = create(customer, new BigDecimal("100"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		final AccountDTO account = response.getBody();
		assertThat(account.customer).isEqualTo(customer);
		assertThat(account.id).isNotBlank();
		assertThat(account.balance).isEqualByComparingTo("100");
		assertThat(account.closed).isFalse();
	}

	@Test
	void createAccountShouldNotWorkIfCustomerIsBanned() {
		banCustomer("tescustomer");

		final ResponseEntity<AccountDTO> response = create("tescustomer", BigDecimal.ZERO);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.BANNED_CUSTOMER.message);
	}

	@Test
	void createAccountShouldNotWorkIfCustomerDoesNotExists() {
		final ResponseEntity<AccountDTO> response = create("iDoNotExist", BigDecimal.ZERO);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.CUSTOMER_DOES_NOT_EXISTS.message);
	}

	@Test
	void createAccountShouldNotWorkIfCustomerAlreadyHasOne() {
		String customer = "testcustomer";
		allowCustomer(customer);

		create(customer, BigDecimal.ZERO);
		final ResponseEntity<AccountDTO> response = create(customer, BigDecimal.ZERO);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.ACCOUNT_ALREADY_EXISTS.message);
	}

	@Test
	void createAccountShouldNotWorkIfBalanceIsNegative() {
		String customer = "testcustomer";
		allowCustomer(customer);

		create(customer, BigDecimal.ZERO);
		final ResponseEntity<AccountDTO> response = create(customer, new BigDecimal("-100"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.NEGATIVE_BALANCE_AT_ACCOUNT_OPENING.message);
	}

	@Test
	void depositShouldWorkCorrectly() {
		String customer = "testcustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> creationResponse = create(customer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> response = deposit(creationResponse.getBody().id, new BigDecimal("10"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().balance).isEqualByComparingTo("110");
	}

	@Test
	void depositShouldNotWorkWithNegativeAmount() {
		String customer = "testcustomer";
		allowCustomer(customer);

		final ResponseEntity<AccountDTO> creationResponse = create(customer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> response = deposit(creationResponse.getBody().id, new BigDecimal("-10"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error).isEqualTo(Error.NEGATIVE_DEPOSIT.message);
	}

	@Test
	void transferShouldWorkCorrectly() {
		String fromCustomer = "fromCustomer";
		String toCustomer = "toCustomer";
		allowCustomer(fromCustomer);
		allowCustomer(toCustomer);

		final ResponseEntity<AccountDTO> fromCreationResponse = create(fromCustomer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> toCreationResponse = create(toCustomer, new BigDecimal("0"));
		final ResponseEntity<TransferResultDTO> response = transfer(fromCreationResponse.getBody().id, toCreationResponse.getBody().id, new BigDecimal("80"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		final TransferResultDTO result = response.getBody();
		assertThat(result.source.balance).isEqualByComparingTo("20");
		assertThat(result.target.balance).isEqualByComparingTo("80");
	}

	@Test
	void transferShouldFailIfBalanceIsTooLow() {
		String fromCustomer = "fromCustomer";
		String toCustomer = "toCustomer";
		allowCustomer(fromCustomer);
		allowCustomer(toCustomer);

		final ResponseEntity<AccountDTO> fromCreationResponse = create(fromCustomer, new BigDecimal("20"));
		final ResponseEntity<AccountDTO> toCreationResponse = create(toCustomer, new BigDecimal("0"));
		final ResponseEntity<TransferResultDTO> response = transfer(fromCreationResponse.getBody().id, toCreationResponse.getBody().id, new BigDecimal("80"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		final TransferResultDTO result = response.getBody();
		assertThat(result.error).isEqualTo(Error.INSUFFICIENT_BALANCE.message);
	}

	@Test
	void transferShouldFailIfSourceAccountDoesNotExist() {
		String fromCustomer = "fromCustomer";
		String toCustomer = "toCustomer";
		allowCustomer(toCustomer);

		final ResponseEntity<AccountDTO> fromCreationResponse = create(fromCustomer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> toCreationResponse = create(toCustomer, new BigDecimal("0"));
		final ResponseEntity<TransferResultDTO> response = transfer(fromCreationResponse.getBody().id, toCreationResponse.getBody().id, new BigDecimal("80"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		final TransferResultDTO result = response.getBody();
		assertThat(result.error).isEqualTo(Error.ACCOUNT_NOT_FOUND.message);
	}

	@Test
	void transferShouldFailIfTargetAccountDoesNotExist() {
		String fromCustomer = "fromCustomer";
		String toCustomer = "toCustomer";
		allowCustomer(fromCustomer);

		final ResponseEntity<AccountDTO> fromCreationResponse = create(fromCustomer, new BigDecimal("100"));
		final ResponseEntity<AccountDTO> toCreationResponse = create(toCustomer, new BigDecimal("0"));
		final ResponseEntity<TransferResultDTO> response = transfer(fromCreationResponse.getBody().id, toCreationResponse.getBody().id, new BigDecimal("80"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		final TransferResultDTO result = response.getBody();
		assertThat(result.error).isEqualTo(Error.ACCOUNT_NOT_FOUND.message);
	}

	@Test
	void readShouldWorkCorrectly() {
		String fromCustomer = "fromCustomer";
		allowCustomer(fromCustomer);

		String accountId = create(fromCustomer, new BigDecimal("80")).getBody().id;
		final ResponseEntity<AccountDTO> result = read(accountId);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		final AccountDTO account = result.getBody();
		assertThat(account.balance).isEqualByComparingTo("80");
		assertThat(account.customer).isEqualTo(fromCustomer);
		assertThat(account.closed).isFalse();
		assertThat(account.id).isEqualTo(accountId);
	}


	@Test
	@DirtiesContext
	void createShouldReturnAnErrorWhenDatabaseIsDown() throws IOException {
		shutdownDatabase();
		String fromCustomer = "fromCustomer";
		allowCustomer(fromCustomer);

		final ResponseEntity<AccountDTO> response = create(fromCustomer, new BigDecimal("80"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().error).isEqualTo(Error.DB_ERROR.message);
	}

	void shutdownDatabase() {
		databaseClosed.set(true);
		try {
			postgres.close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	ResponseEntity<AccountDTO> create(String customer, BigDecimal balance) {
		String body = """
			{
				"customer": \"""" + customer + "\"," + """
   				"balance": """ + balance + """
			}
		""";

		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-type", Collections.singletonList("application/json"));
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		return restTemplate.exchange("/accounts", HttpMethod.POST, entity, AccountDTO.class);
	}

	ResponseEntity<AccountDTO> withdraw(String accountId, BigDecimal amount) {
		String body = """
			{
				"amount": """ + amount + """
			}
		""";

		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-type", Collections.singletonList("application/json"));
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		return restTemplate.exchange("/accounts/" + accountId + "/_withdraw", HttpMethod.POST, entity, AccountDTO.class);
	}

	ResponseEntity<AccountDTO> deposit(String accountId, BigDecimal amount) {
		String body = """
			{
				"amount": """ + amount + """
			}
		""";

		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-type", Collections.singletonList("application/json"));
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		return restTemplate.exchange("/accounts/" + accountId + "/_deposit", HttpMethod.POST, entity, AccountDTO.class);
	}

	ResponseEntity<TransferResultDTO> transfer(String from, String to, BigDecimal amount) {
		String body = """
			{
				"amount": """ + amount + """
			}
		""";

		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-type", Collections.singletonList("application/json"));
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		return restTemplate.exchange("/accounts/" + from + "/" + to + "/_transfer", HttpMethod.POST, entity, TransferResultDTO.class);
	}

	ResponseEntity<AccountDTO> read(String id) {
		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-type", Collections.singletonList("application/json"));
		HttpEntity<String> entity = new HttpEntity<>(headers);
		return restTemplate.exchange("/accounts/" + id, HttpMethod.GET, entity, AccountDTO.class);
	}

	void allowCustomer(String customer) {
		customerServer.stubFor(WireMock.get("/customers/" + customer)
			.willReturn(ok().withHeader("Content-Type", "application/json")
					.withBody(
							"""
       							{
       								"id": \"""" + customer + "\"," + """
              						"banned": false
       							}
							"""
					)
			));
	}

	void banCustomer(String customer) {
		customerServer.stubFor(WireMock.get("/customers/" + customer)
				.willReturn(ok().withHeader("Content-Type", "application/json")
						.withBody(
								"""
							   {
								   "id": \"""" + customer + "\"," + """
              						"banned": true
       							}
							"""
						)
				));
	}

	static void initDB() throws SQLException {
		try(final PreparedStatement statement = postgres.getPostgresDatabase().getConnection().prepareStatement("""
 			CREATE DATABASE account;
			CREATE USER accountuser WITH PASSWORD 'accountpassword';
 			GRANT ALL PRIVILEGES ON DATABASE "account" to accountuser;
 		""")) {
			statement.execute();
		}

		try(final PreparedStatement preparedStatement = postgres.getDatabase("accountuser", "account").getConnection().prepareStatement("""
            CREATE TABLE IF NOT EXISTS account (
                id varchar(100),
                customer varchar(100),
                balance money,
                closed boolean
            );
            """
		)) {
			preparedStatement.execute();
		}
		databaseClosed.set(false);
	}

}
