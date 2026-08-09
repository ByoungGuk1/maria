package com.app.maria.performance.domain.settlement;

import java.util.List;

final class PerformanceStatistics {
  private PerformanceStatistics() {}

  static Result calculate(List<Long> values) {
    List<Long> sorted = values.stream().sorted().toList();
    long total = sorted.stream().mapToLong(Long::longValue).sum();
    int p95Index = (int) Math.ceil(sorted.size() * 0.95d) - 1;
    return new Result(total / (double) sorted.size(), sorted.get(p95Index), sorted.get(0), sorted.get(sorted.size() - 1));
  }

  record Result(double averageMillis, long p95Millis, long minMillis, long maxMillis) {}
}
