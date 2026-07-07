import 'package:freezed_annotation/freezed_annotation.dart';

part 'backtest.freezed.dart';
part 'backtest.g.dart';

/// 계약 §5: POST /backtests 요청. 필수: symbol, interval, strategy, initialCapital.
@freezed
class BacktestRequest with _$BacktestRequest {
  const factory BacktestRequest({
    required String symbol,
    required String interval, // DAY / ...
    required String strategy,
    @Default(<String, dynamic>{}) Map<String, dynamic> params,
    required String initialCapital,
    String? commissionRate,
    String? taxRate,
    String? slippageRate,
    int? positionSizePct,
  }) = _BacktestRequest;

  factory BacktestRequest.fromJson(Map<String, dynamic> json) =>
      _$BacktestRequestFromJson(json);
}

@freezed
class EquityPoint with _$EquityPoint {
  const factory EquityPoint({
    required String time,
    required String equity,
  }) = _EquityPoint;

  factory EquityPoint.fromJson(Map<String, dynamic> json) =>
      _$EquityPointFromJson(json);
}

/// 계약 §5: POST /backtests 응답 data.
@freezed
class BacktestResult with _$BacktestResult {
  const factory BacktestResult({
    required String initialCapital,
    required String finalEquity,
    required String totalReturn,
    required String cagr,
    required String maxDrawdown,
    required String winRate,
    required String profitFactor,
    required int totalTrades,
    required int closedTrades,
    required String totalCommission,
    required String totalTax,
    @Default(<EquityPoint>[]) List<EquityPoint> equityCurve,
    @Default(<String>[]) List<String> warnings,
  }) = _BacktestResult;

  factory BacktestResult.fromJson(Map<String, dynamic> json) =>
      _$BacktestResultFromJson(json);
}
