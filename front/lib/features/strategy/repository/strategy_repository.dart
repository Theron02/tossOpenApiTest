import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/strategy.dart';

abstract class StrategyRepository {
  Future<List<Strategy>> getStrategies();

  /// 계약 §4: PATCH /strategies/{id}. null 필드는 미변경.
  Future<Strategy> updateStrategy(
    String id, {
    bool? enabled,
    Map<String, dynamic>? params,
  });
}

class StrategyRepositoryImpl implements StrategyRepository {
  StrategyRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<List<Strategy>> getStrategies() async {
    final data = await _api.get('/strategies');
    return (data as List)
        .map((e) => Strategy.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  @override
  Future<Strategy> updateStrategy(
    String id, {
    bool? enabled,
    Map<String, dynamic>? params,
  }) async {
    final data = await _api.patch('/strategies/$id', body: {
      if (enabled != null) 'enabled': enabled,
      if (params != null) 'params': params,
    });
    return Strategy.fromJson(Map<String, dynamic>.from(data as Map));
  }
}

final strategyRepositoryProvider = Provider<StrategyRepository>(
  (ref) => StrategyRepositoryImpl(ref.watch(apiClientProvider)),
);
