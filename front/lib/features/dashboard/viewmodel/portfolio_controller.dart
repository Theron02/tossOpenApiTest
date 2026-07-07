import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/config.dart';
import '../model/portfolio.dart';
import '../repository/portfolio_repository.dart';

/// 대시보드 포트폴리오. 진입 시 로드 + 주기 폴링(실시간 대체).
class PortfolioController extends AsyncNotifier<Portfolio> {
  Timer? _timer;

  @override
  Future<Portfolio> build() async {
    ref.onDispose(() => _timer?.cancel());
    _startPolling();
    return ref.read(portfolioRepositoryProvider).getPortfolio();
  }

  void _startPolling() {
    _timer?.cancel();
    _timer = Timer.periodic(
      const Duration(seconds: AppConfig.pollIntervalSeconds),
      (_) => _silentRefresh(),
    );
  }

  /// 폴링: 스피너 없이 조용히 갱신(에러는 무시하고 다음 주기 재시도).
  Future<void> _silentRefresh() async {
    final next = await AsyncValue.guard(
      () => ref.read(portfolioRepositoryProvider).getPortfolio(),
    );
    if (next.hasValue) state = next;
  }

  /// 당김 새로고침·수동 새로고침용(에러 노출).
  Future<void> refresh() async {
    state = const AsyncLoading<Portfolio>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(portfolioRepositoryProvider).getPortfolio(),
    );
  }
}

final portfolioControllerProvider =
    AsyncNotifierProvider<PortfolioController, Portfolio>(
        PortfolioController.new);
