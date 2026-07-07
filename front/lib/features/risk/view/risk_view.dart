import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme.dart';
import '../../../core/error/failure.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/async_state_view.dart';
import '../../../shared/confirm_dialog.dart';
import '../model/risk_setting.dart';
import '../viewmodel/risk_controller.dart';

class RiskView extends ConsumerWidget {
  const RiskView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final risk = ref.watch(riskControllerProvider);
    Future<void> refresh() =>
        ref.read(riskControllerProvider.notifier).refresh();

    return RefreshIndicator(
      onRefresh: refresh,
      child: AsyncStateView<RiskSetting>(
        value: risk,
        onRetry: refresh,
        onData: (r) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _KillSwitchCard(r),
            const SizedBox(height: 16),
            _RiskLimitsCard(r),
          ],
        ),
      ),
    );
  }
}

class _KillSwitchCard extends ConsumerStatefulWidget {
  const _KillSwitchCard(this.r);
  final RiskSetting r;

  @override
  ConsumerState<_KillSwitchCard> createState() => _KillSwitchCardState();
}

class _KillSwitchCardState extends ConsumerState<_KillSwitchCard> {
  bool _busy = false;

  Future<void> _toggle(bool next) async {
    final ok = await showConfirmDialog(
      context,
      title: next ? 'KILL SWITCH 를 켤까요?' : 'KILL SWITCH 를 끌까요?',
      message: next
          ? '모든 신규 주문을 즉시 중단합니다. 계속하시겠어요?'
          : '신규 주문 차단을 해제합니다. 봇이 다시 자동 주문을 시작할 수 있습니다.',
      confirmLabel: next ? '중단하기' : '해제하기',
      danger: next,
    );
    if (!ok) return;

    setState(() => _busy = true);
    try {
      await ref.read(riskControllerProvider.notifier).setKillSwitch(next);
    } on Failure catch (e) {
      if (mounted) _snack(context, e.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final on = widget.r.killSwitch;
    return Card(
      color: on ? warningColor.withValues(alpha: 0.12) : null,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(on ? Icons.dangerous : Icons.shield_outlined,
                    color: on ? warningColor : null),
                const SizedBox(width: 8),
                const Text('Kill Switch',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const Spacer(),
                if (_busy)
                  const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2))
                else
                  Switch(
                    value: on,
                    activeThumbColor: warningColor,
                    onChanged: _toggle,
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              on
                  ? '현재 ON — 모든 신규 주문이 차단되어 있습니다.'
                  : '현재 OFF — 봇이 자동 주문을 실행할 수 있습니다.',
              style: TextStyle(
                color: on ? warningColor : Colors.grey,
                fontWeight: on ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _snack(BuildContext context, String msg) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _RiskLimitsCard extends ConsumerStatefulWidget {
  const _RiskLimitsCard(this.r);
  final RiskSetting r;

  @override
  ConsumerState<_RiskLimitsCard> createState() => _RiskLimitsCardState();
}

class _RiskLimitsCardState extends ConsumerState<_RiskLimitsCard> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _dailyLoss;
  late final TextEditingController _maxPct;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    // dailyLossLimit 은 문자열(원) → 정수 편집. 소수부는 절삭.
    final daily = widget.r.dailyLossLimit.split('.').first;
    _dailyLoss = TextEditingController(text: daily);
    _maxPct = TextEditingController(text: '${widget.r.maxPositionPct}');
  }

  @override
  void dispose() {
    _dailyLoss.dispose();
    _maxPct.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() => _busy = true);
    try {
      await ref.read(riskControllerProvider.notifier).updateSetting(
            dailyLossLimit: int.parse(_dailyLoss.text),
            maxPositionPct: int.parse(_maxPct.text),
          );
      if (mounted) _snack('리스크 설정을 저장했습니다.');
    } on Failure catch (e) {
      // 서버 검증 에러(validation-failed)를 사용자에게 표시.
      if (mounted) _snack(e.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('리스크 한도',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text('현재 일일 손실 한도 ${AppFormat.won(widget.r.dailyLossLimit)}',
                  style: const TextStyle(color: Colors.grey)),
              const SizedBox(height: 16),
              TextFormField(
                controller: _dailyLoss,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: '일일 손실 한도 (원)',
                  border: OutlineInputBorder(),
                  helperText: '0 이상 정수',
                ),
                validator: (v) {
                  final n = int.tryParse(v ?? '');
                  if (n == null) return '숫자를 입력하세요';
                  if (n < 0) return '0 이상이어야 합니다';
                  return null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _maxPct,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: '종목당 최대 비중 (%)',
                  border: OutlineInputBorder(),
                  helperText: '1 ~ 100',
                ),
                validator: (v) {
                  final n = int.tryParse(v ?? '');
                  if (n == null) return '숫자를 입력하세요';
                  if (n < 1 || n > 100) return '1 ~ 100 범위여야 합니다';
                  return null;
                },
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _busy ? null : _save,
                  child: _busy
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('저장'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}
