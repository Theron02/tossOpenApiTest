import 'package:auto_trading/features/strategy/model/strategy.dart';
import 'package:auto_trading/features/strategy/repository/strategy_repository.dart';
import 'package:auto_trading/features/strategy/viewmodel/strategy_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class _MockStrategyRepository extends Mock implements StrategyRepository {}

const _s1 = Strategy(
  id: '1',
  strategyName: 'golden-cross',
  stockCode: '005930',
  enabled: false,
);
const _s2 = Strategy(
  id: '2',
  strategyName: 'rsi',
  stockCode: '000660',
  enabled: false,
);

void main() {
  late _MockStrategyRepository repo;

  ProviderContainer makeContainer() {
    final c = ProviderContainer(
      overrides: [strategyRepositoryProvider.overrideWithValue(repo)],
    );
    addTearDown(c.dispose);
    return c;
  }

  setUp(() => repo = _MockStrategyRepository());

  test('build 는 전략 목록을 로드한다', () async {
    when(() => repo.getStrategies()).thenAnswer((_) async => [_s1, _s2]);
    final c = makeContainer();

    final list = await c.read(strategyControllerProvider.future);

    expect(list, [_s1, _s2]);
  });

  test('setEnabled 성공 시 서버 응답으로 해당 전략만 교체한다', () async {
    when(() => repo.getStrategies()).thenAnswer((_) async => [_s1, _s2]);
    when(() => repo.updateStrategy('1', enabled: true))
        .thenAnswer((_) async => _s1.copyWith(enabled: true));
    final c = makeContainer();
    await c.read(strategyControllerProvider.future);

    await c.read(strategyControllerProvider.notifier).setEnabled('1', true);

    final list = c.read(strategyControllerProvider).value!;
    expect(list.firstWhere((s) => s.id == '1').enabled, true);
    expect(list.firstWhere((s) => s.id == '2').enabled, false);
  });

  test('updateStrategy 실패는 호출부로 전파되고 상태는 유지된다', () async {
    when(() => repo.getStrategies()).thenAnswer((_) async => [_s1]);
    when(() => repo.updateStrategy('1', enabled: true))
        .thenThrow(Exception('boom'));
    final c = makeContainer();
    await c.read(strategyControllerProvider.future);

    await expectLater(
      c.read(strategyControllerProvider.notifier).setEnabled('1', true),
      throwsException,
    );
    // 낙관적 반영하지 않으므로 상태는 그대로.
    expect(c.read(strategyControllerProvider).value!.single.enabled, false);
  });
}
