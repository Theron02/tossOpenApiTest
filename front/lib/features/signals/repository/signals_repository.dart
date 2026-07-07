import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/signal.dart';

abstract class SignalsRepository {
  Future<List<SignalLog>> getSignals({int page = 0, int size = 20});
}

class SignalsRepositoryImpl implements SignalsRepository {
  SignalsRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<List<SignalLog>> getSignals({int page = 0, int size = 20}) async {
    final data = await _api.get('/signals', query: {'page': page, 'size': size});
    return (data as List)
        .map((e) => SignalLog.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }
}

final signalsRepositoryProvider = Provider<SignalsRepository>(
  (ref) => SignalsRepositoryImpl(ref.watch(apiClientProvider)),
);
