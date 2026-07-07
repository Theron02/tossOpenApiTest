# front — 자동매매 모니터·제어 앱 (Flutter, MVVM)

봇의 상태를 보고 **전략·리스크·kill switch로 안전하게 제어**하는 앱.
매매 판단·주문은 백엔드 봇이 하며, 이 앱은 모니터링·제어만 담당한다(개별 종목 수동 매매 화면 없음).

`back/docs/API_CONTRACT.md` 계약을 기준으로 실제 백엔드에 붙인다.

## 스택
- Flutter 3.x / Dart 3.x
- 상태관리·DI: Riverpod (`AsyncNotifier` 기반 ViewModel)
- 모델: freezed + json_serializable (불변, 서버 DTO ↔ 화면 모델 분리)
- 네트워킹: dio (JWT 인터셉터), 토큰은 flutter_secure_storage
- 라우팅: go_router (인증 상태 기반 redirect)
- 차트: fl_chart (백테스트 equity curve)

## 구조 (MVVM, feature-first)
```
lib/
├── app/                 진입·라우터·테마·config(baseUrl)
├── core/
│   ├── network/         dio·JWT 인터셉터·ApiClient(공통 래퍼 언랩)·TokenStorage
│   ├── error/           Failure (계약 error.code)
│   └── util/            통화·손익·수익률·KST 포맷터
├── shared/              AsyncStateView(로딩/에러/빈), 확인 다이얼로그, PnlText
└── features/<f>/        각 feature 는 model/repository/viewmodel/view 로 구성
    ├── auth/  dashboard/  positions/  orders/  signals/
    ├── strategy/  risk/   backtest/
    └── home/            드로어 네비게이션 셸
```
View → ViewModel(Riverpod) → Repository(추상) → ApiClient(dio). View 에 비즈니스 로직 없음.

## 실행

```bash
# 1) 의존성
flutter pub get

# 2) 코드 생성 (freezed / json_serializable — *.freezed.dart, *.g.dart 생성)
dart run build_runner build --delete-conflicting-outputs

# 3) 실행 (베이스 URL 은 하드코딩하지 않고 --dart-define 주입)
flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1
#   Android 에뮬레이터에서 호스트 localhost 는 10.0.2.2

# 4) 테스트
flutter test
```

> 생성 파일(`*.freezed.dart`, `*.g.dart`)은 `.gitignore` 처리되어 있으므로
> 클론 후 반드시 `build_runner`를 먼저 돌려야 컴파일된다.

## 화면 ↔ 계약 매핑
| 화면 | 소비 API |
|---|---|
| 로그인 | `POST /auth/login` |
| 대시보드 | `GET /portfolio` (+ kill switch 상태 배너) |
| 포지션 | `GET /positions` |
| 주문·체결 | `GET /orders`, `GET /executions` |
| 신호 로그 | `GET /signals` |
| 전략 | `GET/PATCH /strategies` — 활성화 시 확인 다이얼로그 |
| 리스크·제어 | `GET/PATCH /risk-setting`, `POST /risk-setting/kill-switch` (confirm=true) |
| 백테스트 | `POST /backtests` — equity curve 차트 |

## 안전·UX
- **kill switch / 전략 활성화**: 확인 다이얼로그 후에만 API 호출. 낙관적 반영하지 않고
  서버 응답으로 상태 확정. kill switch ON 은 경고색으로 뚜렷이 표시.
- 손익 색상은 국내 관례(상승=빨강, 하락=파랑), 다크모드 대응.
- 모든 목록·조회 화면은 로딩/에러/빈 상태 + 당김 새로고침. 대시보드는 주기 폴링.

## 범위 밖 / 참고
- **참고용 캔들 차트(TASK_07의 선택 항목)는 이번 범위에서 제외했다.**
  `API_CONTRACT.md`에 `/candles` 엔드포인트가 없어 "계약대로" 붙일 근거가 없기 때문이다.
  백엔드가 캔들 조회 API를 노출하면 `features/chart/`로 추가한다.
- 실시간 WebSocket 스트림(백엔드 채널 준비 시 폴링 → 구독 전환).
- 실거래 전환은 백엔드 승인 절차 이후. 앱은 상태 표시만 한다.
