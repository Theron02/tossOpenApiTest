/// 앱 전역 설정. 베이스 URL 은 하드코딩하지 않고 --dart-define 으로 주입한다.
///
///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
///
/// (안드로이드 에뮬레이터에서 호스트 localhost 는 10.0.2.2)
class AppConfig {
  const AppConfig._();

  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );

  /// 대시보드 등 주기 폴링 간격(초). 실시간 WebSocket 도입 전까지 폴링으로 대체.
  static const int pollIntervalSeconds = 10;
}
