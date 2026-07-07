import 'package:freezed_annotation/freezed_annotation.dart';

part 'position.freezed.dart';
part 'position.g.dart';

/// 계약 §3: GET /positions[]. currentPrice 는 시세 미연동 시 null.
@freezed
class Position with _$Position {
  const factory Position({
    required String stockCode,
    required int quantity,
    required String avgPrice,
    String? currentPrice,
    required String evalAmount,
    required String evalPnl,
    required String pnlRate,
  }) = _Position;

  factory Position.fromJson(Map<String, dynamic> json) =>
      _$PositionFromJson(json);
}
