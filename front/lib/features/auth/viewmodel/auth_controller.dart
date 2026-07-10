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
    final tokens = ref.read(tokenStorageProvider);
    // 인터셉터가 401(만료·무효 토큰)로 토큰을 지우면 미인증으로 전환해
    // 라우터가 로그인 화면으로 보내게 한다.
    void onTokenChange() {
      if (tokens.token == null && state.valueOrNull == true) {
        state = const AsyncData(false);
      }
    }

    tokens.addListener(onTokenChange);
    ref.onDispose(() => tokens.removeListener(onTokenChange));
    return tokens.token != null;
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
