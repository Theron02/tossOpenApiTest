import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_token.freezed.dart';
part 'auth_token.g.dart';

/// 계약 §2: POST /auth/login 응답 data.
@freezed
class AuthToken with _$AuthToken {
  const factory AuthToken({
    required String token,
    @Default('Bearer') String tokenType,
    @JsonKey(name: 'expiresInSeconds') @Default(0) int expiresInSeconds,
  }) = _AuthToken;

  factory AuthToken.fromJson(Map<String, dynamic> json) =>
      _$AuthTokenFromJson(json);
}
