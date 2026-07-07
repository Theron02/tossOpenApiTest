import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'core/network/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 시작 시 저장된 JWT 를 캐시에 로드해 인터셉터가 동기적으로 사용하도록 한다.
  final container = ProviderContainer();
  await container.read(tokenStorageProvider).load();

  runApp(
    UncontrolledProviderScope(
      container: container,
      child: const AutoTradingApp(),
    ),
  );
}
