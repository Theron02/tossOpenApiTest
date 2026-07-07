import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../model/backtest.dart';
import '../repository/backtest_repository.dart';

/// 백테스트는 사용자가 실행 버튼을 눌러야 시작된다.
/// null = 아직 실행 안 함 / AsyncLoading = 실행 중 / data|error = 결과.
class BacktestController extends Notifier<AsyncValue<BacktestResult>?> {
  @override
  AsyncValue<BacktestResult>? build() => null;

  Future<void> run(BacktestRequest request) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref.read(backtestRepositoryProvider).run(request),
    );
  }

  void reset() => state = null;
}

final backtestControllerProvider =
    NotifierProvider<BacktestController, AsyncValue<BacktestResult>?>(
        BacktestController.new);
