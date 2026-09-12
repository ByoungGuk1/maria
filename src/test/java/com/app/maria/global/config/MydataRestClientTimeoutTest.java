package com.app.maria.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.provider.MydataProviderImpl;
import com.app.maria.global.exception.MydataApiException;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class MydataRestClientTimeoutTest {
    @Test
    void appliesConnectAndReadTimeouts() {
        RestClient client = new RestClientConfig("http://localhost", "http://localhost")
                .mydataRestClient(RestClient.builder());
        Object factory = ReflectionTestUtils.getField(client, "clientRequestFactory");
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(3000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(5000);
    }

    @Test
    void delayedLookupAndSyncBecomeMydataErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch release = new CountDownLatch(1);
        server.setExecutor(executor);
        server.createContext("/api/mydata/ria-accounts", exchange -> {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            RestClient client = new RestClientConfig(url, url).mydataRestClient(RestClient.builder());
            MydataProviderImpl provider = new MydataProviderImpl(client);
            ReflectionTestUtils.setField(provider, "myDataUrl", url);
            ReflectionTestUtils.setField(provider, "ownBrokerName", "test-broker");
            assertThatThrownBy(() -> provider.getExternalConfiguredLimit("test-ci"))
                    .isInstanceOf(MydataApiException.class)
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
            AccountDTO account = AccountDTO.builder().limitAmount(BigDecimal.TEN).build();
            assertThatThrownBy(() -> provider.syncRiaAccount("test-ci", account))
                    .isInstanceOf(MydataApiException.class)
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
