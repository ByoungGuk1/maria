package com.app.maria.global.client.mydata;

import com.app.maria.global.config.properties.MydataApiProperties;
import com.app.maria.global.exception.MydataApiException;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Component
@RequiredArgsConstructor
public class MydataClient {

    private final RestTemplate restTemplate;
    private final MydataApiProperties mydataApiProperties;

    public BigDecimal getExternalSellTotal(String ciHash) {
        String requestUrl = mydataApiProperties.getUrl() + "/api/mydata/ria-accounts";

        Map<String, String> body = Map.of("ciHash", ciHash);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<ApiResponseDTO<List<MydataRiaAccountDTO>>> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<ApiResponseDTO<List<MydataRiaAccountDTO>>>() {
                    }
            );
            List<MydataRiaAccountDTO> accounts = response.getBody() != null ? response.getBody().getData() : null;
            return sum(accounts);
        } catch (RestClientException e) {
            throw new MydataApiException("myData 외부 순매수 조회 실패", e);
        }
    }

    private BigDecimal sum(List<MydataRiaAccountDTO> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return accounts.stream()
                .filter(a -> !mydataApiProperties.getOwnBrokerName().equals(a.getBrokerName()))
                .map(MydataRiaAccountDTO::getRiaCumulativeSell)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
