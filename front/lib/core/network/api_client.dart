import 'package:dio/dio.dart';

import '../error/failure.dart';
import 'token_storage.dart';

/// 매 요청에 JWT 를 주입하는 인터셉터. 401 이면 캐시 토큰을 정리한다.
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._tokens);

  final TokenStorage _tokens;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = _tokens.token;
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response?.statusCode == 401) {
      // 계약 §2: 토큰 만료/무효. 재발급 엔드포인트가 없으므로 재로그인 유도.
      _tokens.clear();
    }
    handler.next(err);
  }
}

/// 계약 §1 공통 응답 래퍼({ data, error, timestamp })를 벗겨 data 만 돌려준다.
/// 실패는 모두 [Failure] 로 정규화한다.
class ApiClient {
  ApiClient(this._dio);

  final Dio _dio;

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) =>
      _run(() => _dio.get(path, queryParameters: query));

  Future<dynamic> post(String path, {Object? body}) =>
      _run(() => _dio.post(path, data: body));

  Future<dynamic> patch(String path, {Object? body}) =>
      _run(() => _dio.patch(path, data: body));

  Future<dynamic> _run(Future<Response> Function() call) async {
    try {
      final res = await call();
      return _unwrap(res.data);
    } on DioException catch (e) {
      throw _toFailure(e);
    }
  }

  dynamic _unwrap(dynamic body) {
    if (body is Map) {
      final error = body['error'];
      if (error is Map) {
        throw Failure(
          (error['code'] ?? 'internal-error').toString(),
          (error['message'] ?? '').toString(),
        );
      }
      if (body.containsKey('data')) return body['data'];
    }
    return body;
  }

  Failure _toFailure(DioException e) {
    final data = e.response?.data;
    if (data is Map && data['error'] is Map) {
      final error = data['error'] as Map;
      return Failure(
        (error['code'] ?? 'internal-error').toString(),
        (error['message'] ?? '').toString(),
      );
    }
    if (e.type == DioExceptionType.connectionError ||
        e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout) {
      return const Failure('network', '');
    }
    return Failure('http-${e.response?.statusCode ?? 0}', e.message ?? '');
  }
}
