import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/util/formatters.dart';
import '../../../shared/async_state_view.dart';
import '../../../shared/pnl_text.dart';
import '../model/position.dart';
import '../viewmodel/positions_controller.dart';

class PositionsView extends ConsumerWidget {
  const PositionsView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positions = ref.watch(positionsControllerProvider);
    Future<void> refresh() =>
        ref.read(positionsControllerProvider.notifier).refresh();

    return RefreshIndicator(
      onRefresh: refresh,
      child: AsyncStateView<List<Position>>(
        value: positions,
        onRetry: refresh,
        isEmpty: (list) => list.isEmpty,
        emptyMessage: '보유 중인 포지션이 없습니다.',
        onData: (list) => ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: list.length,
          separatorBuilder: (_, __) => const SizedBox(height: 10),
          itemBuilder: (_, i) => _PositionCard(list[i]),
        ),
      ),
    );
  }
}

class _PositionCard extends StatelessWidget {
  const _PositionCard(this.p);
  final Position p;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(p.stockCode,
                    style: Theme.of(context)
                        .textTheme
                        .titleMedium
                        ?.copyWith(fontWeight: FontWeight.bold)),
                const Spacer(),
                Text('${p.quantity}주'),
              ],
            ),
            const SizedBox(height: 12),
            _row('평단가', AppFormat.won(p.avgPrice)),
            const SizedBox(height: 4),
            _row('현재가',
                p.currentPrice == null ? '시세 미연동' : AppFormat.won(p.currentPrice)),
            const SizedBox(height: 4),
            _row('평가금액', AppFormat.won(p.evalAmount)),
            const Divider(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('평가손익'),
                PnlText(amount: p.evalPnl, rate: p.pnlRate),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          Text(value),
        ],
      );
}
