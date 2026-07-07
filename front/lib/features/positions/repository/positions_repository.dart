import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/position.dart';

abstract class PositionsRepository {
  Future<List<Position>> getPositions();
}

class PositionsRepositoryImpl implements PositionsRepository {
  PositionsRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<List<Position>> getPositions() async {
    final data = await _api.get('/positions');
    return (data as List)
        .map((e) => Position.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }
}

final positionsRepositoryProvider = Provider<PositionsRepository>(
  (ref) => PositionsRepositoryImpl(ref.watch(apiClientProvider)),
);
