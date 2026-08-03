package com.app.maria.global.client.exchange;

import com.app.maria.global.config.properties.ExchangeApiProperties;
import com.app.maria.global.exception.ExchangeRateNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
// 기준환율찾기
public class ExchangeRateClient {

    private final RestTemplate restTemplate;
    private final ExchangeApiProperties exchangeApiProperties;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // LocalDate -> SYSTEM_CLOCK 만들면 그것으로 교체
    // currencyUnit = 화폐 단위
    // 날짜를 안넘겨도 되는 편의용 진입점
    public BigDecimal getBaseRate(String currencyUnit) {
        return getBaseRate(currencyUnit, LocalDate.now());
    }

    public BigDecimal getBaseRate(String currencyUnit, LocalDate searchDate) {
        String url = exchangeApiProperties.getUrl()
                + "authkey=" + exchangeApiProperties.getApiKey()
                + "&searchdate=" + searchDate.format(DATE_FORMAT)
                + "&data=AP01";

        JsonNode response = restTemplate.getForObject(url, JsonNode.class);

        if (response == null || !response.isArray()) {
            throw new ExchangeRateNotFoundException("환율 API 응답 오류");
        }

        // 찾고있는 통화와 일치하는 항목 찾기
        for (JsonNode node: response) {
            if (currencyUnit.equals(node.path("cur_unit").asText())) {
                // 매매기준율 deal base rate
                String rate = node.path("deal_bas_r").asText().replace(",", "");
                return new BigDecimal(rate);
            }
        }

        // 고시환율 없었던 주말/공휴일 -> 하루 전 날짜 호출해서 최근 영업일 찾음
        // 무한 재귀 방지용
        if (searchDate.isAfter(LocalDate.now().minusDays(7))) {
            return getBaseRate(currencyUnit, searchDate.minusDays(1));
        }

        throw new ExchangeRateNotFoundException("해당 통화의 환율 정보를 찾을 수 없습니다: " + currencyUnit);

    }

}
