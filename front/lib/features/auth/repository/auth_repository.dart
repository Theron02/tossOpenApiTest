import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/auth_token.dart';

abstract class AuthRepository {
  /// 계약 §2: POST /auth/login. 실패 시 [Failure] (invalid-credentials 등).
  Future<AuthToken> login(String username, String password);
}

class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<AuthToken> login(String username, String password) async {
    final data = await _api.post(
      '/auth/login',
      body: {'username': username, 'password': password},
    );
    return AuthToken.fromJson(Map<String, dynamic>.from(data as Map));
  }
}

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepositoryImpl(ref.watch(apiClientProvider)),
);
