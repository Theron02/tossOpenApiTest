import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../model/execution.dart';
import '../model/order.dart';
import '../repository/orders_repository.dart';

/// 주문 상태 필터. null = 전체. 값이 바뀌면 OrdersController 가 재조회된다.
final orderStatusFilterProvider = StateProvider<String?>((_) => null);

class OrdersController extends AsyncNotifier<List<Order>> {
  @override
  Future<List<Order>> build() {
    final status = ref.watch(orderStatusFilterProvider);
    return ref.read(ordersRepositoryProvider).getOrders(status: status);
  }

  Future<void> refresh() async {
    final status = ref.read(orderStatusFilterProvider);
    state = const AsyncLoading<List<Order>>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(ordersRepositoryProvider).getOrders(status: status),
    );
  }
}

final ordersControllerProvider =
    AsyncNotifierProvider<OrdersController, List<Order>>(OrdersController.new);

class ExecutionsController extends AsyncNotifier<List<Execution>> {
  @override
  Future<List<Execution>> build() {
    return ref.read(ordersRepositoryProvider).getExecutions();
  }

  Future<void> refresh() async {
    state = const AsyncLoading<List<Execution>>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => ref.read(ordersRepositoryProvider).getExecutions(),
    );
  }
}

final executionsControllerProvider =
    AsyncNotifierProvider<ExecutionsController, List<Execution>>(
        ExecutionsController.new);
