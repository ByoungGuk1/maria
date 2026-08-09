package com.app.maria.performance.domain.settlement;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PerformanceStatisticsTest {
  @Test
  void calculatesAverageAndNearestRankP95() {
    PerformanceStatistics.Result result = PerformanceStatistics.calculate(
        java.util.stream.IntStream.rangeClosed(1, 20).mapToObj(Long::valueOf).toList());
    assertThat(result.averageMillis()).isEqualTo(10.5);
    assertThat(result.p95Millis()).isEqualTo(19);
    assertThat(result.minMillis()).isEqualTo(1);
    assertThat(result.maxMillis()).isEqualTo(20);
  }
}
