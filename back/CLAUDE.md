# CLAUDE.md — 백엔드 (Spring Boot + Kotlin, MVC)

루트 `../CLAUDE.md`의 도메인·아키텍처를 전제로 한다. 이 문서는 백엔드 구현 규칙이다.

---

## 1. 스택 / 빌드

- Kotlin 1.9+, Spring Boot 3.x, Spring MVC, JDK 21
- Gradle Kotlin DSL (`build.gradle.kts`)
- 주요 의존성: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-websocket`, `spring-boot-starter-validation`,
  `spring-boot-starter-data-redis`, `jjwt`, PostgreSQL 드라이버
- 테스트: JUnit5, MockK, `spring-boot-starter-test`

---

## 2. MVC 레이어드 아키텍처

요청 흐름은 **Controller → Service → Repository** 단방향이다. 역방향 의존 금지.

```
Controller   HTTP 경계. 요청 검증, DTO 변환, 응답 코드. 비즈니스 로직 없음.
   │
Service      트랜잭션 경계. 비즈니스 로직. 도메인 객체 조율.
   │
Repository   영속화. JPA. 쿼리.
```

트레이딩 엔진 컴포넌트(엔진/전략/리스크/실행기)는 Service 계층 하위의
`domain` 패키지에 둔다. 이들은 스케줄러·이벤트로 구동되며 Controller를 거치지 않는다.

### 패키지 구조
```
com.autotrading
├── config/            Spring 설정 (Web, Security, Redis, Scheduler, WebSocket)
├── controller/        REST 컨트롤러 + 요청/응답 DTO
├── service/           비즈니스 서비스 (@Service, @Transactional)
├── domain/
│   ├── strategy/      TradingStrategy 인터페이스 + 구현체
│   ├── indicator/     지표 계산기 (MA, RSI, Bollinger)
│   ├── engine/        StrategyEngine, 판단 루프
│   ├── risk/          RiskManager, RiskGuard 규칙
│   └── order/         OrderExecutor, 주문 상태머신
├── repository/        Spring Data JPA 인터페이스
├── entity/            JPA 엔티티
├── external/
│   └── kis/           KIS API 클라이언트 (REST + WebSocket), 토큰 관리
├── scheduler/         장 운영시간·토큰 갱신 스케줄러
└── common/            공통 (예외, 응답 래퍼, 유틸, 상수)
```

---

## 3. 코딩 규칙

- **불변 우선**: `val` 기본, `var`는 정말 필요할 때만. DTO·엔티티 필드는 가능한 한 불변.
- **data class**로 DTO 정의. 요청/응답 DTO는 엔티티와 분리한다 (엔티티 직접 노출 금지).
- **null 안전**: 플랫폼 타입 경계(외부 API)에서 명시적으로 nullable 처리.
- **금액·수량**: `BigDecimal` 또는 정수(원). `Double`/`Float`로 금전 계산 금지.
- **확장 함수**로 변환 로직 정리 (`Entity.toDto()`, `Dto.toEntity()`).
- **sealed class / enum**으로 상태 표현 (`Signal`, `OrderStatus` 등).
- 코루틴은 외부 I/O(KIS 호출) 비동기 처리에만 신중히 사용. 트랜잭션 경계와 섞지 않는다.

---

## 4. 전략 엔진 — 핵심 설계

전략은 반드시 인터페이스로 추상화한다. 규칙 기반으로 시작하고, 추후 ML 전략을
같은 인터페이스로 끼운다.

```kotlin
interface TradingStrategy {
    val name: String
    fun evaluate(context: MarketContext): Signal  // BUY / SELL / HOLD
}
```

- `MarketContext`: 현재가, 최근 캔들, 계산된 지표, 현재 포지션을 담은 불변 입력.
- 구현체 예: `GoldenCrossStrategy`, `RsiStrategy`, `BollingerStrategy`.
- `StrategyEngine`은 활성 전략을 평가해 `Signal`을 산출하고, **주문 직전 반드시
  `RiskManager.check()`를 통과**시킨 뒤에만 `OrderExecutor`를 호출한다.
- ML 모델 전략을 추가할 경우에도 엔진 코드는 수정하지 않는다 (전략 패턴 유지).

---

## 5. 주문·리스크 (가장 민감한 영역)

- **모든 주문은 `RiskManager`를 경유**한다. 우회 경로 절대 금지.
- `RiskManager`가 막는 것: 일일 누적 손실 한도, 종목당 비중 한도, 잔고 부족,
  중복 주문, kill switch 활성 상태.
- **멱등성**: 주문 요청에 멱등키를 부여하고 Redis로 중복 실행을 차단한다.
- **트랜잭션**: 잔고 차감 + 주문 기록 + 포지션 갱신은 하나의 `@Transactional`로 원자화.
- **상태머신**: `PENDING → FILLED / PARTIAL / CANCELLED / REJECTED`.
  KIS 체결 폴링으로 상태를 갱신하며, 불법 전이를 막는다.
- **모의/실거래 분리**: `OrderExecutor`는 인터페이스로 두고
  `PaperOrderExecutor`(기본) / `LiveOrderExecutor`를 구현. 주입은 설정 플래그로 제어.
  실거래 구현·활성화는 사용자의 명시적 승인 없이는 작성하지 않는다.

---

## 6. KIS 연동

- 앱키/시크릿은 환경변수로만. 코드·VCS에 절대 포함하지 않는다.
- **토큰**: KIS 액세스 토큰(유효기간 있음)은 Redis에 캐싱하고 스케줄러로 사전 갱신.
  만료 임박 시 재발급. 토큰 발급 호출을 매 요청마다 하지 않는다.
- **시세 WebSocket**: KIS 구독을 백엔드 단일 커넥션으로 받고, 앱에는 백엔드가 중계.
  앱이 KIS에 직접 붙지 않는다 (키 노출·연결 수 관리).
- KIS 응답 스키마가 불확실하면 추측 금지. 실제 응답 예시를 사용자에게 요청한다.
- 외부 호출은 타임아웃·재시도·서킷브레이커를 적용하고 실패를 명시적으로 처리한다.

---

## 7. API 설계

- REST 경로: `/api/v1/...`. 동사 대신 자원 중심.
- 응답은 공통 래퍼로 일관화 (`data`, `error`, `timestamp`).
- 검증 실패·도메인 예외는 `@RestControllerAdvice`에서 일괄 처리, 명확한 에러 코드 반환.
- 실시간 푸시(체결·신호·손익)는 WebSocket/STOMP 채널로.

---

## 8. 테스트

- 손익·수수료·평단가 계산, 리스크 가드, 주문 상태 전이는 **반드시 단위 테스트**.
- 전략은 고정된 캔들 픽스처로 신호 산출을 검증한다 (결정론적 테스트).
- KIS 클라이언트는 MockK로 외부 호출을 격리한다.
- 통합 테스트는 Testcontainers(PostgreSQL/Redis) 권장.

---

## 9. Claude 작업 지침 (백엔드)

- 새 지표/전략은 해당 인터페이스 구현 + 테스트 픽스처를 함께 생성한다.
- 주문을 만드는 코드에서 `RiskManager` 호출을 빠뜨리지 않는다.
- 엔티티를 컨트롤러 응답에 직접 노출하지 않는다. 항상 DTO 변환.
- 금전 계산에 `Double`을 쓰지 않는다.
- 실거래 관련 코드는 명시적 승인 전까지 작성하지 않는다.