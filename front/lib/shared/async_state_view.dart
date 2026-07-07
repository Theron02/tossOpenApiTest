import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/error/failure.dart';

/// 로딩/에러/데이터 상태를 일관되게 렌더링한다(무한 스피너·무응답 금지).
/// [isEmpty] 를 주면 빈 상태도 처리한다.
class AsyncStateView<T> extends StatelessWidget {
  const AsyncStateView({
    super.key,
    required this.value,
    required this.onData,
    this.onRetry,
    this.isEmpty,
    this.emptyMessage = '표시할 내용이 없습니다.',
  });

  final AsyncValue<T> value;
  final Widget Function(T data) onData;
  final Future<void> Function()? onRetry;
  final bool Function(T data)? isEmpty;
  final String emptyMessage;

  @override
  Widget build(BuildContext context) {
    return value.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => _ErrorView(
        message: err is Failure ? err.userMessage : '오류가 발생했습니다.',
        onRetry: onRetry,
      ),
      data: (data) {
        if (isEmpty != null && isEmpty!(data)) {
          return _EmptyView(message: emptyMessage, onRetry: onRetry);
        }
        return onData(data);
      },
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, this.onRetry});
  final String message;
  final Future<void> Function()? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 40),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Text(message, textAlign: TextAlign.center),
          ),
          if (onRetry != null) ...[
            const SizedBox(height: 16),
            FilledButton.tonal(onPressed: onRetry, child: const Text('다시 시도')),
          ],
        ],
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView({required this.message, this.onRetry});
  final String message;
  final Future<void> Function()? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.inbox_outlined,
              size: 40, color: Theme.of(context).disabledColor),
          const SizedBox(height: 12),
          Text(message),
          if (onRetry != null) ...[
            const SizedBox(height: 16),
            TextButton(onPressed: onRetry, child: const Text('새로고침')),
          ],
        ],
      ),
    );
  }
}
