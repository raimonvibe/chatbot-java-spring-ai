package com.prayer_chat.chatbot.unit.service;

import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.service.AuditService;
import com.prayer_chat.chatbot.service.FraudDetectionService;
import com.prayer_chat.chatbot.service.SecurityAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService Unit Tests")
class FraudDetectionServiceTest {

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityAlertService securityAlertService;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);
    }

    @Test
    @DisplayName("Should detect too many failed logins")
    void shouldDetectTooManyFailedLogins() {
        // Arrange
        when(auditService.hasTooManyFailedLogins(eq(1L), anyInt(), anyInt()))
            .thenReturn(true);
        when(auditService.hasTooManyPaymentFailures(eq(1L), anyInt(), anyInt()))
            .thenReturn(false);

        // Act
        FraudDetectionService.FraudAnalysisResult result =
            fraudDetectionService.analyzeUser(1L);

        // Assert
        assertThat(result.tooManyFailedLogins).isTrue();
        assertThat(result.riskScore).isGreaterThanOrEqualTo(30);
        assertThat(result.riskLevel).isIn(
            FraudDetectionService.RiskLevel.LOW,
            FraudDetectionService.RiskLevel.MEDIUM
        );
        verify(auditService, times(1)).hasTooManyFailedLogins(eq(1L), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should detect too many payment failures")
    void shouldDetectTooManyPaymentFailures() {
        // Arrange
        when(auditService.hasTooManyFailedLogins(eq(1L), anyInt(), anyInt()))
            .thenReturn(false);
        when(auditService.hasTooManyPaymentFailures(eq(1L), anyInt(), anyInt()))
            .thenReturn(true);

        // Act
        FraudDetectionService.FraudAnalysisResult result =
            fraudDetectionService.analyzeUser(1L);

        // Assert
        assertThat(result.tooManyPaymentFailures).isTrue();
        assertThat(result.riskScore).isGreaterThanOrEqualTo(40);
        assertThat(result.riskLevel).isIn(
            FraudDetectionService.RiskLevel.MEDIUM,
            FraudDetectionService.RiskLevel.HIGH
        );
    }

    @Test
    @DisplayName("Should calculate HIGH risk when both indicators present")
    void shouldCalculateHighRisk_whenBothIndicatorsPresent() {
        // Arrange
        when(auditService.hasTooManyFailedLogins(eq(1L), anyInt(), anyInt()))
            .thenReturn(true);
        when(auditService.hasTooManyPaymentFailures(eq(1L), anyInt(), anyInt()))
            .thenReturn(true);

        // Act
        FraudDetectionService.FraudAnalysisResult result =
            fraudDetectionService.analyzeUser(1L);

        // Assert
        assertThat(result.tooManyFailedLogins).isTrue();
        assertThat(result.tooManyPaymentFailures).isTrue();
        assertThat(result.riskScore).isEqualTo(70); // 30 + 40
        assertThat(result.riskLevel).isEqualTo(FraudDetectionService.RiskLevel.CRITICAL);
        assertThat(result.isSuspicious()).isTrue();
    }

    @Test
    @DisplayName("Should return NONE risk level when no indicators")
    void shouldReturnNoneRiskLevel_whenNoIndicators() {
        // Arrange
        when(auditService.hasTooManyFailedLogins(eq(1L), anyInt(), anyInt()))
            .thenReturn(false);
        when(auditService.hasTooManyPaymentFailures(eq(1L), anyInt(), anyInt()))
            .thenReturn(false);

        // Act
        FraudDetectionService.FraudAnalysisResult result =
            fraudDetectionService.analyzeUser(1L);

        // Assert
        assertThat(result.riskScore).isEqualTo(0);
        assertThat(result.riskLevel).isEqualTo(FraudDetectionService.RiskLevel.NONE);
        assertThat(result.isSuspicious()).isFalse();
    }

    @Test
    @DisplayName("Should detect suspicious payment pattern")
    void shouldDetectSuspiciousPaymentPattern() {
        // Arrange
        when(auditService.hasTooManyPaymentFailures(eq(testUser.getId()), anyInt(), anyInt()))
            .thenReturn(true);

        // Act
        boolean result = fraudDetectionService.isSuspiciousPaymentPattern(testUser);

        // Assert
        assertThat(result).isTrue();
        verify(auditService, times(1)).log(
            any(), any(), anyString(), eq(testUser), anyString(), isNull()
        );
    }
}
