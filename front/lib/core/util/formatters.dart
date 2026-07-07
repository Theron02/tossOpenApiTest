import 'package:intl/intl.dart';

/// 금액·손익·수익률·시각 포맷터.
///
/// 금액은 계약(§0)상 문자열(decimal)로 오며, 부동소수점 계산을 피하기 위해
/// **문자열을 직접 가공**해 천단위 구분을 넣는다(원 표기는 소수부 절삭).
class AppFormat {
  const AppFormat._();

  /// "9500000" -> "9,500,000원"  /  "-3000" -> "-3,000원"
  static String won(String? raw) {
    final parsed = _split(raw);
    if (parsed == null) return '-';
    final (neg, intPart, _) = parsed;
    return '${neg ? '-' : ''}${_group(intPart)}원';
  }

  /// 부호 없는 천단위 숫자. "20000" -> "20,000" (원 표기 없음)
  static String number(String? raw) {
    final parsed = _split(raw);
    if (parsed == null) return '-';
    final (neg, intPart, frac) = parsed;
    final base = '${_group(intPart)}$frac';
    return neg ? '-$base' : base;
  }

  /// 손익용 부호 포함. "+20,000" / "-3,000" / "0"
  static String signed(String? raw) {
    final sign = pnlSign(raw);
    final body = number(raw?.replaceFirst('-', ''));
    if (body == '-') return '-';
    if (sign > 0) return '+$body';
    if (sign < 0) return '-$body';
    return body;
  }

  /// 수익률. 계약상 percent 값("0.2" = 0.2%). "0.2" -> "+0.2%"
  static String percent(String? raw, {bool withSign = true}) {
    if (raw == null || raw.trim().isEmpty) return '-';
    final sign = pnlSign(raw);
    var value = raw.trim();
    if (value.startsWith('-')) value = value.substring(1);
    final prefix = !withSign ? '' : (sign > 0 ? '+' : (sign < 0 ? '-' : ''));
    return '$prefix$value%';
  }

  /// 손익 방향: 1(상승)/-1(하락)/0. 색상 결정에 사용.
  static int pnlSign(String? raw) {
    if (raw == null || raw.trim().isEmpty) return 0;
    final t = raw.trim();
    final neg = t.startsWith('-');
    final digits = t.replaceAll(RegExp(r'[^0-9]'), '');
    final isZero = digits.isEmpty || RegExp(r'^0+$').hasMatch(digits);
    if (isZero) return 0;
    return neg ? -1 : 1;
  }

  /// UTC ISO-8601(Instant) -> KST 표시. 예: "2026-07-04T05:00:00Z" -> "2026-07-04 14:00"
  static String kst(String? isoUtc, {String pattern = 'yyyy-MM-dd HH:mm'}) {
    if (isoUtc == null || isoUtc.isEmpty) return '-';
    final parsed = DateTime.tryParse(isoUtc);
    if (parsed == null) return '-';
    final seoul = parsed.toUtc().add(const Duration(hours: 9));
    return DateFormat(pattern).format(seoul);
  }

  // ── 내부 ──────────────────────────────────────────────

  /// (음수여부, 정수부, 소수부(".xx" 또는 "")) 로 분해. 유효하지 않으면 null.
  static (bool, String, String)? _split(String? raw) {
    if (raw == null) return null;
    final t = raw.trim();
    if (t.isEmpty) return null;
    final neg = t.startsWith('-');
    final s = neg ? t.substring(1) : t;
    final dot = s.indexOf('.');
    var intPart = dot >= 0 ? s.substring(0, dot) : s;
    final frac = dot >= 0 ? s.substring(dot) : '';
    if (!RegExp(r'^\d+$').hasMatch(intPart)) return null;
    intPart = intPart.replaceFirst(RegExp(r'^0+(?=\d)'), '');
    return (neg, intPart, frac);
  }

  static String _group(String digits) {
    final b = StringBuffer();
    final n = digits.length;
    for (var i = 0; i < n; i++) {
      if (i > 0 && (n - i) % 3 == 0) b.write(',');
      b.write(digits[i]);
    }
    return b.toString();
  }
}
