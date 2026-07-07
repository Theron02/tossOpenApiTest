import 'package:freezed_annotation/freezed_annotation.dart';

part 'signal.freezed.dart';
part 'signal.g.dart';

/// 계약 §3: GET /signals[]. indicatorSnapshot 은 지표명→값(문자열)의 자유 맵.
@freezed
class SignalLog with _$SignalLog {
  const factory SignalLog({
    required String id,
    required String strategyName,
    required String stockCode,
    required String signal, // BUY / SELL / HOLD
    @Default(<String, dynamic>{}) Map<String, dynamic> indicatorSnapshot,
    required String createdAt,
  }) = _SignalLog;

  factory SignalLog.fromJson(Map<String, dynamic> json) =>
      _$SignalLogFromJson(json);
}
