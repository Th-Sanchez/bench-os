package com.benchos.api.shared.push;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpoPushNotificationSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void parsesExpoTicketsAndExtractsUnregisteredTokens() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "data": [
                    { "status": "ok", "id": "ticket-1" },
                    { "status": "error", "message": "Not registered", "details": { "error": "DeviceNotRegistered" } }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        PushProperties props = new PushProperties("expo", "https://exp.host/--/api/v2/push/send", "expo-secret", false, null, null);
        ExpoPushNotificationSender sender = new ExpoPushNotificationSender(props, objectMapper, httpClient);

        PushDeliveryResult result = sender.sendBatch(List.of(
                PushMessage.of("ExponentPushToken[valid-1]", "Title", "Body", Map.of("route", "/(tabs)/workbench")),
                PushMessage.of("ExponentPushToken[stale-2]", "Title", "Body", Map.of())
        ));

        assertThat(result.sentCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).containsExactly("ExponentPushToken[stale-2]");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().headers().firstValue("Authorization"))
                .contains("Bearer expo-secret");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesExpoHttpErrorGracefullyWithoutThrowing() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("Service Unavailable");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        PushProperties props = new PushProperties("expo", "https://exp.host/--/api/v2/push/send", "", false, null, null);
        ExpoPushNotificationSender sender = new ExpoPushNotificationSender(props, objectMapper, httpClient);

        PushDeliveryResult result = sender.sendBatch(List.of(
                PushMessage.of("ExponentPushToken[abc]", "Title", "Body", Map.of())
        ));

        assertThat(result.sentCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).isEmpty();
    }
}
