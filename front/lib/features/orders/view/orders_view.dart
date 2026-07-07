import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/util/formatters.dart';
import '../../../shared/async_state_view.dart';
import '../model/execution.dart';
import '../model/order.dart';
import '../viewmodel/orders_controller.dart';

class OrdersView extends StatelessWidget {
  const OrdersView({super.key});

  @override
  Widget build(BuildContext context) {
    return const DefaultTabController(
      length: 2,
      child: Column(
        children: [
          TabBar(tabs: [Tab(text: '주문'), Tab(text: '체결')]),
          Expanded(
            child: TabBarView(
              children: [_OrdersTab(), _ExecutionsTab()],
            ),
          ),
        ],
      ),
    );
  }
}

const _statuses = <String?>[
  null,
  'PENDING',
  'FILLED',
  'PARTIAL',
  'CANCELLED',
  'REJECTED',
];

class _OrdersTab extends ConsumerWidget {
  const _OrdersTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selected = ref.watch(orderStatusFilterProvider);
    final orders = ref.watch(ordersControllerProvider);
    Future<void> refresh() =>
        ref.read(ordersControllerProvider.notifier).refresh();

    return Column(
      children: [
        SizedBox(
          height: 56,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            itemCount: _statuses.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (_, i) {
              final s = _statuses[i];
              return ChoiceChip(
                label: Text(s ?? '전체'),
                selected: selected == s,
                onSelected: (_) =>
                    ref.read(orderStatusFilterProvider.notifier).state = s,
              );
            },
          ),
        ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: refresh,
            child: AsyncStateView<List<Order>>(
              value: orders,
              onRetry: refresh,
              isEmpty: (l) => l.isEmpty,
              emptyMessage: '주문 내역이 없습니다.',
              onData: (list) => ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: list.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (_, i) => _OrderCard(list[i]),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _OrderCard extends StatelessWidget {
  const _OrderCard(this.o);
  final Order o;

  @override
  Widget build(BuildContext context) {
    final isBuy = o.side == 'BUY';
    final sideColor = isBuy ? const Color(0xFFE53935) : const Color(0xFF1E88E5);
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
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: sideColor.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(isBuy ? '매수' : '매도',
                      style: TextStyle(
                          color: sideColor, fontWeight: FontWeight.bold)),
                ),
                const SizedBox(width: 8),
                Text(o.stockCode,
                    style: const TextStyle(fontWeight: FontWeight.bold)),
                const Spacer(),
                _StatusChip(o.status),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('${o.orderType} · ${o.quantity}주'),
                Text(AppFormat.won(o.price)),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('체결 ${o.filledQuantity}/${o.quantity}',
                    style: const TextStyle(color: Colors.grey)),
                Text(AppFormat.kst(o.createdAt),
                    style: const TextStyle(color: Colors.grey, fontSize: 12)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip(this.status);
  final String status;

  @override
  Widget build(BuildContext context) {
    Color c;
    switch (status) {
      case 'FILLED':
        c = const Color(0xFF2E7D32);
      case 'PARTIAL':
        c = const Color(0xFFF9A825);
      case 'PENDING':
        c = Colors.blueGrey;
      case 'REJECTED':
        c = const Color(0xFFC62828);
      default:
        c = Colors.grey;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: c.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(status,
          style: TextStyle(
              color: c, fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }
}

class _ExecutionsTab extends ConsumerWidget {
  const _ExecutionsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final executions = ref.watch(executionsControllerProvider);
    Future<void> refresh() =>
        ref.read(executionsControllerProvider.notifier).refresh();

    return RefreshIndicator(
      onRefresh: refresh,
      child: AsyncStateView<List<Execution>>(
        value: executions,
        onRetry: refresh,
        isEmpty: (l) => l.isEmpty,
        emptyMessage: '체결 내역이 없습니다.',
        onData: (list) => ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: list.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (_, i) {
            final e = list[i];
            return ListTile(
              title: Text('${e.filledQty}주 @ ${AppFormat.won(e.filledPrice)}'),
              subtitle: Text('수수료 ${AppFormat.won(e.fee)}'),
              trailing: Text(AppFormat.kst(e.executedAt),
                  style: const TextStyle(fontSize: 12, color: Colors.grey)),
            );
          },
        ),
      ),
    );
  }
}
