import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../model/position.dart';
import '../repository/positions_repository.dart';

class PositionsController extends AsyncNotifier<List<Position>> {
  @override
  Future<List<Position>> build() {
    return ref.read(positionsRepositoryProvider).getPositions();
  }

  Future<void> refresh() async {
    state = const AsyncLoading<List<Position>>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(positionsRepositoryProvider).getPositions(),
    );
  }
}

final positionsControllerProvider =
    AsyncNotifierProvider<PositionsController, List<Position>>(
        PositionsController.new);
