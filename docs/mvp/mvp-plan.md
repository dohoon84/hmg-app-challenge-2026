# 미리(Miri) MVP 개발 계획 — Phase 1

- 작성: 2026-10-24(토) 13:35 KST · **개정 13:45 KST: CTO 결정 5건(13:37) + 대표님 패키지 결정(13:40) 반영 / 13:47 KST: CTO 결정 4건 추가 — §7-0** · 근거: 대표님 MVP 개발 승인(10/24 13:23 KST), CTO 콘솔 SDK 신청 목록(9개 항목, 10/24 13:31 KST 전달)
- 기준 문서: u1-rental-screens-brief.md v1.20(§A~F) · u1-context-signals.md v1.17 · mockups/u1-rental/v4/ · navi-event-check.md §9~10 · voice-check.md §3-d(29클립판, 13:40 2차: G0 → 고속도로 진입 H1)
- 기준 코드: `dohoon84/hmg-app-challenge-2026` develop **41ad71b**(PR #5 squash 머지본). 프로브는 PR #6(머지 안 함, 참고만)
- 표기: ✅ 원문·실측 근거 있음 / [해석] 원문에 직접 문장은 없고 근거로 판단함 / **미확인** 근거 없음(추측하지 않음)
- 보안: 비밀값은 이 문서에 하나도 적지 않았습니다. 변수 **이름**만 적습니다(§4).

---

## 0. 한눈에 보기

| 항목 | 내용 |
|---|---|
| Phase 1 범위 | F1→F2 대여 시작(P) · D1 '오늘 길' · D2 충전소(EV, D2 공통 조건·빈 값 규칙) · F3 반납 준비(카드 전용) · C/F4 반납 점검(자동 체크: 문·창문·**실내등·독서등**) · **B 주행 중 예외 1: 방향지시등 음성(데모 0:43, CTO 결정)** · E1 우리 앱 영역(주행 중 아이콘만) |
| 데모 | 약 90초 흐름을 Pleos 에뮬레이터에서 실행. 주행 시뮬레이션이 없어 D2-b·반납 장소 도착 장면은 **녹화 재생 + 자막 '시뮬레이션'** |
| 콘솔 SDK 신청 | CTO 목록 9개 = Vehicle State 7 + NaviHelper 2. **MVP가 쓰는 것 7개**(도어, 창문, EV·연료, 라이트, 좌석, 주행 정보, 경로 정보 조회) + 조건부 2개(경로 탐색 설정 = debug 전용, 차량 제원 = Pleos API 미사용·AAOS CAR_INFO 때문) — §1 |
| 가장 큰 미확인 | **유종/충전구 위치·커넥터(`INFO_*`)는 Pleos Vehicle SDK에 API가 없고 AAOS `android.car.permission.CAR_INFO`로만 읽힙니다.** 기어는 AAOS `CAR_POWERTRAIN`으로 읽되(CTO 결정), **Vehicle 2.3.2 AAR에 문서에 없는 Pleos `Gear` API(CAR_DRIVE)가 있어 대체 경로가 있습니다**(13:45 AAR 확인). AAOS 권한이 9개 항목에 포함되는지는 **미확인** → 콘솔 신청서 설명란에 명시 + Pleos 문의(§1-3) |
| 첫 에뮬레이터 빌드 ETA | 클라우드 에이전트 작업 약 31시간 + 에뮬레이터 확인 1시간. **가장 이른 시점 10/25(일) 22:00 KST, 현실적 10/26(월) 13:00 KST** [가정: 오늘 15:00 KST 착수, 에이전트 2개 병렬, 대표님 Mac 스왑 여유] — §5 |

---

## 1. SDK·권한 표 (콘솔 SDK 탭 9개 항목 기준)

### 1-1. 근거
- **콘솔 상태 규칙 ✅** (docs/txt `create-my-project`): "Vehicle State와 NaviHelper SDK가 이 상태(사용 가능)에 해당합니다. 이 두 SDK는 신청 없이 바로 사용 가능합니다." FusedLocation·TextToSpeech·SpeechToText·Vehicle Control은 '준비 중'(Coming Soon, 신청 불가). → 문서상 사전 승인 불필요. 다만 **CTO가 기능 항목별 신청 목록을 받았으므로 실제 콘솔은 항목별 신청 화면이 있는 것으로 보이며, 문서(크롤 시점) 이후 바뀌었을 수 있습니다. 항목별 승인 필요 여부·심사 기간은 미확인**입니다. 레포 pleos 매니페스트 주석도 "승인 목록은 10/27 이후 공개"라고 적혀 있습니다.
- **9개 항목 ↔ Pleos 권한 대응 [해석]:** Vehicle 권한 문서(docs/txt `Vehicle__vehicle-permission`) ✅의 조회 도메인은 정확히 11개입니다 — Door=CAR_DOORS, Window·Wiper=CAR_WINDOWS, HVAC=CAR_CLIMATE, DrivingMode·Brake·Odometer=CAR_DRIVE, EvBattery·Fuel=CAR_ENERGY, CarInfo·Display=CAR_INFO, Light·TurnSignal=CAR_LIGHTS, SideMirror=CAR_MIRRORS, Safety·Tire=CAR_SAFETY, Seat=CAR_SEATS, Toll=CAR_TOLL. CTO 목록(포함 7 + 제외 공조·사이드미러·차량 안전·통행료 4)이 이 11개와 1:1로 맞습니다. 콘솔 화면의 한국어 이름과 권한 상수의 공식 대응표는 문서에서 찾지 못해 **[해석]**입니다.
- **NaviHelper ✅** (docs/txt `NaviHelper__declaring-permissions` + API 레퍼런스 `@RequiresPermission`): `pleos.car.permission.NAVI_ROUTE` = `get*Info()`, `requestTrip()` / `pleos.car.permission.NAVI_ROUTE_SEARCH` = `requestRoute()`, `requestRouteWithWaypoints()`, `cancelRoute()`, `requestReRoute()`, `addWaypoint()`, `removeWaypoint()`, `changeRouteOption()` / `NAVI_CUSTOM_MAP` = `requestNavigationScreen()`. 개별 레퍼런스: `requestTrip` → NAVI_ROUTE "Protection level: Normal" ✅, `getTBTInfo` → NAVI_ROUTE ✅, `getCurrentLocationInfo` → NAVI_ROUTE ✅.
- **보호 수준 ✅:** 에뮬레이터 `pm list permissions -f` 실측(evidence/navi/perms_pleos_all.txt, perms_selected.txt)과 Car 서비스 매니페스트(carsvc_manifest.xml). SDK AAR 매니페스트(Vehicle 2.3.2, NaviHelper 3.0.5)에는 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`(normal) 하나만 선언되어 있고 기능 권한은 앱이 직접 선언합니다.
- **Pleos Vehicle SDK에 없는 값 ✅** (vehicle_api_summary.txt 458행 전체 API 목록 + 13:45 Vehicle-2.3.2.aar `classes.jar` 문자열 확인): 유종, 주유구·충전구 위치, 커넥터 종류, 차속. → AAOS `CarPropertyManager`로만 읽습니다(레포 develop 코드도 같은 방식).
- **AAR에는 있지만 문서 목록에 없는 API ✅(AAR 확인, 동작은 미확인):** `Gear.getCurrentGearSelectionState`/`registerGearSelection` (`@RequiresPermission pleos.car.permission.CAR_DRIVE`), `Odometer.getRangeRemainingDistance`/`registerRangeRemainingListener` (CAR_DRIVE), `EvBattery.registerEvChargePortOpen`. develop `PleosCarClient` 주석도 "`Vehicle.getGear()` exists on the 2.3.2 AAR … This class does not call it"라고 적고 기어는 AAOS로 읽습니다. → 기어는 **AAOS 기본 + Pleos Gear 대체 후보**, 남은 주행거리는 Pleos Odometer 후보(에뮬레이터 값 미확인).
- **develop 41ad71b 코드가 읽는 값 ✅** (`PleosVehicleRepository.kt`): 도어(`registerDoorPosition`), **창문(`subscribeWindows` → `registerWindowPosition`/`getCurrentWindowPosition`)**, 연료 %(`getFuelPercent`), SOC(`getEvBatteryStateOfCharge`), **주행거리(`registerDistanceDriven`, CAR_DRIVE)**, 좌석(capability 조회). `PleosCarClient.kt`: AAOS `GEAR_SELECTION`(CAR_POWERTRAIN), `RANGE_REMAINING`(android CAR_ENERGY — 권한 확인 후에만 읽음).

### 1-2. 콘솔 9개 항목 표

| # | 콘솔 항목 (CTO 목록) | 대응 Android 권한 (보호 수준) | MVP에서 쓰는 API·프로퍼티 | 화면 | MVP 사용 | 콘솔 신청 |
|---|---|---|---|---|---|---|
| V1 | 차량 제원 정보 조회 | `pleos.car.permission.CAR_INFO` (normal) [해석] | Pleos `CarInfo` — MVP 미사용(develop 매니페스트에 있음, 유지) / **AAOS `android.car.permission.CAR_INFO`(normal) 선언 — CTO 결정**: `INFO_FUEL_TYPE`·`INFO_FUEL_DOOR_LOCATION`·`INFO_EV_PORT_LOCATION`·`INFO_EV_CONNECTOR_TYPE` | D2-a/b, F3/F4 문구 구분 | ✅ (AAOS) | **신청.** AAOS 권한이 이 항목에 포함되는지 **미확인** → 설명란 명시 |
| V2 | 차량 주행 정보 조회 | `pleos.car.permission.CAR_DRIVE` (normal) [해석] | Pleos `registerDistanceDriven`(develop 코드가 이미 읽음) + 대체 후보 `Gear`·`getRangeRemainingDistance`(AAR 확인) / **AAOS `android.car.permission.CAR_POWERTRAIN`(normal) 선언 — CTO 결정**: `GEAR_SELECTION` | 모든 화면의 P/D/R | ✅ | **신청.** AAOS 권한 포함 여부 **미확인**. 승인 밖이면 Pleos Gear(CAR_DRIVE)로 전환 |
| V3 | 도어/트렁크/프렁크 상태 조회 | `pleos.car.permission.CAR_DOORS` (normal) ✅ | `getDoorLockState`/`registerDoorLockState`, `getCurrentDoorPosition`/`registerDoorPosition`(DoorArea별) | F1(A 트리거: 긴 잠금→해제), C/F4(열린 문 항목, 뒷문 여닫음 안내) | ✅ | **신청** |
| V4 | EV 및 연료 정보 조회 | `pleos.car.permission.CAR_ENERGY` (normal) ✅ | `getEvBatteryStateOfCharge`/`registerEvBatteryStateOfCharge`(SOC — vehicle_api_summary ✅, AAR `@RequiresPermission CAR_ENERGY` ✅, develop 코드가 이미 읽음), ICE `getFuelPercent`(에뮬레이터 이미지 미지원 ✅, 실차 미확인) | D2-a(예비 트리거 상태 줄), F3(더 충전할 양·충족 문구), F4('충전 · 반납 기준') | ✅ | **신청.** **AAOS `android.car.permission.CAR_ENERGY`(dangerous)는 선언하지 않음 — CTO 결정 4: SOC를 Pleos CAR_ENERGY로 읽을 수 있으므로 생략** |
| V5 | 내부/외부 라이트 상태 조회 | `pleos.car.permission.CAR_LIGHTS` (normal) ✅ | `getCurrentTurnSignalState`/`registerTurnSignalState`, `getCurrentCabinLightState`/`registerCabinLightState`, `getCurrentReadingLightState`/`registerReadingLightState` (AAR에서 세 클래스 모두 CAR_LIGHTS ✅). 앰비언트·전조등·안개등 API는 쓰지 않음 | B 방향지시등 음성(데모 0:43), C/F4 실내등·독서등 항목 | ✅ (CTO 결정 1·2) | **신청.** release 검사 수정 필요(§1-6) |
| V6 | 좌석 설정 상태 조회 | `pleos.car.permission.CAR_SEATS` (normal) ✅ | `getCurrentSeatOccupancy`/`registerSeatOccupancy`(운전석) | F1(A 트리거의 '운전석 착석') | ✅ | **신청**. 값이 안 오면 '잠금 해제 + P'로 대체 [가정] |
| V7 | 창문 상태 조회 | `pleos.car.permission.CAR_WINDOWS` (normal) ✅ | `checkWindowPositionCapabilities`, `getCurrentWindowPosition`/`registerWindowPosition` — **develop 코드가 이미 읽음 ✅**(`PleosVehicleRepository.subscribeWindows`) | C/F4 열린 창문 항목(반납 점검) | ✅ (CTO 결정 2: 코드가 읽으므로 유지) | **신청** |
| N1 | 경로 정보 조회 | `pleos.car.permission.NAVI_ROUTE` (normal) ✅ | `getRouteStateInfo`(isRouting), `getDestinationInfo`(name·address·lat/lng·remainDistance·remainTime·isChargingStation), `getTBTInfo` + `onTBTInfo`, `onRouteStarted`·`onDestinationInfo`·`onDestinationArrived`, `getRecentDestinationInfo`, **`requestTrip`** + `onTripInfo`, **`getCurrentLocationInfo`** + `onCurrentLocationInfo` | D1, D2-a/b, F2(반납 장소 후보), F3(출발 권장 시각), C3 제목 | ✅ | **신청** |
| N2 | 경로 탐색 설정 | `pleos.car.permission.NAVI_ROUTE_SEARCH` (normal) ✅ | `requestRoute` — '반납 장소로 안내' 버튼(**대표님 미결정**)과 debug 데모 하네스의 경로 요청에만 | F3 버튼(제안) | △ | 신청 권장(버튼이 확정되면 필요). 확정 전에는 **debug 매니페스트에만** 선언 |

**권한 필요 없음 ✅:** `CarUxRestrictionsManager`(주행 규제), `AudioManager.registerAudioPlaybackCallback`(내비 음성 usage `ASSISTANCE_NAVIGATION_GUIDANCE` 판별, navi-event-check §10), 앱 내 클립 재생.

### 1-3. 질문 (1) — 9개 항목에서 빠진, 실제로 필요한 것

| 필요한 것 | 권한 (보호 수준) | 왜 필요한가 | 9개 항목 포함 여부 | 제안 |
|---|---|---|---|---|
| **기어 `GEAR_SELECTION`** | `android.car.permission.CAR_POWERTRAIN` (normal ✅) | 모든 화면의 P/D/R 판단 | **미확인** | **선언(CTO 결정 4).** V2 설명란에 "AAOS GEAR_SELECTION 읽기"를 적고 문의. 승인 밖이면 AAR의 Pleos `Gear`(CAR_DRIVE, V2 안)로 바꿀 수 있게 기어 읽기를 인터페이스 하나 뒤에 둠 |
| **유종·주유구/충전구 위치·커넥터 `INFO_*`** | `android.car.permission.CAR_INFO` (normal ✅) | D2 공통 조건 1·2, F3/F4 문구 구분. Pleos SDK에 해당 API 없음 ✅(AAR 확인) | **미확인** (V1 '제원'에 해당한다고 [해석]) | **선언(CTO 결정 4).** develop 매니페스트에 빠져 있어 추가. V1 설명란에 명시 |
| 연료 %·남은 주행거리 | `android.car.permission.CAR_ENERGY` (**dangerous** ✅) | D2-a 예비 트리거(ICE), "약 {km}km" 선택 줄 | **미확인** (V4 [해석]) | **선언 안 함(CTO 결정 4).** SOC = Pleos `getEvBatteryStateOfCharge`, ICE 연료 % = Pleos `getFuelPercent`(실차 확인 전까지 값 없으면 트리거 끔), 남은 주행거리 = Pleos `Odometer.getRangeRemainingDistance`(AAR에만 있음, 값 없으면 "약 {km}km" 줄 뺌). develop `PleosCarClient`의 `RANGE_REMAINING` 읽기는 권한 확인에서 항상 꺼지므로 삭제 권장 |
| 충전구 열림 `EV_CHARGE_PORT_OPEN` | `android.car.permission.CAR_ENERGY_PORTS` (normal ✅) | 브리프상 D2 종료 조건에 쓰지 않음(§D2-a 종료 조건) | 미확인 | **MVP 불필요.** 선언하지 않음 |
| 현재 위치 `getCurrentLocationInfo` | `pleos.car.permission.NAVI_ROUTE` ✅ | F2 '대여 시작 위치' 후보 | **포함(N1)** ✅ | 추가 신청 없음. FusedLocation은 쓰지 않음(확정 CTO 10/24). 다만 App Market 제출 항목 '위치정보 활용'에 해당할 수 있음(미확인) |
| 차속 | `android.car.permission.CAR_SPEED` (dangerous) | 불필요(기어로 충분) | — | 쓰지 않음. release 검사가 `PERF_VEHICLE_SPEED` 금지 |

→ **결론:** Pleos 권한만 보면 9개 항목으로 충분합니다. 빠질 수 있는 것은 **AAOS 권한 2종(CAR_POWERTRAIN, CAR_INFO — 둘 다 선언, CTO 결정)**이고(android CAR_ENERGY는 생략), 이것이 9개 항목에 포함되는지 문서에 근거가 없어 **미확인**입니다. 콘솔 신청 때 V1·V2·V4 설명란에 AAOS 프로퍼티 이름을 적어 두고, 승인 회신으로 확인하는 것을 제안합니다.

### 1-4. 질문 (2) — NaviHelper 두 항목 소속

| API | 권한 | 콘솔 항목 | 근거 |
|---|---|---|---|
| `requestTrip` | NAVI_ROUTE | **N1 경로 정보 조회** | API 레퍼런스 `@RequiresPermission("pleos.car.permission.NAVI_ROUTE")` "Protection level: Normal" ✅ |
| `getCurrentLocationInfo` | NAVI_ROUTE | **N1** | API 레퍼런스 ✅ |
| `getTBTInfo` / `onTBTInfo` | NAVI_ROUTE | **N1** | API 레퍼런스 ✅ |
| `getDestinationInfo` / `onDestinationInfo` / `getRouteStateInfo` / `getRecentDestinationInfo` | NAVI_ROUTE | **N1** | 권한 문서 "`get*Info()`" ✅ |
| `requestRoute` (+ cancelRoute 등) | NAVI_ROUTE_SEARCH | **N2 경로 탐색 설정** | 권한 문서 ✅ |

- 콘솔 한국어 이름 ↔ 권한 상수 대응(N1=NAVI_ROUTE, N2=NAVI_ROUTE_SEARCH)은 이름 뜻으로 본 **[해석]**입니다. `requestNavigationScreen`(NAVI_CUSTOM_MAP)은 9개 항목에 없고 쓰지 않습니다.
- `requestTrip`이 실제로 내비 화면을 띄우거나 안내를 시작하는지는 **미확인**(M0 측정 항목 2). 문서는 "실제 이벤트는 Navi 앱 구현에 따라 다를 수 있다"고만 적습니다.

### 1-5. 9개 중 MVP가 쓰지 않는 항목
- (13:45 개정) **V5 라이트·V7 창문·V2 주행 정보는 이제 씁니다**(CTO 결정 1·2, develop 코드의 창문·주행거리 읽기).
- **V1 제원:** Pleos `CarInfo` API는 미사용. AAOS `CAR_INFO`(유종·위치·커넥터) 때문에 신청합니다.
- **N2 경로 탐색 설정:** debug 하네스 전용('반납 장소로 안내' 미결정).

### 1-6. 매니페스트 규칙 (cloud-agent 브리프에도 동일)
- **매니페스트에는 9개 항목에 해당하는 권한만 선언합니다.** pleos release 매니페스트 목록(Phase 1, 13:45 개정):
  - Pleos: `pleos.car.permission.CAR_DOORS`(V3), `pleos.car.permission.CAR_WINDOWS`(V7), `pleos.car.permission.CAR_ENERGY`(V4), `pleos.car.permission.CAR_LIGHTS`(V5, **추가**), `pleos.car.permission.CAR_SEATS`(V6), `pleos.car.permission.CAR_DRIVE`(V2), `pleos.car.permission.CAR_INFO`(V1), `pleos.car.permission.NAVI_ROUTE`(N1)
  - AAOS: `android.car.permission.CAR_POWERTRAIN`(V2), `android.car.permission.CAR_INFO`(V1, **추가**) — 9개 항목 포함 여부 **미확인**(1-3), PR 본문에 명시
  - SDK가 넣는 것: `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`, `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
- debug 전용: `pleos.car.permission.NAVI_ROUTE_SEARCH`(데모 하네스 경로 요청). '반납 장소로 안내'가 확정되면 release로 옮김(develop은 지금 main 매니페스트에 있음 → debug로 이동).
- **develop 대비 변경:** 추가 `pleos.car.permission.CAR_LIGHTS`, `android.car.permission.CAR_INFO` / 삭제 `android.car.permission.CAR_ENERGY`(CTO 결정 4) / 이동 `NAVI_ROUTE_SEARCH` → debug. (13:35판의 "CAR_WINDOWS·CAR_DRIVE·pleos CAR_INFO 삭제"는 철회: 코드가 창문·주행거리를 읽고, 9개 항목 안이기 때문)
- **선언 금지(제외):** Vehicle Control(`CONTROL_CAR_*` 전부, `CONTROL_CAR_LIGHTS` 포함), 공조 `CAR_CLIMATE`, 사이드미러 `CAR_MIRRORS`, 차량 안전 `CAR_SAFETY`, 통행료 `CAR_TOLL`, Vehicle Data(EU 전용), `android.car.permission.CAR_ENERGY`, `CAR_ENERGY_PORTS`, TTS·STT SDK(`TTS_SERVICE`·`STT_SERVICE`), FusedLocation(`FUSED_LOCATION`, dangerous), `NAVI_CUSTOM_*`·`NAVI_NOTIFY`, `android.car.permission.CAR_SPEED`.
- **release 검사 수정(`checkPleosReleaseContents`, CTO 결정 1):**
  1. 금지 토큰에서 `CAR_LIGHTS`를 **뺍니다**. `CONTROL_CAR` 토큰은 그대로 두므로 `CONTROL_CAR_LIGHTS`는 계속 막힙니다.
  2. `allowedPleosReleasePermissions`에 `pleos.car.permission.CAR_LIGHTS`, `android.car.permission.CAR_INFO`를 넣고 `android.car.permission.CAR_ENERGY`, `pleos.car.permission.NAVI_ROUTE_SEARCH`(release)를 뺍니다. 목록 밖 권한은 실패.
  3. 나머지 금지 토큰(`CONTROL_CAR`, `PRIVATE_CAR_SAFETY`, `PERF_VEHICLE_SPEED`, `Fake*`, 데모 하네스 `DemoScenario*`)은 유지.
  4. 앰비언트·전조등 등 라이트 set/읽기 금지는 **코드 규칙(리뷰·단위 테스트)**으로 지킵니다. SDK `Light` 클래스 자체가 dex에 들어가 메서드 이름 문자열 검사로는 막을 수 없습니다 [해석].

---

## 2. 앱 이름·패키지

| 항목 | 값 |
|---|---|
| 표시 이름 | **미리 (Miri)** — `strings.xml` `app_name` = "미리"(현재 "Foundation"). 영문 리소스(`values-en`)를 두면 "Miri" |
| 개발사 | 크레이지랩스 / 카테고리 Mobility Services(콘솔 프로젝트에 등록됨. App Market 카테고리 목록 이름과 같은지는 미확인) |
| **패키지 (확정, 대표님 결정 10/24 13:40 KST)** | **`com.krazylabs.miri`** (K로 시작). `applicationId` = `namespace` = `com.krazylabs.miri`, fake 플레이버는 `com.krazylabs.miri.fake`. io.hmg.* 사용 금지. |

**점검 결과**
1. **Pleos 콘솔 패키지 규칙:** docs/txt 제출 가이드·프로젝트 생성 문서에서 패키지 이름 규칙(접두사 금지 등)을 찾지 못했습니다 → **미확인**. 콘솔 프로젝트 생성 단계에서는 패키지명을 받지 않고(문서 ✅: 이름·개발사명·카테고리·설명만), App Market 심사 때 APK로 제출합니다.
2. **공개 사용 (참고):** `com.krazylabs`는 미국 'KrazyLabs'(The Krazy Coupon Lady) GitHub 조직의 React Native 라이브러리 Java 패키지(`com.krazylabs.OpenAppSettingsPackage`)로 쓰인 적이 있습니다(웹 확인 10/24). 앱 ID가 아니라 라이브러리 클래스 이름이라 설치 충돌은 없습니다. 'Miri' 이름 앱은 이미 있습니다(`app.sajugpt.miri`, `dominapp.number` "Miri: Voice Assistant AI") → 상표 검토는 별도 권장.
3. **SDK·매니페스트 제약:** SDK AAR은 패키지에 제약이 없습니다(✅ AAR 매니페스트에 패키지 의존 권한 없음). `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`은 applicationId를 따라 바뀌며 develop 허용 목록이 이미 `$pleosApplicationId`로 계산합니다 ✅.
4. **applicationId·namespace 변경:**
   - **applicationId와 namespace를 둘 다 `com.krazylabs.miri`로**, Kotlin 소스 패키지도 `com.krazylabs.miri.*`로 옮깁니다(io.hmg 흔적 제거). `R`/`BuildConfig` import, Hilt 생성 코드, 테스트 패키지가 함께 바뀝니다.
   - Room 스키마 폴더 `app/schemas/io.hmg.appchallenge.foundation.data.local.FoundationDatabase/` → 새 FQCN 폴더로 옮깁니다(DB 이름·버전은 유지).
   - 에뮬레이터: 옛 패키지 `adb uninstall`, 앱 데이터 초기화. (android CAR_ENERGY를 선언하지 않으므로 `pm grant`는 필요 없음)
   - `exportContestApk` 파일명: 공모전 규칙 `팀명_앱이름_release.apk` ✅(참가 가이드) → 현재 `placeholder-team_foundation_release.apk`를 `{팀명}_miri_release.apk`로. **팀명 미확인**.
   - `com.hmg.rotary.ROTARY_SUPPORTED` 메타데이터 키는 플랫폼 키라 그대로 둡니다.

---

## 3. (Task A 결과) 음성 클립 수
- voice-check.md §3-d: 터널 11개 삭제, `vo_charge_port_rear_right.wav`(오른쪽 뒤)·`vo_charge_port_rear_left.wav`(왼쪽 뒤)·`vo_connector_dc_combo.wav` 추가. **13:40 2차(CTO 결정 3): 톨게이트 G0 `vo_toll_0` 삭제, 톨게이트 0개일 때 쓰는 고속도로 진입 1개 `vo_highway_entry.wav`("오늘 길엔 고속도로 진입이 있어요. 출발 전에 운전 보조 버튼 위치를 봐 두세요.", P 카드와 짝, 주행 중 재생 안 함 — 13:47 확정) 추가** → **29클립 그대로.** 13:47 H1 문장 확정(두 문장, 약 5.1 s)으로 **약 59.3초(묵음 포함), 24 kHz 모노 16-bit WAV 약 2.8 MB**. '왼쪽 뒤'를 시험용으로만 두면 28클립·약 57.6초·약 2.8 MB. 조합: D2-a 16 · D2-b 6 · D1 11(톨게이트 10 + 고속도로 진입 1). 백업 `voice-check.v3d-37.bak.md`, `voice-check.v3d-29g0.bak.md`.
- **방향지시등 음성은 클립 없이 차임만**(CTO 13:47 확정, 29개 유지).
- 아직 클립이 없으므로 MVP는 **파일 이름 자리만 두고 차임으로 대체**합니다(§6-4).

---

## 4. 비밀값 처리 (Pleos Connect 프로젝트 '미리')
- 파일: 박스 `/workspace/hmg-contest/secrets/pleos-miri.env` (mode 600). **변수 이름만:** `PLEOS_PROJECT_ID`, `PLEOS_CLIENT_ID`, `PLEOS_CLIENT_SECRET`, `PLEOS_TEST_CRN` (+ 주석으로 devbox CRN 주입 adb 절차).
- **MVP에서 실제 필요성 ✅(create-my-project 문서):** 프로젝트 ID = App Market 심사 요청 때 콘솔에 입력하는 값(앱 런타임 불필요). 클라이언트 ID/시크릿 = 서버 연결 SDK(TextToSpeech·SpeechToText) 전용 → **MVP는 TTS/STT SDK를 쓰지 않으므로 앱에 넣지 않습니다.** CRN = "Pleos Connect Emulator 2.1.0 이후 CRN 입력이 요구되지 않음" → MVP는 `getCRNInfo`도 쓰지 않으므로 보통 불필요.
- **빌드가 읽는 방법:**
  1. 우선순위: 환경 변수 → `local.properties`(git 무시) → `PLEOS_SECRETS_FILE`이 가리키는 파일(기본 `secrets/pleos-miri.env`, 레포 밖 또는 `.gitignore`에 `secrets/`·`*.env` 추가).
  2. `app/build.gradle.kts`에서 pleos 플레이버에만 `buildConfigField`/`manifestPlaceholders`로 주입. **값이 없으면 빈 문자열("") 폴백**으로 빌드가 성공해야 합니다(CI·fake 빌드·클라우드 에이전트는 비밀값 없이 빌드).
  3. MVP는 `PLEOS_PROJECT_ID`만 선택적으로 BuildConfig에 둘 수 있고, **`PLEOS_CLIENT_SECRET`은 APK에 넣지 않습니다**(APK는 디컴파일 가능). TTS/STT SDK를 도입할 때 다시 결정.
  4. 절대 커밋하지 않음. 로그·PR·크래시 리포트에 값 출력 금지. CI에 `local.properties`·`secrets/`·`*.env`가 git에 추적되지 않는지 확인하는 단계를 둡니다(값 자체는 어디에서도 출력·비교하지 않음).
- **CRN 주입:** 에뮬레이터에서 `adb root`가 될 때만, 콘솔 안내 명령(비밀 파일 주석의 절차: root → VHAL fifo에 CRN 프로퍼티 기록 → 재부팅)을 사람이 직접 실행합니다. 실차·사용자 기기에는 하지 않습니다. 재부팅이 있어 대표님 Mac 스왑 부담이 큽니다 → 인증·CRN 오류가 실제로 날 때만 합니다.
- **주의:** 값은 스크린샷에서 옮겨 적어 **I/l(대문자 아이/소문자 엘) 오인 가능성**이 있습니다. 인증 실패가 나면 값을 추측해 바꾸지 말고 **CTO에게 보고**합니다.

---

## 5. 마일스톤 (시간은 클라우드 에이전트 작업 시간 추정)

전제: 개발은 클라우드 에이전트(박스·Mac 접근 없음, fake 플레이버 단위 테스트로 검증), 실행 확인은 대표님 Mac의 Pleos 에뮬레이터(스왑 여유가 있을 때만, 한 번에 한 사람).

| M | 내용 | 에이전트 | 에뮬레이터 | 끝 조건 |
|---|---|---|---|---|
| **M0** 측정(보류) | 기존 프로브(경로 없음 상태)에서 ⓪ 기어: AAOS `GEAR_SELECTION` vs Pleos `Gear`(문서 없음) 값·지연 비교(CTO 13:47) ① `getCurrentLocationInfo` 응답 여부·좌표/주소 필드·권한 ② 임의 반납 좌표 `requestTrip`이 안내 시작 없이 거리·ETA를 주는지, 내비 화면이 뜨는지. **지금 실행하지 않음** | 0.5 h(프로브 버튼 추가, PR #6 방식) | 0.5 h | 결과를 navi-event-check에 기록. 실패 시 F2 '대여 시작 위치' 후보·F3 출발 시각은 폴백 문구로 |
| **M1** 골격 + 데모 하네스 | 41ad71b에서 브랜치. 패키지·이름 변경(§2), 비밀값 배선(§4), 매니페스트 정리(§1-6), 상태 머신 골격, **debug 전용 데모 시나리오 입력**(가짜 남은 거리·시간·반납 시각), release 검사 확장 | 6 h | — | fake·pleos 빌드, 단위 테스트 통과 |
| **M2** 신호 계층 | 기어(AAOS, Pleos Gear 대체 가능한 인터페이스)·도어·**창문**·착석·SOC·**라이트(방향지시등·실내등·독서등)**·CAR_INFO·NaviHelper 래퍼, **onResume 재조회**, **빈 TBT 목록 3회 무시**, 동결 중 늦게 온 콜백 무시, 후방 카메라(R→P) 대응 | 6 h | — | fake 시나리오 테스트 |
| **M3** 규칙 엔진 | D-4 대기열(C > A > D2/F3 > D1 > D3, P당 카드 1·문장 1), D2 공통 조건 1~5(a~e), D1 개수·빈 카드 금지, F3 계산·폴백, C/F4 항목(문·창문·실내등·독서등)·실시간 해제, **B 방향지시등 예외 1(20초, 데모 8초, 렌트당 2회, 내비 음성 중 생략)**, 빈도 제한, 새 대여 리셋 | 6 h | — | 규칙별 단위 테스트 |
| **M4** 화면 | F1·F2(칩, P 전용)·D1·D2-a·F3·C(C1/C1r/C2/C3 제목)·F4 항목·E1 주행 중 아이콘. 오른쪽 칸 1682×1210 가로, 다크/라이트, v4 시안 | 8 h | — | 스크린샷 테스트 |
| **M5** 음성 | 클립 슬롯(29개 이름) → 기기 TTS → 차임, 클립 없으면 문장 삭제·모두 빠지면 카드+차임, MAY_DUCK, 내비 음성 대기 최대 5초·150 m, D2-b | 3 h | — | fake 테스트 |
| **M6** 첫 에뮬레이터 빌드 | pleosDebug APK, VC(127.0.0.1:8088)로 기어·문 바꾸며 90초 흐름 스모크, 기어 두 경로(AAOS/Pleos Gear) 비교 재확인, release 검사 통과 | 2 h | 1 h | **첫 에뮬레이터 실행** |
| M7 다듬기 | 데모 리허설, 녹화 재생 장면(D2-b, C3) 자막, 타이밍, 버그 | 8 h | 2 h | 90초 데모 녹화 가능 |
| M8 제출 준비 | release APK(`apk/` 폴더, 파일명 규칙), README(앱 이름·실행 방법·테스트 환경) | 2 h | 0.5 h | 1차 접수(11/15) 가산점용 |

- **M1~M6 합계: 에이전트 약 31시간 + 에뮬레이터 1시간**(13:45 개정: 라이트·창문·방향지시등 +2 h). M3과 M4는 M2 뒤 병렬 가능(에이전트 2개면 벽시계 약 22시간).
- **첫 에뮬레이터 빌드 ETA:** 가장 이른 시점 **10/25(일) 22:00 KST**, 현실적 **10/26(월) 13:00 KST** [가정: 10/24 15:00 KST 착수, 리뷰 대기 포함, Mac 스왑 여유]. M7까지 10/27(화) 전후.

---

## 6. 클라우드 에이전트 브리프 (이 절만 복사해 그대로 전달)

> **목표:** 차량용 앱 '미리 (Miri)' Phase 1 MVP를 만들어, Pleos 에뮬레이터에서 약 90초 데모 흐름이 돌아가게 합니다. 앱은 **알려 주기만** 하고 차를 제어하지 않습니다.

### 6-1. 저장소·브랜치 규칙
- 저장소 `dohoon84/hmg-app-challenge-2026`. 문서는 브랜치 **`feature/miri-mvp`**(develop `41ad71b` + 문서 커밋)의 `docs/mvp/`에 있습니다. 이 브랜치에서 새 브랜치 `cursor/miri-mvp-phase1-<id>`를 만듭니다(코드 기준은 41ad71b 그대로).
- 작업이 끝나면 **develop을 대상으로 PR을 엽니다. 머지하지 않습니다.** develop·main에 직접 push하지 않습니다. PR #6(프로브)은 건드리지 않고 머지하지 않습니다.
- 마일스톤마다 커밋을 나누고 PR 본문에 체크리스트(M1~M6)를 둡니다.
- 읽을 문서(`docs/mvp/`, 비밀값 검사 통과): `u1-rental-screens-brief.md`(v1.20), `u1-context-signals.md`(v1.17), `navi-event-check.md`(§9~10 포함 전문), `voice-check.md`(§3-d 29클립판, 13:40 2차), `mvp-plan.md`(이 문서), `mockups/u1-rental/v4/`의 **최상위 PNG 60장**(약 41 MB). 브리프와 이 문서가 다르면 **이 문서(§7-0 결정)**를 따릅니다.

### 6-2. 앱 정보
- 표시 이름 `app_name` = **"미리"**(영문 리소스 "Miri"). 개발사 크레이지랩스, 카테고리 Mobility Services.
- `applicationId` = `namespace` = **`com.krazylabs.miri`**(K, 확정 — 대표님 10/24), 소스 패키지도 `com.krazylabs.miri.*`로 이동. fake = `com.krazylabs.miri.fake`(applicationIdSuffix `.fake`). **io.hmg.* 금지.** Room 스키마 폴더 이동.
- `exportContestApk` 파일명 `{팀명}_miri_release.apk`(팀명은 Gradle 속성으로, 기본 `team`).
- `versionName` "0.2.0-mvp".

### 6-3. 비밀값
- 변수 이름: `PLEOS_PROJECT_ID`, `PLEOS_CLIENT_ID`, `PLEOS_CLIENT_SECRET`, `PLEOS_TEST_CRN`. **값은 받지 않으며 필요 없습니다.**
- 읽는 순서: 환경 변수 → `local.properties` → `PLEOS_SECRETS_FILE`(기본 `secrets/pleos-miri.env`). `.gitignore`에 `secrets/`, `*.env` 추가. 값이 없으면 "" 폴백으로 **항상 빌드 성공**. pleos 플레이버에만 `buildConfigField("String","PLEOS_PROJECT_ID",…)`. **클라이언트 시크릿은 APK에 넣지 않음**(TTS/STT SDK 미사용). 값을 로그에 찍지 않음.
- CRN 주입은 사람이 에뮬레이터에서 `adb root`가 될 때만 합니다. 코드·스크립트에 넣지 않습니다. 인증 오류는 값을 고치지 말고 CTO에 보고(값에 I/l 오인 가능성).

### 6-4. 권한·SDK
- SDK: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`(develop에 이미 있음), AAOS `useLibrary("android.car")`.
- **매니페스트에는 콘솔 9개 항목에 해당하는 권한만 선언합니다.** release: `pleos.car.permission.CAR_DOORS`, `pleos.car.permission.CAR_WINDOWS`, `pleos.car.permission.CAR_ENERGY`, `pleos.car.permission.CAR_LIGHTS`, `pleos.car.permission.CAR_SEATS`, `pleos.car.permission.CAR_DRIVE`, `pleos.car.permission.CAR_INFO`, `pleos.car.permission.NAVI_ROUTE`, `android.car.permission.CAR_POWERTRAIN`, `android.car.permission.CAR_INFO`(AAOS 2개는 승인 범위 미확인 — PR 본문에 표시). debug 전용 `pleos.car.permission.NAVI_ROUTE_SEARCH`.
- **`android.car.permission.CAR_ENERGY`는 선언하지 않습니다**(SOC는 Pleos `getEvBatteryStateOfCharge`로 읽음). develop `PleosCarClient`의 `RANGE_REMAINING` 읽기와 권한 확인 코드는 삭제합니다. 남은 주행거리가 필요하면 Pleos `Odometer.getRangeRemainingDistance`(CAR_DRIVE, AAR에만 있음)를 쓰고 값이 없으면 그 줄을 뺍니다.
- 금지: `CONTROL_CAR_*`(=`CONTROL_CAR_LIGHTS` 포함), `CAR_CLIMATE`, `CAR_MIRRORS`, `CAR_SAFETY`, `CAR_TOLL`, `android.car.permission.CAR_ENERGY`, `CAR_ENERGY_PORTS`, `TTS_SERVICE`, `STT_SERVICE`, `FUSED_LOCATION`, `NAVI_CUSTOM_*`, `NAVI_NOTIFY`, `CAR_SPEED`.
- **release 검사:** `checkPleosReleaseContents` 금지 토큰에서 `CAR_LIGHTS`를 빼고(`CONTROL_CAR` 토큰은 유지 → CONTROL_CAR_LIGHTS 계속 차단), `allowedPleosReleasePermissions`에 `pleos.car.permission.CAR_LIGHTS`·`android.car.permission.CAR_INFO` 추가, `android.car.permission.CAR_ENERGY`·release `NAVI_ROUTE_SEARCH` 제거. 목록 밖 권한은 실패.
- API: 기어 `CarPropertyManager` `GEAR_SELECTION` / 유종·위치·커넥터 `INFO_FUEL_TYPE`·`INFO_FUEL_DOOR_LOCATION`·`INFO_EV_PORT_LOCATION`·`INFO_EV_CONNECTOR_TYPE`(에뮬레이터 값 ELECTRIC·REAR_RIGHT·COMBO_2·REAR_RIGHT) / Pleos `getDoorLockState`·`registerDoorLockState`·`getCurrentDoorPosition`·`registerDoorPosition` / 창문 `getCurrentWindowPosition`·`registerWindowPosition`(develop 코드 재사용) / 라이트 `getCurrentTurnSignalState`·`registerTurnSignalState`·`getCurrentCabinLightState`·`registerCabinLightState`·`getCurrentReadingLightState`·`registerReadingLightState` / `getCurrentSeatOccupancy`·`registerSeatOccupancy` / `getEvBatteryStateOfCharge`·`registerEvBatteryStateOfCharge` / NaviHelper `getRouteStateInfo`·`getDestinationInfo`·`getTBTInfo`(+onTBTInfo)·`getRecentDestinationInfo`·`getCurrentLocationInfo`·`requestTrip`(+onTripInfo). `requestRoute`는 debug 하네스와 '반납 장소로 안내'(미결정, 기본 숨김 플래그)에서만. 내비 음성 판별 `registerAudioPlaybackCallback`(usage ASSISTANCE_NAVIGATION_GUIDANCE).
- **쓰지 않음:** Vehicle Control(set 계열 전부, 라이트 set 포함), TTS/STT SDK, FusedLocation, 앰비언트·전조등·안개등·테일램프 API, 오버레이·떠 있는 창·토스트, 포그라운드 서비스.

### 6-5. 신호 규칙 (실측 반영)
1. **onResume 재조회:** 다른 앱에 가려진 지 약 10초면 앱이 얼고 콜백은 다시 보일 때 몰아서 옵니다. 다시 보이면 기어·도어·SOC·경로·목적지를 **직접 다시 읽고** 1.7~2.5초 안에 화면을 그립니다. 몰아서 온 오래된 이벤트로 판단하지 않습니다(특히 D2-b 거리 값, 기어 횟수).
2. **빈 TBT 목록 3회 무시:** 경로 시작 때 TBT 전체 목록이 한 번에 옵니다(마지막 TURN_ARRIVE). 빈 목록은 연속 3회까지 무시하고 직전 목록을 유지합니다. 경로 취소·`isRouting=false`면 목록을 버립니다. D1 개수는 TurnType `TURN_TOLL`, `TURN_ENTER_IC_*`를 직접 셉니다.
3. **후방 카메라:** R이면 카메라가 오른쪽 칸을 덮고, R→P 뒤에도 2분 넘게 남으며 앱이 자동 복귀하지 않습니다. 앱은 앞으로 나서지 않습니다(BAL로 막힘). 돌아왔을 때 onResume 규칙으로 처리합니다.
4. 주행 시뮬레이션 없음 → `remainDistance`/`remainTime` 주행 중 갱신은 미확인. 데모 하네스로 대체.
5. 기어 D·R이면 모든 카드 즉시 닫힘, 주행 중에는 대기열에서 꺼내지 않음. 화면은 상태 아이콘 1개(ic_vehicle 회색)만.
6. 값이 비거나 UNKNOWN이면 추측하지 않습니다(D2 공통 조건).

### 6-6. 화면 사양 (요약, 원문은 docs/mvp/u1-rental-screens-brief.md)
- **공통:** 오른쪽 칸 1682×1210 가로 안에서만(그림 왼쪽·글 오른쪽), 일반 Activity 하나, 한 P에 카드 1장·문장 1개. 순위 **C > A > D2(F3 포함) > D1 > D3**. 못 나온 카드는 다음 P로 1번만(D1은 버림). 게임 요소·경고색·비난 표현·'끄기/잠그기' 버튼 금지. 지도·경로선 금지. 다크/라이트 지원.
- **F1 이용 유형(P):** 새 대여(긴 잠금 → 해제 + 운전석 착석 + P; 데모 잠금 10초) 때 A1 환영 다음. 제목 "어떤 차로 타고 계세요?", 칩 3개(내 차/렌터카/쉐어카). '내 차'면 다시 묻지 않고 F2·F3 생략. D·R이면 닫고 다음 P에 다시.
- **F2 반납 정보(P):** 제목 "언제, 어디로 반납하세요?". 날짜 칩(오늘/내일/날짜) + 시각 칩, 장소 후보 최대 3개(운영사 영업소 [가정], `getRecentDestinationInfo`, 대여 시작 위치 = `getCurrentLocationInfo` — M0 미확인 시 생략). 주소 입력칸 없음.
- **D1 '오늘 길'(P):** `isRouting` + 새 경로 ID 뒤 첫 P, 앱이 보일 때만. 항목 최대 2개 "톨게이트 {n}곳"(첫 항목) · "고속도로 진입 {n}번". 둘 다 0이면 카드 없음. 음성은 첫 항목 1문장(톨게이트 G1~G10 클립, 톨게이트 0개면 고속도로 진입 H1 `vo_highway_entry` "오늘 길엔 고속도로 진입이 있어요. 출발 전에 운전 보조 버튼 위치를 봐 두세요." — P 카드와 짝, 주행 중 재생 안 함). 20초 뒤(데모 6초) 자동 닫힘. 경로 ID당 1회, 렌트당 3회. 목적지가 주유소·충전소면 D1 생략.
- **D2-a 충전소(P):** 목적지가 충전소(`isChargingStation`/`EV_CHARGING`)이고 P, 앱이 보일 때(onResume). 제목 "충전소에 가요", 본문 "충전구는 {오른쪽 뒤}에 있어요. 커넥터: {DC 콤보}", 유종 칩, 탑뷰 그림 해당 옆면 강조. 음성 F5 + P5 + K1. 주유소는 이름·브랜드 문자열 매칭(정지 화면용). **D2 공통 조건:** ① 유종·위치 둘 다 유효할 때만, 하나라도 비면 D2 전체 없음 ② 커넥터만 예외(비거나 낯선 값이면 커넥터 문장·칩만 뺌) ③ 기기 TTS로 대신 읽지 않음 ④ (a) 사양 팩과 다르면 없음 (b) 클립 없는 값이면 카드만 (d) 유종·목적지 불일치면 없음 (e) 다연료는 주유소에서만 ⑤ EV '충전', 그 외 '연료'. 예비 트리거(SOC/연료 낮음, 목적지 없음) 상태 줄 "충전 {n}% 남음".
- **D2-b(주행 중 음성만):** 남은 ≤ 500 m 또는 ≤ 45초, 경로당 1회, A2 + 250 ms + P5. 내비 음성 중이면 최대 5초 기다리고 남은 150 m 이상일 때만. 화면 변화 없음. 데모는 녹화 재생 + 자막 '시뮬레이션'(하네스로 값 주입해 동작 확인).
- **F3 반납 준비(P, 카드 전용·음성 없음):** 반납 렌터 24h·3h 전, 쉐어 1h 전. "반납까지 약 {x}% 더 충전해야 해요."(ICE "연료를 약 {x}% 더 채워야 해요.") / "{hh:mm}쯤 출발하면 여유 있게 도착해요."(반납 시각 − `requestTrip` ETA − 15분) / 실패 시 "반납 {hh:mm}까지 {n}시간 남았어요." / 충족 "반납 기준만큼 충전됐어요.". 기준량 모르면 그 문장 뺌. '반납 장소로 안내' 버튼은 기능 플래그로 숨김(점선, 대표님 미결정).
- **C/F4 반납 점검(P):** 주행 뒤 P(또는 반납 장소 도착 → 제목 "반납 장소에 도착했어요"). 제목 "반납 전 점검", 열린 문 항목 "{오른쪽 뒷문}이 열려 있어요." — 닫히면 확인음과 함께 실시간으로 사라짐, 뒷문을 여닫았으면 별도 안내 한 줄(항목 수 제외), 하단 "다 끝나면 알려 드릴게요.", 재표시 배지 "아직 {N}개" + 음성 "아직 확인할 곳이 {N}개 남아 있어요.", 완료 "점검 끝. 좋은 하루 보내세요.". F4 추가 항목 "충전 {x}% · 반납 기준 {y}%"(카드 전용, 신호로 자동 해제). **열린 창문 항목** "{앞 왼쪽} 창문이 열려 있어요."(확정 CTO 13:47, 카드 전용·새 클립 없음, 음성은 기존 C 규칙), **실내등·독서등 항목(CAR_LIGHTS, CTO 결정 2):** "실내등이 켜져 있어요." / "{뒤쪽 왼쪽} 독서등이 켜져 있어요."(브리프 문구) — 꺼지면 실시간으로 사라짐. **데모 한정:** 에뮬레이터 VC로 실내등을 바꿀 수 없으면 데모 1:05·1:09 두 항목만 VC로 연 문 2개로 대체합니다. 제품 규칙(라이트 항목)은 그대로 둡니다.
- **B 주행 중 예외 1 — 방향지시등 음성(Phase 1 포함, CTO 결정 1, 데모 0:43):** 주행 중(D) 방향지시등이 **20초(데모 8초) 이상** 계속 켜져 있으면 음성 1문장 "{왼쪽/오른쪽} 방향지시등이 켜져 있어요." — 화면 변화 없음(아이콘 1개 유지). 렌트당 최대 2회, 내비 음성 재생 중이면 생략, 꺼지면 타이머 리셋. 비상등(양쪽)은 말하지 않음 [가정]. **클립 없이 차임만**(CTO 13:47 확정).
- **E1:** 주행 중 화면 = 아이콘 1개. 정차 중 카드만 오른쪽 칸에. 다른 앱에서 돌아오면 보던 카드·진행 상태 복원, 뒤에 있을 때는 갱신·애니메이션 중지.

### 6-7. 음성
- 순서: **클립 → 기기 TTS(android.speech.tts) → 차임**. 클립은 `res/raw/`에 아직 없음 → **voice-check §3-d의 29개 파일 이름을 키→`R.raw.*` 매핑 표로 자리만 만들고**, 파일이 없으면 기기 TTS를 확인(에뮬레이터엔 엔진 없음)하고 없으면 develop의 `announce_chime.wav`. 클립이 없는 문장은 TTS로 대신 읽지 않고 **문장을 뺍니다**(브리프 규칙). 모든 문장이 빠지면 카드 + 차임.
- 클립 이름: `vo_fuel_{gasoline,diesel,lpg,hybrid,electric}`, `vo_port_{left,right}`, `vo_charge_port_{left,right,rear_right,rear_left}`, `vo_toll_{1..9,many}`, `vo_highway_entry`, `vo_connector_dc_combo`, `vo_arrive_{gas,ev}_soon`, `vo_brief_intro`, `vo_return_arrived`, `vo_return_check`, `vo_check_done` (.wav, 24 kHz mono). 문장 사이 250 ms, 선행 묵음 150~200 ms, MAY_DUCK, 내비 안내 시작 시 즉시 멈춤.
- 방향지시등은 클립 없이 차임만(CTO 13:47 확정, 29개 목록 그대로).
- TTS/STT **SDK**는 쓰지 않습니다.

### 6-8. 데모 시나리오 하네스 (debug 전용)
- `src/debug`(또는 `pleosDebug`) 소스셋에만 둡니다: 가짜 `remainDistance`/`remainTime` 주입, 반납 시각 앞당기기, 새 대여 잠금 10초, 카드 간격 6초 같은 데모 타이밍, 경로 요청 버튼(NAVI_ROUTE_SEARCH, debug 매니페스트).
- release에서 제외: `checkPleosReleaseContents`에 하네스 클래스 이름(예: `DemoScenario*`)을 dex 금지 목록으로 추가하고, 기존 금지 토큰 중 CONTROL_CAR, PRIVATE_CAR_SAFETY, PERF_VEHICLE_SPEED, Fake*는 유지하고 **CAR_LIGHTS는 뺍니다**(CTO 결정 1). 하네스 값: 방향지시등 지속 8초, 실내등 대체(문 2개) 토글.
- 기어·문·방향지시등·실내등(가능하면)은 에뮬레이터 VC(`http://127.0.0.1:8088`)로 바꿉니다(사람이 조작). 앱 코드에서 VC를 부르지 않습니다.

### 6-9. 검증
- 클라우드 에이전트는 fake 플레이버 단위 테스트·스크린샷 테스트, `assemblePleosDebug`, `checkPleosReleaseContents`까지. 에뮬레이터 실행은 사람이 대표님 Mac에서(스왑 제한, 요청 시에만).
- PR 본문에: 권한 목록과 9개 항목 대응, 미확인 항목, 테스트 결과, 에뮬레이터 확인 절차(옛 패키지 uninstall → `com.krazylabs.miri` 설치(dangerous 권한 없음, `pm grant` 불필요) → VC로 P/D/문/방향지시등 변경).

---

## 7. 결정 사항과 미확인

### 7-0. 결정 사항 (10/24)
| # | 시각 (KST) | 누가 | 결정 | 반영 |
|---|---|---|---|---|
| 1 | 13:37 | CTO | 방향지시등 음성(데모 0:43)을 **Phase 1에 포함**. Pleos `CAR_LIGHTS`(normal) 선언, release 검사에서 `CAR_LIGHTS` 금지 토큰 제거(CONTROL_CAR 금지는 유지) | §1-2 V5, §1-6, §6-4, §6-6 B, §6-8 |
| 2 | 13:37 | CTO | C 점검: 실내등·독서등을 라이트 권한으로 읽음. VC로 실내등을 못 바꾸면 **데모에서만** VC 문 2개로 대체(제품 규칙 그대로). 창문은 반납 점검에도 씀 → **develop 41ad71b 코드가 창문을 읽으므로(`subscribeWindows`) `CAR_WINDOWS` 유지** | §1-2 V5·V7, §6-6 C/F4 |
| 3 | 13:37 | CTO | voice-check §3-d: 톨게이트 G0 삭제, 톨게이트 0개용 '고속도로 진입' 클립 1개 추가(짝 카드·문장은 #8에서 확정). **29개 유지** (길이·용량은 #8에서 갱신) | §3, voice-check §3-d |
| 4 | 13:37 | CTO | 매니페스트에 `android.car.permission.CAR_POWERTRAIN`·`android.car.permission.CAR_INFO` 추가. `android.car.permission.CAR_ENERGY`(dangerous)는 **생략** — 근거: SOC를 Pleos `getEvBatteryStateOfCharge`/`registerEvBatteryStateOfCharge`(`pleos.car.permission.CAR_ENERGY`, normal)로 읽을 수 있음(vehicle_api_summary ✅, Vehicle-2.3.2 AAR `@RequiresPermission` ✅, develop 코드가 이미 사용 ✅). 영향: ICE 연료 %는 Pleos `getFuelPercent`(에뮬레이터 미지원, 실차 미확인), 남은 주행거리는 Pleos Odometer(AAR에만 있음) 또는 줄 생략 | §1-2 V1·V2·V4, §1-3, §1-6, §6-4 |
| 5 | 13:37 → 13:40 | CTO → 대표님 | 13:37 CTO: 패키지는 추후 지시까지 유지. **13:40 대표님: 패키지 `com.krazylabs.miri`(K) 확정**, applicationId·namespace 모두, fake `.fake`. 표시 이름 '미리' 유지. 결정 대기 표시 삭제 | §2, §6-2 |
| 6 | 13:47 | CTO | 방향지시등 좌·우 클립은 **만들지 않음 — 차임만**. 클립 29개 유지 | §3, §6-6 B, §6-7, voice-check §3-d |
| 7 | 13:47 | CTO | 창문 카드 문구 확정 **"{앞 왼쪽} 창문이 열려 있어요."** — 카드 전용, 음성은 기존 C 규칙, 새 클립 없음 | §6-6 C/F4, voice-check §3-d |
| 8 | 13:47 | CTO | H1 `vo_highway_entry` 문장 확정 **"오늘 길엔 고속도로 진입이 있어요. 출발 전에 운전 보조 버튼 위치를 봐 두세요."** — P 카드(D1)와 짝, 주행 중 재생 안 함. 길이 재계산(0.157 s/음절 × 31음절 + 쉼 0.25 s ≈ 5.1 s) → **29개 합계 약 59.3 s, 24 kHz WAV 약 2.8 MB** | §3, §6-6 D1, voice-check §3-d (7) |
| 9 | 13:47 | CTO | 문서에 없는 Pleos `Gear` API(CAR_DRIVE)는 **대체 경로로 유지**, M0/M6에서 AAOS `GEAR_SELECTION`(CAR_POWERTRAIN)과 값·지연을 비교 | §1-1, §1-3, §5 M0/M6, §7-1 |

### 7-1. 미확인·결정 필요
1. **AAOS 권한(CAR_POWERTRAIN·CAR_INFO)이 콘솔 9개 항목 승인 범위에 드는지** — 미확인, 최우선. 콘솔 신청서 V1·V2 설명란에 명시 제안. 기어는 승인 밖이어도 Pleos `Gear`(CAR_DRIVE, AAR에만 있음·문서 없음)로 대체 가능성 있음 — 에뮬레이터 동작 미확인.
2. 콘솔 항목 한국어 이름 ↔ 권한 상수 공식 대응표 — [해석](도메인 11개 1:1 일치). 항목별 승인 필요 여부·심사 기간 — 미확인(문서상 Vehicle State·NaviHelper는 신청 없이 사용 가능, 승인 목록 10/27 이후 공개).
3. `getCurrentLocationInfo` 응답·필드, `requestTrip`이 안내를 시작하는지 — M0 측정 대기.
4. (해결 13:47) 방향지시등은 차임만. 비상등(양쪽 점멸) 처리만 [가정: 말하지 않음] 확인 필요.
5. 에뮬레이터 VC로 실내등·독서등·방향지시등 값을 바꿀 수 있는지 미확인(M6에서 확인). 못 바꾸면 데모 1:05·1:09는 문 2개로 대체(결정 2), 0:43은 하네스로만 확인.
6. (해결 13:47) C 창문 문구 확정.
7. H1 문장 확정(13:47). 브리프 D1 음성 [확정 틀] "고속도로 진입이 {n}번 있어요."를 H1 문장으로 맞추는 브리프 수정은 남음.
8. '반납 장소로 안내'(requestRoute, N2) — 대표님 미결정.
9. 'Miri' 이름 앱 기존재 → 상표 검토 권장. 콘솔 패키지 규칙 미확인. 팀명(APK 파일명) 미확인.
10. 클립 미녹음(10/28 설명회 뒤 음성 서비스 결정).
11. 착석 신호가 에뮬레이터 VC에서 바뀌는지 미확인 → 안 되면 F1 트리거는 잠금 해제 + P.
