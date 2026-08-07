package com.app.maria.global.client.mydata;

import com.app.maria.global.config.properties.MydataApiProperties;
import com.app.maria.global.exception.MydataApiException;
import com.app.maria.global.response.ApiResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MydataClientTest {

    @Mock
    RestTemplate restTemplate;

    MydataClient mydataClient;

    @BeforeEach
    void setUp() {
        MydataApiProperties properties = new MydataApiProperties();
        properties.setUrl("https://mydata.test");
        mydataClient = new MydataClient(restTemplate, properties);
    }

    private MydataRiaAccountDTO account(String brokerName, String riaCumulativeSell) {
        return MydataRiaAccountDTO.builder()
                .brokerName(brokerName)
                .riaCumulativeSell(riaCumulativeSell == null ? null : new BigDecimal(riaCumulativeSell))
                .build();
    }

    private void mockExchange(ResponseEntity<ApiResponseDTO<List<MydataRiaAccountDTO>>> toReturn) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(toReturn);
    }

    @Test
    void getExternalSellTotal_계좌하나면_그계좌의_riaCumulativeSell을_반환한다() {
        mockExchange(new ResponseEntity<>(ApiResponseDTO.of("성공", List.of(account("증권사A", "1000000"))), HttpStatus.OK));

        BigDecimal result = mydataClient.getExternalSellTotal("ci-hash-1");

        assertThat(result).isEqualByComparingTo("1000000");
    }

    @Test
    void getExternalSellTotal_계좌가_여러개면_riaCumulativeSell을_전부합산한다() {
        mockExchange(new ResponseEntity<>(
                ApiResponseDTO.of("성공", List.of(account("증권사A", "1000000"), account("증권사B", "2500000"))),
                HttpStatus.OK));

        BigDecimal result = mydataClient.getExternalSellTotal("ci-hash-1");

        assertThat(result).isEqualByComparingTo("3500000");
    }

    @Test
    void getExternalSellTotal_riaCumulativeSell이_null인_계좌는_제외하고_합산한다() {
        mockExchange(new ResponseEntity<>(
                ApiResponseDTO.of("성공", List.of(account("증권사A", "1000000"), account("증권사B", null))),
                HttpStatus.OK));

        BigDecimal result = mydataClient.getExternalSellTotal("ci-hash-1");

        assertThat(result).isEqualByComparingTo("1000000");
    }

    @Test
    void getExternalSellTotal_응답데이터가_빈리스트면_0을반환한다() {
        mockExchange(new ResponseEntity<>(ApiResponseDTO.of("성공", List.<MydataRiaAccountDTO>of()), HttpStatus.OK));

        BigDecimal result = mydataClient.getExternalSellTotal("ci-hash-1");

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getExternalSellTotal_응답바디가_null이면_0을반환한다() {
        mockExchange(new ResponseEntity<>(null, HttpStatus.OK));

        BigDecimal result = mydataClient.getExternalSellTotal("ci-hash-1");

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getExternalSellTotal_404면_장애로간주해_MydataApiException을던진다() {
        HttpClientErrorException notFound =
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(notFound);

        assertThatThrownBy(() -> mydataClient.getExternalSellTotal("ci-hash-1"))
                .isInstanceOf(MydataApiException.class)
                .hasCause(notFound);
    }

    @Test
    void getExternalSellTotal_통신오류면_MydataApiException으로감싸서던진다() {
        RestClientException cause = new RestClientException("connection refused");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> mydataClient.getExternalSellTotal("ci-hash-1"))
                .isInstanceOf(MydataApiException.class)
                .hasCause(cause);
    }

    @Test
    void getExternalSellTotal_요청URL과_바디에_ciHash가들어간다() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(ApiResponseDTO.of("성공", List.<MydataRiaAccountDTO>of()), HttpStatus.OK));

        mydataClient.getExternalSellTotal("ci-hash-xyz");

        assertThat(urlCaptor.getValue()).isEqualTo("https://mydata.test/api/mydata/ria-accounts");
        Map<String, String> body = (Map<String, String>) entityCaptor.getValue().getBody();
        assertThat(body).containsEntry("ciHash", "ci-hash-xyz");
    }
}