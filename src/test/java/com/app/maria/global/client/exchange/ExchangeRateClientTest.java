package com.app.maria.global.client.exchange;

import com.app.maria.global.config.properties.ExchangeApiProperties;
import com.app.maria.global.exception.ExchangeRateNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateClientTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    RestTemplate restTemplate;

    ExchangeRateClient exchangeRateClient;

    @BeforeEach
    void setUp() {
        ExchangeApiProperties properties = new ExchangeApiProperties();
        properties.setUrl("https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON?");
        properties.setApiKey("test-auth-key");
        exchangeRateClient = new ExchangeRateClient(restTemplate, properties);
    }

    private JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private String buildUrl(LocalDate date) {
        return "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON?authkey=test-auth-key&searchdate="
                + date.format(DATE_FORMAT) + "&data=AP01";
    }

    @Test
    void getBaseRate_통화를_찾으면_콤마를_제거하고_BigDecimal로_반환한다() throws Exception {
        JsonNode response = json("""
                [
                  {"cur_unit":"AED","deal_bas_r":"390.33"},
                  {"cur_unit":"USD","deal_bas_r":"1,433.6"}
                ]
                """);
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(response);

        BigDecimal rate = exchangeRateClient.getBaseRate("USD", LocalDate.now());

        assertThat(rate).isEqualByComparingTo("1433.6");
    }

    @Test
    void getBaseRate_요청URL에_authkey_searchdate_data파라미터가_들어간다() throws Exception {
        JsonNode response = json("[{\"cur_unit\":\"USD\",\"deal_bas_r\":\"1,433.6\"}]");
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.getForObject(urlCaptor.capture(), eq(JsonNode.class))).thenReturn(response);

        LocalDate today = LocalDate.now();
        exchangeRateClient.getBaseRate("USD", today);

        assertThat(urlCaptor.getValue())
                .contains("authkey=test-auth-key")
                .contains("searchdate=" + today.format(DATE_FORMAT))
                .contains("data=AP01");
    }

    @Test
    void getBaseRate_응답이_null이면_예외를_던진다() {
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(null);

        assertThatThrownBy(() -> exchangeRateClient.getBaseRate("USD", LocalDate.now()))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }

    @Test
    void getBaseRate_응답이_배열이_아니면_예외를_던진다() throws Exception {
        JsonNode response = json("{\"result\":2}");
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(response);

        assertThatThrownBy(() -> exchangeRateClient.getBaseRate("USD", LocalDate.now()))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }

    @Test
    void getBaseRate_당일에_통화가_없으면_하루전날짜로_재조회한다() throws Exception {
        JsonNode empty = json("[]");
        JsonNode found = json("[{\"cur_unit\":\"USD\",\"deal_bas_r\":\"1,420.5\"}]");
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        when(restTemplate.getForObject(urlCaptor.capture(), eq(JsonNode.class)))
                .thenReturn(empty)
                .thenReturn(found);

        LocalDate today = LocalDate.now();
        BigDecimal rate = exchangeRateClient.getBaseRate("USD", today);

        assertThat(rate).isEqualByComparingTo("1420.5");
        assertThat(urlCaptor.getAllValues())
                .containsExactly(buildUrl(today), buildUrl(today.minusDays(1)));
    }

    @Test
    void getBaseRate_7일_넘게_못찾으면_예외를_던지고_정확히_8번만_조회한다() throws Exception {
        JsonNode empty = json("[]");
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(empty);

        assertThatThrownBy(() -> exchangeRateClient.getBaseRate("USD", LocalDate.now()))
                .isInstanceOf(ExchangeRateNotFoundException.class);

        verify(restTemplate, times(8)).getForObject(anyString(), eq(JsonNode.class));
    }

    @Test
    void getBaseRate_날짜없는_오버로드는_오늘날짜로_조회한다() throws Exception {
        JsonNode response = json("[{\"cur_unit\":\"USD\",\"deal_bas_r\":\"1,433.6\"}]");
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.getForObject(urlCaptor.capture(), eq(JsonNode.class))).thenReturn(response);

        exchangeRateClient.getBaseRate("USD");

        assertThat(urlCaptor.getValue()).contains("searchdate=" + LocalDate.now().format(DATE_FORMAT));
    }
}
