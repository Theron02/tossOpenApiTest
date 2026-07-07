import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/util/formatters.dart';
import '../../../shared/async_state_view.dart';
import '../model/signal.dart';
import '../viewmodel/signals_controller.dart';

class SignalsView extends ConsumerWidget {
  const SignalsView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final signals = ref.watch(signalsControllerProvider);
    Future<void> refresh() =>
        ref.read(signalsControllerProvider.notifier).refresh();

    return RefreshIndicator(
      onRefresh: refresh,
      child: AsyncStateView<List<SignalLog>>(
        value: signals,
        onRetry: refresh,
        isEmpty: (l) => l.isEmpty,
        emptyMessage: '기록된 신호가 없습니다.',
        onData: (list) => ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: list.length,
          separatorBuilder: (_, __) => const SizedBox(height: 10),
          itemBuilder: (_, i) => _SignalCard(list[i]),
        ),
      ),
    );
  }
}

class _SignalCard extends StatelessWidget {
  const _SignalCard(this.s);
  final SignalLog s;

  Color get _signalColor => switch (s.signal) {
        'BUY' => const Color(0xFFE53935),
        'SELL' => const Color(0xFF1E88E5),
        _ => Colors.grey,
      };

  @override
  Widget build(BuildContext context) {
    final indicators = s.indicatorSnapshot.entries
        .map((e) => '${e.key}: ${e.value}')
        .join('  ·  ');
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: _signalColor.withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(s.signal,
                      style: TextStyle(
                          color: _signalColor, fontWeight: FontWeight.bold)),
                ),
                const SizedBox(width: 10),
                Text(s.stockCode,
                    style: const TextStyle(fontWeight: FontWeight.bold)),
                const Spacer(),
                Text(AppFormat.kst(s.createdAt),
                    style: const TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
            const SizedBox(height: 8),
            Text(s.strategyName,
                style: Theme.of(context).textTheme.bodyMedium),
            if (indicators.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(indicators,
                  style: const TextStyle(fontSize: 12, color: Colors.grey)),
            ],
          ],
        ),
      ),
    );
  }
}
