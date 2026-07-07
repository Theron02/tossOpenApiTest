import 'package:freezed_annotation/freezed_annotation.dart';

part 'order.freezed.dart';
part 'order.g.dart';

/// 계약 §3: GET /orders[].
@freezed
class Order with _$Order {
  const factory Order({
    required String id,
    required String stockCode,
    required String side, // BUY / SELL
    required String orderType, // MARKET / LIMIT
    required int quantity,
    required String price,
    required String status, // PENDING/FILLED/PARTIAL/CANCELLED/REJECTED
    required int filledQuantity,
    required String createdAt,
  }) = _Order;

  factory Order.fromJson(Map<String, dynamic> json) => _$OrderFromJson(json);
}
