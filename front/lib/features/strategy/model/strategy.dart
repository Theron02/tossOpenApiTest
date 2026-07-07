import 'package:freezed_annotation/freezed_annotation.dart';

part 'strategy.freezed.dart';
part 'strategy.g.dart';

/// 계약 §3/§4: GET /strategies[], PATCH /strategies/{id}.
/// params 는 전략별 파라미터 자유 맵.
@freezed
class Strategy with _$Strategy {
  const factory Strategy({
    required String id,
    required String strategyName,
    required String stockCode,
    @Default(<String, dynamic>{}) Map<String, dynamic> params,
    @Default(false) bool enabled,
  }) = _Strategy;

  factory Strategy.fromJson(Map<String, dynamic> json) =>
      _$StrategyFromJson(json);
}
