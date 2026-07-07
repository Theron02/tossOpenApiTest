import 'package:auto_trading/core/util/formatters.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppFormat.won', () {
    test('천단위 구분 + 원 표기, 소수부 절삭', () {
      expect(AppFormat.won('9500000'), '9,500,000원');
      expect(AppFormat.won('520000'), '520,000원');
      expect(AppFormat.won('0'), '0원');
      expect(AppFormat.won('55000.50'), '55,000원');
    });

    test('음수', () {
      expect(AppFormat.won('-3000'), '-3,000원');
    });

    test('null/빈값', () {
      expect(AppFormat.won(null), '-');
      expect(AppFormat.won(''), '-');
    });
  });

  group('AppFormat.signed', () {
    test('부호 표시', () {
      expect(AppFormat.signed('20000'), '+20,000');
      expect(AppFormat.signed('-3000'), '-3,000');
      expect(AppFormat.signed('0'), '0');
    });
  });

  group('AppFormat.percent', () {
    test('percent 값에 % 부호와 방향 부호', () {
      expect(AppFormat.percent('0.2'), '+0.2%');
      expect(AppFormat.percent('-5.77'), '-5.77%');
      expect(AppFormat.percent('0'), '0%');
    });

    test('withSign=false 면 방향 부호 없음(MDD·승률)', () {
      expect(AppFormat.percent('0.15', withSign: false), '0.15%');
    });
  });

  group('AppFormat.pnlSign', () {
    test('방향 판별', () {
      expect(AppFormat.pnlSign('100'), 1);
      expect(AppFormat.pnlSign('-100'), -1);
      expect(AppFormat.pnlSign('0'), 0);
      expect(AppFormat.pnlSign('0.00'), 0);
      expect(AppFormat.pnlSign(null), 0);
    });
  });

  group('AppFormat.kst', () {
    test('UTC → KST(+9h) 변환', () {
      expect(AppFormat.kst('2026-07-04T05:00:00Z'), '2026-07-04 14:00');
    });

    test('잘못된 값', () {
      expect(AppFormat.kst(null), '-');
      expect(AppFormat.kst('not-a-date'), '-');
    });
  });
}
