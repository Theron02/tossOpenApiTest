import 'package:freezed_annotation/freezed_annotation.dart';

part 'risk_setting.freezed.dart';
part 'risk_setting.g.dart';

/// 계약 §3/§4: GET/PATCH /risk-setting, POST /risk-setting/kill-switch.
@freezed
class RiskSetting with _$RiskSetting {
  const factory RiskSetting({
    required String id,
    required String accountId,
    required String dailyLossLimit,
    required int maxPositionPct,
    @Default(false) bool killSwitch,
  }) = _RiskSetting;

  factory RiskSetting.fromJson(Map<String, dynamic> json) =>
      _$RiskSettingFromJson(json);
}
