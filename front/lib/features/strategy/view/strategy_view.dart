import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/failure.dart';
import '../../../shared/async_state_view.dart';
import '../../../shared/confirm_dialog.dart';
import '../model/strategy.dart';
import '../viewmodel/strategy_controller.dart';

class StrategyView extends ConsumerWidget {
  const StrategyView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strategies = ref.watch(strategyControllerProvider);
    Future<void> refresh() =>
        ref.read(strategyControllerProvider.notifier).refresh();

    return RefreshIndicator(
      onRefresh: refresh,
      child: AsyncStateView<List<Strategy>>(
        value: strategies,
        onRetry: refresh,
        isEmpty: (l) => l.isEmpty,
        emptyMessage: '등록된 전략이 없습니다.',
        onData: (list) => ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: list.length,
          separatorBuilder: (_, __) => const SizedBox(height: 10),
          itemBuilder: (_, i) => _StrategyCard(list[i]),
        ),
      ),
    );
  }
}

class _StrategyCard extends ConsumerWidget {
  const _StrategyCard(this.s);
  final Strategy s;

  Future<void> _toggle(BuildContext context, WidgetRef ref, bool next) async {
    // 활성화(true)는 봇이 자동 주문을 시작한다는 의미 → 확인 다이얼로그.
    if (next) {
      final ok = await showConfirmDialog(
        context,
        title: '전략을 활성화할까요?',
        message: "'${s.strategyName}' (${s.stockCode})\n\n"
            '켜면 봇이 이 전략으로 자동 주문을 시작합니다. 계속하시겠어요?',
        confirmLabel: '활성화',
      );
      if (!ok) return;
    }
    try {
      await ref.read(strategyControllerProvider.notifier).setEnabled(s.id, next);
    } on Failure catch (e) {
      if (context.mounted) _snack(context, e.userMessage);
    }
  }

  Future<void> _editParams(BuildContext context, WidgetRef ref) async {
    final updated = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (_) => _ParamsEditDialog(s.params),
    );
    if (updated == null) return;
    try {
      await ref
          .read(strategyControllerProvider.notifier)
          .updateParams(s.id, updated);
      if (context.mounted) _snack(context, '파라미터를 저장했습니다.');
    } on Failure catch (e) {
      if (context.mounted) _snack(context, e.userMessage);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final params =
        s.params.entries.map((e) => '${e.key}=${e.value}').join(', ');
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 8, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(s.strategyName,
                          style: const TextStyle(
                              fontWeight: FontWeight.bold, fontSize: 16)),
                      Text(s.stockCode,
                          style: const TextStyle(color: Colors.grey)),
                    ],
                  ),
                ),
                Switch(
                  value: s.enabled,
                  onChanged: (v) => _toggle(context, ref, v),
                ),
              ],
            ),
            if (params.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(params,
                    style: const TextStyle(fontSize: 13, color: Colors.grey)),
              ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                icon: const Icon(Icons.tune, size: 18),
                label: const Text('파라미터 편집'),
                onPressed: () => _editParams(context, ref),
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

/// 파라미터 값을 텍스트로 편집한다(숫자는 숫자 타입으로 되돌려 전송).
class _ParamsEditDialog extends StatefulWidget {
  const _ParamsEditDialog(this.params);
  final Map<String, dynamic> params;

  @override
  State<_ParamsEditDialog> createState() => _ParamsEditDialogState();
}

class _ParamsEditDialogState extends State<_ParamsEditDialog> {
  late final Map<String, TextEditingController> _controllers = {
    for (final e in widget.params.entries)
      e.key: TextEditingController(text: '${e.value}'),
  };

  @override
  void dispose() {
    for (final c in _controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  Object _coerce(Object original, String text) {
    if (original is int) return int.tryParse(text) ?? original;
    if (original is double) return double.tryParse(text) ?? original;
    if (original is num) return num.tryParse(text) ?? original;
    return text;
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('파라미터 편집'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_controllers.isEmpty) const Text('편집할 파라미터가 없습니다.'),
            for (final e in _controllers.entries)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: TextField(
                  controller: e.value,
                  decoration: InputDecoration(
                    labelText: e.key,
                    border: const OutlineInputBorder(),
                  ),
                ),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () {
            final result = <String, dynamic>{
              for (final e in _controllers.entries)
                e.key: _coerce(widget.params[e.key] as Object, e.value.text),
            };
            Navigator.of(context).pop(result);
          },
          child: const Text('저장'),
        ),
      ],
    );
  }
}
