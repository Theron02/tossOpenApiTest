import 'package:freezed_annotation/freezed_annotation.dart';

part 'execution.freezed.dart';
part 'execution.g.dart';

/// 계약 §3: GET /executions[].
@freezed
class Execution with _$Execution {
  const factory Execution({
    required String id,
    required String orderId,
    required int filledQty,
    required String filledPrice,
    required String fee,
    required String executedAt,
  }) = _Execution;

  factory Execution.fromJson(Map<String, dynamic> json) =>
      _$ExecutionFromJson(json);
}
