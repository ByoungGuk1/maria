package com.app.maria.performance.domain.account;

import com.app.maria.domain.account.infra.RedisMydataSyncTaskRepository;
import com.app.maria.domain.account.infra.RedisMydataSyncTaskRepository.ClaimedTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("redis-integration")
@EnabledIfSystemProperty(named = "account.redis.enabled", matches = "true")
class RedisMydataSyncTaskRepositoryIntegrationTest {
  private static final String SCHEDULE_KEY = "mydata:ria:retry:schedule";
  private static final String TASK_KEY_PREFIX = "mydata:ria:retry:task:";
  private static final String LOCK_KEY_PREFIX = "mydata:ria:retry:lock:";

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private RedisMydataSyncTaskRepository repository;
  private long accountId;

  @BeforeEach
  void setUp() {
    accountId = Math.abs(UUID.randomUUID().getMostSignificantBits());
    connectionFactory = new LettuceConnectionFactory(required("account.redis.host"),
        Integer.parseInt(required("account.redis.port")));
    String password = System.getProperty("account.redis.password");
    if (password != null && !password.isBlank()) {
      connectionFactory.setPassword(password);
    }
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    repository = new RedisMydataSyncTaskRepository(redisTemplate);
  }

  @AfterEach
  void tearDown() {
    redisTemplate.delete(taskKey());
    redisTemplate.delete(lockKey());
    redisTemplate.opsForZSet().remove(SCHEDULE_KEY, Long.toString(accountId));
    connectionFactory.destroy();
  }

  @Test
  void previousWorkerCannotReleaseNewOwnersLock() {
    repository.enqueue(accountId, "CREATE");
    ClaimedTask workerA = repository.claimDueTasks(1).get(0);

    redisTemplate.delete(lockKey());
    redisTemplate.opsForValue().set(lockKey(), "worker-b-token");

    repository.release(workerA);

    assertThat(redisTemplate.opsForValue().get(lockKey())).isEqualTo("worker-b-token");
  }

  @Test
  void previousWorkerCannotCompleteNewerTask() {
    repository.enqueue(accountId, "CREATE");
    ClaimedTask workerA = repository.claimDueTasks(1).get(0);
    repository.enqueue(accountId, "UPDATE_LIMIT");

    repository.complete(workerA);

    assertThat(redisTemplate.opsForValue().get(taskKey())).isNotEqualTo(workerA.value());
    assertThat(redisTemplate.opsForZSet().score(SCHEDULE_KEY, Long.toString(accountId))).isNotNull();
  }

  private String taskKey() { return TASK_KEY_PREFIX + accountId; }
  private String lockKey() { return LOCK_KEY_PREFIX + accountId; }

  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) throw new IllegalStateException(property + " 시스템 속성이 필요합니다.");
    return value;
  }
}
