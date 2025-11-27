package com.patorinaldi.wallet.account;

import com.patorinaldi.wallet.account.dto.CreateUserRequest;
import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.Optional;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountServiceIntegrationTest {

    @LocalServerPort
    private int port;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private UserRepository userRepository;

    private RestTestClient restTestClient;

    @BeforeEach
    public void setup() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldCreateUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest(
                "integration@test.com",
                "Integration Test",
                "1234567890"
        );

        // When
        UserResponse response = restTestClient.post()
                .uri("/users")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        // Verify response contains correct data
        assert response != null;
        assert response.email().equals("integration@test.com");
        assert response.fullName().equals("Integration Test");
        assert response.phoneNumber().equals("1234567890");
        assert response.active();
        assert response.id() != null;
        assert response.createdAt() != null;

        // Verify user exists in database
        Optional<User> userInDb = userRepository.findById(response.id());
        assert userInDb.isPresent();
        assert userInDb.get().getEmail().equals("integration@test.com");
    }
}