import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// JWT 를 안전 저장소에 보관한다. 평문/소스에 저장하지 않는다(front/CLAUDE.md §6).
/// 인터셉터가 매 요청 동기적으로 읽을 수 있도록 메모리에 캐시한다.
class TokenStorage {
  TokenStorage(this._storage);

  static const String _key = 'jwt_token';
  final FlutterSecureStorage _storage;
  String? _cached;

  String? get token => _cached;

  /// 앱 시작 시 1회 호출해 캐시를 채운다.
  Future<void> load() async {
    _cached = await _storage.read(key: _key);
  }

  Future<void> save(String token) async {
    _cached = token;
    await _storage.write(key: _key, value: token);
  }

  Future<void> clear() async {
    _cached = null;
    await _storage.delete(key: _key);
  }
}
