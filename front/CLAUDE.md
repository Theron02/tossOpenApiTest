# CLAUDE.md — 프론트 (Flutter, MVVM)

루트 `../CLAUDE.md`의 도메인·아키텍처를 전제로 한다. 이 문서는 Flutter 앱 구현 규칙이다.

---

## 1. 앱의 역할

이 앱은 **모니터링·제어용**이다. 매매는 백엔드 봇이 한다. 앱은 다음만 담당한다.

- 전략 ON/OFF, 파라미터 설정
- 실시간 손익·체결 내역·신호 표시
- 포트폴리오·잔고 조회, 차트(참고용) 표시
- kill switch 등 긴급 제어

**앱에서 사람이 개별 매매를 직접 결정하지 않는다.** (모의투자 수동 주문 기능은 검증용 옵션으로만, 명확히 분리.)

---

## 2. 스택

- Flutter 3.x, Dart 3.x
- 상태관리/DI: **Riverpod**
- 네트워킹: `dio` (REST), `web_socket_channel` 또는 STOMP 클라이언트 (실시간)
- 모델 직렬화: `freezed` + `json_serializable`
- 차트(참고 표시용): `fl_chart` 또는 `candlesticks`
- 라우팅: `go_router`

---

## 3. MVVM 구조

```
View (Widget)          UI만. 상태를 그리고 사용자 입력을 ViewModel로 전달.
   │  watch / read
ViewModel              화면 상태 보유·변형. UseCase/Repository 호출. UI 로직.
   │
Repository             데이터 출처 추상화. Remote(API)·Local(캐시) 조합.
   │
DataSource             실제 I/O. REST 클라이언트, WebSocket, 로컬 저장소.
```

- **View는 비즈니스 로직을 갖지 않는다.** 분기·계산은 ViewModel에.
- ViewModel은 Riverpod `Notifier`/`AsyncNotifier`로 구현, 상태는 불변 모델로 노출.
- View는 `ref.watch`로 상태 구독, 이벤트는 `ref.read(...).method()`로 호출.
- Repository는 인터페이스로 두고 구현을 분리 (테스트 시 mock 주입).

### 디렉토리 구조 (feature-first)
```
lib/
├── main.dart
├── app/                 앱 진입, 라우터, 테마, 전역 provider
├── core/
│   ├── network/         dio 설정, 인터셉터(JWT), WebSocket 클라이언트
│   ├── error/           Failure 타입, 예외 매핑
│   └── util/            포맷터(통화·손익), 상수
├── features/
│   ├── dashboard/       손익·포지션 요약
│   │   ├── view/
│   │   ├── viewmodel/
│   │   ├── model/
│   │   └── repository/
│   ├── strategy/        전략 ON/OFF·파라미터 설정
│   ├── chart/           참고용 차트 표시
│   ├── orders/          체결·주문 내역
│   └── auth/            로그인(JWT)
└── shared/              공통 위젯, 모델
```

---

## 4. 상태·모델 규칙

- 모델은 `freezed`로 **불변** 정의. `copyWith`로만 변형.
- 화면 상태는 명시적 상태 타입으로 표현: `loading / data / error` (sealed union 권장).
- 서버 DTO와 화면 모델을 분리. 매핑은 Repository에서 수행.
- 금액·손익은 정수(원)/`Decimal`로 다루고, 표시 단계에서만 포맷팅. 계산에 `double` 지양.

---

## 5. 실시간 데이터

- 손익·체결·신호는 백엔드 WebSocket 채널을 구독해 받는다.
- WebSocket 스트림은 Repository에서 노출하고, ViewModel이 구독해 상태로 반영.
- 연결 끊김 시 자동 재연결·백오프. 끊긴 동안의 상태를 UI에 명확히 표시.
- 실시간 채널과 REST 조회를 일관된 모델로 합류시킨다 (중복·순서 처리).

---

## 6. 네트워킹·인증

- 모든 REST는 `dio` 인스턴스 1개 + 인터셉터로 JWT 주입.
- 토큰 만료 시 재발급·재시도 인터셉터. 실패 시 로그인으로 라우팅.
- 비밀값(토큰)은 안전 저장소(`flutter_secure_storage`)에. 평문·소스에 저장 금지.
- API 베이스 URL은 환경별 설정(`--dart-define`)으로 주입. 하드코딩 금지.

---

## 7. UI / UX

- 로딩·에러·빈 상태를 항상 처리. 무한 스피너·무응답 화면 금지.
- 긴급 제어(kill switch)는 오작동 방지를 위해 확인 다이얼로그를 둔다.
- 손익은 색상(상승/하락)으로 즉시 구분. 국내 관례(상승=빨강, 하락=파랑) 따름.
- 다크모드 대응.

---

## 8. 테스트

- ViewModel 로직은 단위 테스트 (상태 전이, Repository mock).
- 위젯 테스트로 주요 화면의 상태별 렌더링 검증.
- 손익·통화 포맷터는 단위 테스트 필수.

---

## 9. Claude 작업 지침 (프론트)

- 새 화면은 feature 단위로 view/viewmodel/model/repository를 함께 생성한다.
- View에 비즈니스 로직(분기·계산·네트워크 호출)을 넣지 않는다. ViewModel에 둔다.
- 모델은 `freezed` 불변으로. 서버 DTO를 화면에 직접 쓰지 않는다.
- 앱은 모니터링·제어 역할임을 전제로 한다. 자동 매매 의사결정 로직을 앱에 두지 않는다.
- 비밀값을 평문 저장하거나 소스에 포함하지 않는다.