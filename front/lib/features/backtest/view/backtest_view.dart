import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/failure.dart';
import '../../../core/util/formatters.dart';
import '../model/backtest.dart';
import '../viewmodel/backtest_controller.dart';

const _intervals = <String>['DAY', 'WEEK', 'MONTH'];

class BacktestView extends ConsumerStatefulWidget {
  const BacktestView({super.key});

  @override
  ConsumerState<BacktestView> createState() => _BacktestViewState();
}

class _BacktestViewState extends ConsumerState<BacktestView> {
  final _formKey = GlobalKey<FormState>();
  final _symbol = TextEditingController(text: '005930');
  final _strategy = TextEditingController(text: 'golden-cross');
  final _capital = TextEditingController(text: '10000000');
  final _shortPeriod = TextEditingController(text: '5');
  final _longPeriod = TextEditingController(text: '20');
  String _interval = 'DAY';

  @override
  void dispose() {
    _symbol.dispose();
    _strategy.dispose();
    _capital.dispose();
    _shortPeriod.dispose();
    _longPeriod.dispose();
    super.dispose();
  }

  void _run() {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    final request = BacktestRequest(
      symbol: _symbol.text.trim(),
      interval: _interval,
      strategy: _strategy.text.trim(),
      initialCapital: _capital.text.trim(),
      params: {
        'shortPeriod': int.tryParse(_shortPeriod.text) ?? 5,
        'longPeriod': int.tryParse(_longPeriod.text) ?? 20,
      },
    );
    ref.read(backtestControllerProvider.notifier).run(request);
  }

  @override
  Widget build(BuildContext context) {
    final result = ref.watch(backtestControllerProvider);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _field(_symbol, '종목코드', required: true),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _interval,
                    decoration: const InputDecoration(
                        labelText: '봉 주기', border: OutlineInputBorder()),
                    items: [
                      for (final i in _intervals)
                        DropdownMenuItem(value: i, child: Text(i)),
                    ],
                    onChanged: (v) => setState(() => _interval = v ?? 'DAY'),
                  ),
                  const SizedBox(height: 12),
                  _field(_strategy, '전략명', required: true),
                  const SizedBox(height: 12),
                  _field(_capital, '초기 자본 (원)',
                      required: true, number: true),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                          child: _field(_shortPeriod, '단기 기간',
                              number: true)),
                      const SizedBox(width: 12),
                      Expanded(
                          child: _field(_longPeriod, '장기 기간', number: true)),
                    ],
                  ),
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    onPressed: result?.isLoading == true ? null : _run,
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('백테스트 실행'),
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (result != null) _ResultSection(result),
      ],
    );
  }

  Widget _field(TextEditingController c, String label,
      {bool required = false, bool number = false}) {
    return TextFormField(
      controller: c,
      keyboardType: number ? TextInputType.number : null,
      decoration:
          InputDecoration(labelText: label, border: const OutlineInputBorder()),
      validator: required
          ? (v) => (v == null || v.trim().isEmpty) ? '$label 을(를) 입력하세요' : null
          : null,
    );
  }
}

class _ResultSection extends StatelessWidget {
  const _ResultSection(this.state);
  final AsyncValue<BacktestResult> state;

  @override
  Widget build(BuildContext context) {
    return state.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(32),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (err, _) => Card(
        color: Theme.of(context).colorScheme.errorContainer,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Text(err is Failure ? err.userMessage : '백테스트 실행에 실패했습니다.'),
        ),
      ),
      data: (r) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _MetricsCard(r),
          const SizedBox(height: 16),
          _EquityChartCard(r.equityCurve),
          if (r.warnings.isNotEmpty) ...[
            const SizedBox(height: 16),
            Card(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('경고',
                        style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 6),
                    for (final w in r.warnings) Text('• $w'),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _MetricsCard extends StatelessWidget {
  const _MetricsCard(this.r);
  final BacktestResult r;

  @override
  Widget build(BuildContext context) {
    final items = <(String, String)>[
      ('총 수익률', AppFormat.percent(r.totalReturn)),
      ('CAGR', AppFormat.percent(r.cagr)),
      ('MDD', AppFormat.percent(r.maxDrawdown, withSign: false)),
      ('승률', AppFormat.percent(r.winRate, withSign: false)),
      ('손익비', r.profitFactor),
      ('최종 자산', AppFormat.won(r.finalEquity)),
      ('총 거래', '${r.totalTrades}회'),
      ('청산 거래', '${r.closedTrades}회'),
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Wrap(
          spacing: 12,
          runSpacing: 16,
          children: [
            for (final (label, value) in items)
              SizedBox(
                width: 140,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(label,
                        style:
                            const TextStyle(color: Colors.grey, fontSize: 12)),
                    const SizedBox(height: 2),
                    Text(value,
                        style: const TextStyle(
                            fontSize: 16, fontWeight: FontWeight.bold)),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _EquityChartCard extends StatelessWidget {
  const _EquityChartCard(this.curve);
  final List<EquityPoint> curve;

  @override
  Widget build(BuildContext context) {
    if (curve.isEmpty) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: Text('equity curve 데이터가 없습니다.')),
        ),
      );
    }
    final spots = <FlSpot>[
      for (var i = 0; i < curve.length; i++)
        FlSpot(i.toDouble(), double.tryParse(curve[i].equity) ?? 0),
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(8, 20, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(left: 12),
              child: Text('Equity Curve',
                  style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 220,
              child: LineChart(
                LineChartData(
                  gridData: const FlGridData(show: true, drawVerticalLine: false),
                  titlesData: const FlTitlesData(
                    topTitles:
                        AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    rightTitles:
                        AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles:
                        AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    leftTitles: AxisTitles(
                        sideTitles:
                            SideTitles(showTitles: true, reservedSize: 44)),
                  ),
                  borderData: FlBorderData(show: false),
                  lineBarsData: [
                    LineChartBarData(
                      spots: spots,
                      isCurved: false,
                      dotData: const FlDotData(show: false),
                      color: Theme.of(context).colorScheme.primary,
                      barWidth: 2,
                      belowBarData: BarAreaData(
                        show: true,
                        color: Theme.of(context)
                            .colorScheme
                            .primary
                            .withValues(alpha: 0.12),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
