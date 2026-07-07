import 'package:auto_trading/features/risk/model/risk_setting.dart';
import 'package:auto_trading/features/risk/repository/risk_repository.dart';
import 'package:auto_trading/features/risk/viewmodel/risk_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class _MockRiskRepository extends Mock implements RiskRepository {}

const _setting = RiskSetting(
  id: 'r1',
  accountId: 'a1',
  dailyLossLimit: '500000',
  maxPositionPct: 20,
  killSwitch: false,
);

void main() {
  late _MockRiskRepository repo;

  ProviderContainer makeContainer() {
    final c = ProviderContainer(
      overrides: [riskRepositoryProvider.overrideWithValue(repo)],
    );
    addTearDown(c.dispose);
    return c;
  }

  setUp(() => repo = _MockRiskRepository());

  test('setKillSwitch 는 항상 confirm=true 로 호출하고 서버 응답으로 확정한다', () async {
    when(() => repo.getRiskSetting()).thenAnswer((_) async => _setting);
    when(() => repo.setKillSwitch(enabled: true, confirm: true))
        .thenAnswer((_) async => _setting.copyWith(killSwitch: true));
    final c = makeContainer();
    await c.read(riskControllerProvider.future);

    await c.read(riskControllerProvider.notifier).setKillSwitch(true);

    verify(() => repo.setKillSwitch(enabled: true, confirm: true)).called(1);
    expect(c.read(riskControllerProvider).value!.killSwitch, true);
  });

  test('updateSetting 성공 시 상태를 갱신한다', () async {
    when(() => repo.getRiskSetting()).thenAnswer((_) async => _setting);
    when(() => repo.updateRiskSetting(dailyLossLimit: 300000, maxPositionPct: 10))
        .thenAnswer((_) async =>
            _setting.copyWith(dailyLossLimit: '300000', maxPositionPct: 10));
    final c = makeContainer();
    await c.read(riskControllerProvider.future);

    await c.read(riskControllerProvider.notifier).updateSetting(
          dailyLossLimit: 300000,
          maxPositionPct: 10,
        );

    final r = c.read(riskControllerProvider).value!;
    expect(r.dailyLossLimit, '300000');
    expect(r.maxPositionPct, 10);
  });
}
