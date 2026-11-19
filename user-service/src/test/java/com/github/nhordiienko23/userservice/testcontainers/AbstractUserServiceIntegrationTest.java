package com.github.nhordiienko23.userservice.testcontainers;

import com.github.nhordiienko23.userservice.service.RefreshTokenCleanupJob;
import com.github.nhordiienko23.userservice.service.UserEventOutboxProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.kafka.admin.auto-create=false",
                "spring.main.web-application-type=servlet"
        }
)
@MockitoBean(types = {
        JwtEncoder.class,
        UserEventOutboxProcessor.class,
        RefreshTokenCleanupJob.class
})
public abstract class AbstractUserServiceIntegrationTest
        extends AbstractPostgresIntegrationTest {
}