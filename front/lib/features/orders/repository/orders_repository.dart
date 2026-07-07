import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/execution.dart';
import '../model/order.dart';

abstract class OrdersRepository {
  /// 계약 §3: GET /orders?status=&page=&size=
  Future<List<Order>> getOrders({String? status, int page = 0, int size = 20});

  /// 계약 §3: GET /executions?limit=
  Future<List<Execution>> getExecutions({int limit = 50});
}

class OrdersRepositoryImpl implements OrdersRepository {
  OrdersRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<List<Order>> getOrders({
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    final data = await _api.get('/orders', query: {
      if (status != null) 'status': status,
      'page': page,
      'size': size,
    });
    return (data as List)
        .map((e) => Order.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  @override
  Future<List<Execution>> getExecutions({int limit = 50}) async {
    final data = await _api.get('/executions', query: {'limit': limit});
    return (data as List)
        .map((e) => Execution.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }
}

final ordersRepositoryProvider = Provider<OrdersRepository>(
  (ref) => OrdersRepositoryImpl(ref.watch(apiClientProvider)),
);
