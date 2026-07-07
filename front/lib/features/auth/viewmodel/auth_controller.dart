import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../repository/auth_repository.dart';

/// 인증 상태를 AsyncValue<bool> 로 노출한다.
///  - loading : 확인/로그인 진행 중
///  - data(true)  : 인증됨
///  - data(false) : 미인증(로그아웃 포함)
///  - error   : 로그인 실패 (Failure)
class AuthController extends AsyncNotifier<bool> {
  @override
  Future<bool> build() async {
    return ref.read(tokenStorageProvider).token != null;
  }

  Future<void> login(String username, String password) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final token = await ref.read(authRepositoryProvider).login(
            username.trim(),
            password,
          );
      await ref.read(tokenStorageProvider).save(token.token);
      return true;
    });
  }

  Future<void> logout() async {
    await ref.read(tokenStorageProvider).clear();
    state = const AsyncData(false);
  }
}

final authControllerProvider =
    AsyncNotifierProvider<AuthController, bool>(AuthController.new);
