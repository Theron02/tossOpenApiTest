import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/risk_setting.dart';

abstract class RiskRepository {
  Future<RiskSetting> getRiskSetting();

  /// 계약 §4: PATCH /risk-setting. dailyLossLimit≥0(정수 원), maxPositionPct 1–100.
  Future<RiskSetting> updateRiskSetting({int? dailyLossLimit, int? maxPositionPct});

  /// 계약 §4: POST /risk-setting/kill-switch. confirm=true 필수(오작동 방지).
  Future<RiskSetting> setKillSwitch({required bool enabled, required bool confirm});
}

class RiskRepositoryImpl implements RiskRepository {
  RiskRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<RiskSetting> getRiskSetting() async {
    final data = await _api.get('/risk-setting');
    return RiskSetting.fromJson(Map<String, dynamic>.from(data as Map));
  }

  @override
  Future<RiskSetting> updateRiskSetting({
    int? dailyLossLimit,
    int? maxPositionPct,
  }) async {
    final data = await _api.patch('/risk-setting', body: {
      if (dailyLossLimit != null) 'dailyLossLimit': dailyLossLimit,
      if (maxPositionPct != null) 'maxPositionPct': maxPositionPct,
    });
    return RiskSetting.fromJson(Map<String, dynamic>.from(data as Map));
  }

  @override
  Future<RiskSetting> setKillSwitch({
    required bool enabled,
    required bool confirm,
  }) async {
    final data = await _api.post('/risk-setting/kill-switch', body: {
      'enabled': enabled,
      'confirm': confirm,
    });
    return RiskSetting.fromJson(Map<String, dynamic>.from(data as Map));
  }
}

final riskRepositoryProvider = Provider<RiskRepository>(
  (ref) => RiskRepositoryImpl(ref.watch(apiClientProvider)),
);
