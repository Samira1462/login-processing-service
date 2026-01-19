package com.codechallenge.loginprocessingservice;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

class ContainersSmokeTest extends AbstractTest {

    @Test
    void shouldHaveKafkaPostgresAndWireMockContainersRunning() throws Exception {

        assertTrue(kafkaContainer.isRunning(), "Kafka container is not running");
        assertTrue(postgresContainer.isRunning(), "Postgres container is not running");
        assertTrue(wireMockContainer.isRunning(), "WireMock container is not running");

        System.out.println("Kafka bootstrap: " + kafkaContainer.getBootstrapServers());
        System.out.println("Postgres JDBC:  " + postgresContainer.getJdbcUrl());
        System.out.println("WireMock base:  " + wireMockContainer.getBaseUrl());

        try (Connection con = DriverManager.getConnection(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword()
        )) {
            assertNotNull(con);

            try (ResultSet rs = con.createStatement().executeQuery("select 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        assertNotNull(wireMockContainer.getBaseUrl());
        assertFalse(wireMockContainer.getBaseUrl().isBlank());
    }
}
