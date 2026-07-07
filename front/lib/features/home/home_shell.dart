import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/viewmodel/auth_controller.dart';
import '../backtest/view/backtest_view.dart';
import '../dashboard/view/dashboard_view.dart';
import '../orders/view/orders_view.dart';
import '../positions/view/positions_view.dart';
import '../risk/view/risk_view.dart';
import '../signals/view/signals_view.dart';
import '../strategy/view/strategy_view.dart';

class _Tab {
  const _Tab(this.title, this.icon, this.body);
  final String title;
  final IconData icon;
  final Widget body;
}

const _tabs = <_Tab>[
  _Tab('대시보드', Icons.dashboard_outlined, DashboardView()),
  _Tab('포지션', Icons.pie_chart_outline, PositionsView()),
  _Tab('주문·체결', Icons.receipt_long_outlined, OrdersView()),
  _Tab('신호 로그', Icons.insights_outlined, SignalsView()),
  _Tab('전략', Icons.tune_outlined, StrategyView()),
  _Tab('리스크·제어', Icons.shield_outlined, RiskView()),
  _Tab('백테스트', Icons.query_stats_outlined, BacktestView()),
];

/// 로그인 후 진입점. 좌측 드로어로 화면 전환(7개 화면). 매매 화면은 없다.
class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final tab = _tabs[_index];
    return Scaffold(
      appBar: AppBar(
        title: Text(tab.title),
        actions: [
          IconButton(
            tooltip: '로그아웃',
            icon: const Icon(Icons.logout),
            onPressed: () => ref.read(authControllerProvider.notifier).logout(),
          ),
        ],
      ),
      drawer: NavigationDrawer(
        selectedIndex: _index,
        onDestinationSelected: (i) {
          setState(() => _index = i);
          Navigator.of(context).pop();
        },
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(28, 24, 16, 12),
            child: Text('자동매매 모니터',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
          for (final t in _tabs)
            NavigationDrawerDestination(
              icon: Icon(t.icon),
              label: Text(t.title),
            ),
        ],
      ),
      body: IndexedStack(
        index: _index,
        children: [for (final t in _tabs) t.body],
      ),
    );
  }
}
