import 'package:freezed_annotation/freezed_annotation.dart';

part 'portfolio.freezed.dart';
part 'portfolio.g.dart';

/// 계약 §3: GET /portfolio. 금액은 문자열(decimal)로 온다.
@freezed
class Portfolio with _$Portfolio {
  const factory Portfolio({
    required String accountId,
    required String name,
    required String cash,
    required String positionsValue,
    required String totalEquity,
    required String evalPnl,
    required String returnRate,
    required String initialSeed,
    @Default(false) bool pricedAtMarket,
  }) = _Portfolio;

  factory Portfolio.fromJson(Map<String, dynamic> json) =>
      _$PortfolioFromJson(json);
}
