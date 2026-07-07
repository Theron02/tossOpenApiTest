import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../model/strategy.dart';
import '../repository/strategy_repository.dart';

class StrategyController extends AsyncNotifier<List<Strategy>> {
  @override
  Future<List<Strategy>> build() {
    return ref.read(strategyRepositoryProvider).getStrategies();
  }

  Future<void> refresh() async {
    state = const AsyncLoading<List<Strategy>>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(strategyRepositoryProvider).getStrategies(),
    );
  }

  /// 전략 on/off. 위험 동작이므로 낙관적 반영하지 않고 서버 응답으로 확정한다.
  /// 실패 시 [Failure] 를 던지므로 호출부(View)에서 처리한다.
  Future<void> setEnabled(String id, bool enabled) async {
    final updated = await ref
        .read(strategyRepositoryProvider)
        .updateStrategy(id, enabled: enabled);
    _replace(updated);
  }

  Future<void> updateParams(String id, Map<String, dynamic> params) async {
    final updated = await ref
        .read(strategyRepositoryProvider)
        .updateStrategy(id, params: params);
    _replace(updated);
  }

  void _replace(Strategy updated) {
    final list = state.valueOrNull ?? const <Strategy>[];
    state = AsyncData([
      for (final s in list) s.id == updated.id ? updated : s,
    ]);
  }
}

final strategyControllerProvider =
    AsyncNotifierProvider<StrategyController, List<Strategy>>(
        StrategyController.new);
