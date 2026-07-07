import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/view/login_view.dart';
import '../features/auth/viewmodel/auth_controller.dart';
import '../features/home/home_shell.dart';

/// authControllerProvider 상태가 바뀔 때 go_router 를 재평가하기 위한 Listenable.
class _AuthRefresh extends ChangeNotifier {
  _AuthRefresh(Ref ref) {
    ref.listen(authControllerProvider, (_, __) => notifyListeners());
  }
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _AuthRefresh(ref);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: '/',
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      // 확인 중(loading)에는 리다이렉트하지 않는다.
      if (auth.isLoading && !auth.hasValue) return null;
      final loggedIn = auth.valueOrNull == true;
      final loggingIn = state.matchedLocation == '/login';

      if (!loggedIn) return loggingIn ? null : '/login';
      if (loggingIn) return '/';
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (_, __) => const LoginView()),
      GoRoute(path: '/', builder: (_, __) => const HomeShell()),
    ],
  );
});
