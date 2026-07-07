import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/backtest.dart';

abstract class BacktestRepository {
  /// 계약 §5: POST /backtests. 실주문과 격리된 경로(토스 주문 없음).
  Future<BacktestResult> run(BacktestRequest request);
}

class BacktestRepositoryImpl implements BacktestRepository {
  BacktestRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<BacktestResult> run(BacktestRequest request) async {
    // 미지정(null) 선택 필드는 전송하지 않는다(계약 §5: 미지정 시 엔진 기본값).
    final body = request.toJson()..removeWhere((_, v) => v == null);
    final data = await _api.post('/backtests', body: body);
    return BacktestResult.fromJson(Map<String, dynamic>.from(data as Map));
  }
}

final backtestRepositoryProvider = Provider<BacktestRepository>(
  (ref) => BacktestRepositoryImpl(ref.watch(apiClientProvider)),
);
