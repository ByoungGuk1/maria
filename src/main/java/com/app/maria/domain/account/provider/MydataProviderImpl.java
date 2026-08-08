package com.app.maria.domain.account.provider;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.response.MydataRiaAccountsResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MydataProviderImpl implements MydataProvider {

  private final RestClient restClient;

  @Value("${custom.mydata.url}")
  private String myDataUrl;
  @Value("${custom.mydata.own-broker-name}")
  private String ownBrokerName;

  @Override
  public MydataRiaAccountsResponseDTO getRiaAccounts(String ciHash) {
    log.info("ciHash: {}", ciHash);
    Map<String, String> req = new HashMap<>();
    req.put("ciHash", ciHash);
    return restClient.post().uri(myDataUrl + "/api/mydata/ria-accounts")
        .contentType(MediaType.APPLICATION_JSON).body(req).retrieve()
        .body(MydataRiaAccountsResponseDTO.class);
  }

  @Override
  public HttpStatusCode saveRiaAccounts(String ciHash, AccountDTO account) {
    log.info("ciHash: {}", ciHash);
    Map<String, String> req = new HashMap<>();
    req.put("ciHash", ciHash);
    req.put("brokerName", ownBrokerName);
    req.put("riaLimit", String.valueOf(account.getLimitAmount()));
    req.put("riaCumlativeSell", String.valueOf(0));

    ResponseEntity<?> resEntity = restClient.post().uri(myDataUrl + "/api/mydata/ria-accounts/save")
        .contentType(MediaType.APPLICATION_JSON).body(req).retrieve()
        .toEntity(Object.class);

    return resEntity.getStatusCode();
  }
}
