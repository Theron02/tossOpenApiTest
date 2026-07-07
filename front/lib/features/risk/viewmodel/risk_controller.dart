import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../model/risk_setting.dart';
import '../repository/risk_repository.dart';

class RiskController extends AsyncNotifier<RiskSetting> {
  @override
  Future<RiskSetting> build() {
    return ref.read(riskRepositoryProvider).getRiskSetting();
  }

  Future<void> refresh() async {
    state = const AsyncLoading<RiskSetting>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(riskRepositoryProvider).getRiskSetting(),
    );
  }

  /// 일일한도·비중 변경. 검증 실패(validation-failed)는 [Failure] 로 View 에 전달.
  Future<void> updateSetting({int? dailyLossLimit, int? maxPositionPct}) async {
    final updated = await ref.read(riskRepositoryProvider).updateRiskSetting(
          dailyLossLimit: dailyLossLimit,
          maxPositionPct: maxPositionPct,
        );
    state = AsyncData(updated);
  }

  /// kill switch. 계약상 confirm=true 필수. View 의 확인 다이얼로그 확정 후에만 호출.
  /// 위험 동작이므로 서버 응답으로만 상태를 확정한다.
  Future<void> setKillSwitch(bool enabled) async {
    final updated = await ref
        .read(riskRepositoryProvider)
        .setKillSwitch(enabled: enabled, confirm: true);
    state = AsyncData(updated);
  }
}

final riskControllerProvider =
    AsyncNotifierProvider<RiskController, RiskSetting>(RiskController.new);
