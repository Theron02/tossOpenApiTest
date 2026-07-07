import 'package:flutter/material.dart';

import '../app/theme.dart';
import '../core/util/formatters.dart';

/// 손익 금액/수익률을 국내 관례 색상으로 표시한다.
class PnlText extends StatelessWidget {
  const PnlText({
    super.key,
    required this.amount,
    this.rate,
    this.style,
  });

  /// 손익 금액(원 문자열). 부호·색상 자동.
  final String? amount;

  /// 수익률(percent 문자열). 있으면 "(+1.2%)" 형태로 덧붙임.
  final String? rate;

  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    final sign = AppFormat.pnlSign(amount);
    final color = pnlColor(sign);
    final text = rate == null
        ? AppFormat.signed(amount)
        : '${AppFormat.signed(amount)} (${AppFormat.percent(rate)})';
    return Text(
      text,
      style: (style ?? const TextStyle()).copyWith(
        color: color,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}
