import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/async_state_view.dart';
import '../../../shared/pnl_text.dart';
import '../../risk/viewmodel/risk_controller.dart';
import '../model/portfolio.dart';
import '../viewmodel/portfolio_controller.dart';

class DashboardView extends ConsumerWidget {
  const DashboardView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final portfolio = ref.watch(portfolioControllerProvider);

    return RefreshIndicator(
      onRefresh: () =>
          ref.read(portfolioControllerProvider.notifier).refresh(),
      child: AsyncStateView<Portfolio>(
        value: portfolio,
        onRetry: () => ref.read(portfolioControllerProvider.notifier).refresh(),
        onData: (p) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const _BotStatusBanner(),
            const SizedBox(height: 12),
            _EquityCard(p),
            const SizedBox(height: 12),
            _BreakdownCard(p),
          ],
        ),
      ),
    );
  }
}

/// kill switch 상태를 대시보드 상단에 뚜렷이 표시(ON=경고색).
class _BotStatusBanner extends ConsumerWidget {
  const _BotStatusBanner();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final risk = ref.watch(riskControllerProvider);
    final killed = risk.valueOrNull?.killSwitch ?? false;
    final loading = risk.isLoading && !risk.hasValue;

    final Color bg;
    final IconData icon;
    final String label;
    if (loading) {
      bg = Theme.of(context).colorScheme.surfaceContainerHighest;
      icon = Icons.sync;
      label = '봇 상태 확인 중…';
    } else if (killed) {
      bg = warningColor;
      icon = Icons.dangerous;
      label = 'KILL SWITCH ON · 신규 주문 차단됨';
    } else {
      bg = const Color(0xFF2E7D32);
      icon = Icons.play_circle_fill;
      label = '봇 가동 중 · 자동 주문 활성';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Icon(icon, color: Colors.white),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              label,
              style: const TextStyle(
                  color: Colors.white, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }
}

class _EquityCard extends StatelessWidget {
  const _EquityCard(this.p);
  final Portfolio p;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(p.name,
                    style: Theme.of(context).textTheme.titleMedium),
                const Spacer(),
                if (!p.pricedAtMarket)
                  const Tooltip(
                    message: '현재가 미연동 — 평단가 기준 평가',
                    child: Chip(
                      visualDensity: VisualDensity.compact,
                      label: Text('평단가 기준'),
                      avatar: Icon(Icons.info_outline, size: 16),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            const Text('총 평가자산'),
            const SizedBox(height: 4),
            Text(
              AppFormat.won(p.totalEquity),
              style: Theme.of(context)
                  .textTheme
                  .headlineMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            PnlText(
              amount: p.evalPnl,
              rate: p.returnRate,
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ],
        ),
      ),
    );
  }
}

class _BreakdownCard extends StatelessWidget {
  const _BreakdownCard(this.p);
  final Portfolio p;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            _row('현금', AppFormat.won(p.cash)),
            const Divider(height: 24),
            _row('주식 평가금액', AppFormat.won(p.positionsValue)),
            const Divider(height: 24),
            _row('투자 원금', AppFormat.won(p.initialSeed)),
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      );
}
