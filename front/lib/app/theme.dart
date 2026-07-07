import 'package:flutter/material.dart';

/// 손익 색상(국내 관례): 상승=빨강, 하락=파랑, 보합=회색. 다크모드에서도 동일.
Color pnlColor(int sign) {
  if (sign > 0) return const Color(0xFFE53935); // red
  if (sign < 0) return const Color(0xFF1E88E5); // blue
  return const Color(0xFF9E9E9E); // grey
}

const Color warningColor = Color(0xFFEF6C00); // kill switch ON 등 경고

class AppTheme {
  const AppTheme._();

  static ThemeData light() => _base(Brightness.light);
  static ThemeData dark() => _base(Brightness.dark);

  static ThemeData _base(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF3D5AFE),
      brightness: brightness,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      appBarTheme: const AppBarTheme(centerTitle: false),
      cardTheme: CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        clipBehavior: Clip.antiAlias,
      ),
    );
  }
}
