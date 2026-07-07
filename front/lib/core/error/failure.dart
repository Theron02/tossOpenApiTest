/// 계약(API_CONTRACT.md §1)의 error.code 를 그대로 담는 실패 타입.
/// Repository/DataSource 경계에서 발생한 예외를 이 타입으로 정규화해 ViewModel 로 올린다.
class Failure implements Exception {
  const Failure(this.code, this.message);

  final String code;
  final String message;

  bool get isUnauthorized =>
      code == 'unauthorized' || code == 'invalid-credentials';

  /// 사용자에게 보여줄 메시지. 계약 코드별로 한국어 안내를 덧붙인다.
  String get userMessage {
    switch (code) {
      case 'invalid-credentials':
        return '아이디 또는 비밀번호가 올바르지 않습니다.';
      case 'unauthorized':
        return '세션이 만료되었습니다. 다시 로그인해 주세요.';
      case 'validation-failed':
      case 'bad-request':
        return message.isEmpty ? '입력값을 확인해 주세요.' : message;
      case 'risk-rejected':
        return message.isEmpty ? '리스크 가드에 의해 거부되었습니다.' : message;
      case 'network':
        return '네트워크에 연결할 수 없습니다. 연결 상태를 확인해 주세요.';
      default:
        return message.isEmpty ? '요청 처리 중 오류가 발생했습니다.' : message;
    }
  }

  @override
  String toString() => 'Failure($code, $message)';
}
