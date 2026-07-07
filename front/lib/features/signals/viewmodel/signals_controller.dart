import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../model/signal.dart';
import '../repository/signals_repository.dart';

class SignalsController extends AsyncNotifier<List<SignalLog>> {
  @override
  Future<List<SignalLog>> build() {
    return ref.read(signalsRepositoryProvider).getSignals();
  }

  Future<void> refresh() async {
    state = const AsyncLoading<List<SignalLog>>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(signalsRepositoryProvider).getSignals(),
    );
  }
}

final signalsControllerProvider =
    AsyncNotifierProvider<SignalsController, List<SignalLog>>(
        SignalsController.new);
