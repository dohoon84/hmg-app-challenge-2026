# 내비 이벤트·차량 신호 실측 점검 (Pleos Connect Emulator)

> 작성: 2026-10-24 10:20 KST (최종본) · 작성 환경: 김도훈 MacBook Air(arm64)의 Pleos Connect 에뮬레이터 + 프로브 APK(`io.hmg.appchallenge.foundation`, PR #6)
> 기준 문서: u1-context-signals.md §1·§7(Q20~Q24). 같은 내용은 반복하지 않고 확인·정정만 적습니다.
> 증거 폴더: `/workspace/hmg-contest/evidence/navi/` (아래에서는 `ev/`로 줄여 씁니다)
> 시각 표기: 에뮬레이터 로그는 PDT(UTC-7)로 찍혀 있습니다. **로그 18:05:50 = KST 10:05:50** (+16시간)입니다.
> 🧪 표시 = 프로브 APK가 있어야 확인되는 항목입니다.

## 0. 결과 요약

| 항목 | 상태 | 핵심 근거 | 안 될 때 대안 (한 줄) |
|---|---|---|---|
| Q21 내비 이벤트 수신 🧪 | **됨** | requestRoute 후 onRouteStarted·onNavigationStatus·onRouteStateInfo·onTBTInfo·onDestinationInfo·onDrivingInfo 수신 (`ev/probe-logcat.txt`) | — |
| Q21 주행 시뮬레이션 | **안 됨** | SDK에 시뮬레이션 시작 API 없음, 내비 앱(3.3.2) 설정에 모의주행 메뉴 없음, `adb emu geo fix`·VC 속도 입력에도 내비 위치 고정 | 경로 요청 후 정지 상태 이벤트 + 프로브 JSONL 녹화·재생으로 데모 |
| Q20 주유소 POI 유형 | **안 됨** | DestinationInfo에 업종 필드 없음, RoutePointPoiType = NORMAL / EV_CHARGING 뿐, 내비 검색 카테고리에도 주유소 없음 | poiName·address 문자열 매칭(“주유소”, 브랜드명), EV는 DrivingState.isChargingStation |
| Q23 TBT 범위 🧪 | **됨** | 경로 시작 시 onTBTInfo 한 번에 **53개(경로 전체, 마지막 TURN_ARRIVE)** | — |
| Q22 권한 | **됨** | NAVI_ROUTE 등 pleos.* 대부분 normal. 단 **android.car.permission.CAR_ENERGY는 dangerous**(런타임 요청 필요) | — |
| Q22 백그라운드 수신 🧪 | **안 됨** | 백그라운드 진입 약 10초 뒤 Android 14 앱 프리저가 프로세스를 얼려 콜백이 복귀 때까지 지연 | 화면에 남는 구성(포그라운드 유지)·포그라운드 서비스 검토, 복귀 시 get*Info/requestTrip 재조회 |
| Q24 차량 정보 값 🧪 | **됨** | AAOS INFO_*·FUEL_LEVEL·EV_BATTERY_LEVEL·RANGE_REMAINING 모두 읽힘. Pleos getFuelPercent는 이미지 미지원 | 연료는 AAOS FUEL_LEVEL / INFO_FUEL_CAPACITY로 계산 |
| 기어 R→D→R 주입·수신 🧪 | **됨** | Virtual Console REST로 주입, Vehicle.getGear·CarPropertyManager(GEAR_SELECTION) 두 경로 모두 수신 | — |

---

## 1. Q21. 내비 이벤트와 시뮬레이션

### 1-1. 이벤트 수신 — **됨** 🧪
- 조작: 프로브 `SignalProbeActivity`의 “제주 경로” 버튼 → `NaviHelper.requestRoute(33.5059, 126.4929)`
- 결과: 내비 앱(`ai.umos.maps.android.navigation.app`, 3.3.2-rc5-hotfix.3)이 오른쪽 작업 슬롯으로 올라오고 **안내가 바로 시작**됐습니다(별도 “안내 시작” 탭 없음). 스크린샷 `ev/shots/02_after_route_tap.png`, `ev/shots/03_route_preview.png`
- 받은 콜백(`ev/probe-logcat.txt`, 태그 PROBE_NAVI):
  - `onRouteStarted`: duration 39180 s, distance 515000 m, tollFare 12880, name “제주공항 렌터카”, remainDestinationChargingValue 18150
  - `onNavigationStatus` = ROUTE_DRIVE / `onRouteStateInfo.naviMode` = ROUTE_GUIDING (+TrafficStatus 혼잡 구간 목록)
  - `onDrivingInfo` (targetSoc 10), `onDestinationInfo` (address 제주시 용담이동, remainDistance 515000, poiId "", poiProvider "", tollFare 0)
  - `onTBTInfo` count=53 (Q23 참고), `onCurrentLocationInfo`(routeId 포함), `onCurrentRoadSpeedLimit`, `onChargerOperatorInfo`(35개 사업자)
  - `requestTrip` → `onTripInfo`: distance 515789, eta 39225, estimatedSocAtDest 23, energyConsumption 59331.55, routePoints(poiType=NORMAL, pointType=DESTINATION)
  - 두 번째 경로 요청 시 이전 경로에 `onRouteCancelled` → FREE_DRIVE(TBT 0개, naviMode SAFE_GUIDING) → 새 경로 시작 순서로 옴
- 화면: `ev/shots/04_probe_fg_callbacks.png`
- 참고: 출발지는 항상 37.4127212, 127.094681(성남시 수정구)로 고정입니다.

### 1-2. 시뮬레이션(모의 주행) — **안 됨**
| 시도 | 결과 | 근거 |
|---|---|---|
| SDK API | NaviMode는 `RouteStateInfo.naviMode`로 **보고만** 됩니다. 시작 메서드 없음. 프로브의 `simulation_api` 메서드 목록이 비어 있음 | PR #6, `ev/probe-logcat.txt`(sdk_shape) |
| 내비 앱 UI | 메뉴·설정에 모의주행 없음. 설정 항목: 선호 충전소, EV 경로 계획, 유료도로 우회, 어린이 보호구역 우회, 음성 안내, 과속 경고, 버전, 데이터 관리 | `ev/shots/10_nav_menu.png`, `11_nav_right_btn.png`, `12_nav_settings.png`, `13_nav_settings_scrolled.png`, `ev/ui6.xml` |
| `adb emu geo fix <lng> <lat>` 4회 | `OK`는 오지만 navi_engine은 `[mm] org(127.094681,37.4127212:0) … speed(0.00)` 그대로 | `ev/navi_system_excerpt.txt` |
| VC로 기어 D + `PUT /vehicle/controls?speed=40&accelerator=20` 15초 | 위치·속도 변화 없음 (`speed(0.00)`, roadid 미매칭) | 아래 발췌 |

```
$ curl -X PUT 'http://127.0.0.1:8088/vehicle/controls?speed=40&accelerator=20&brake=0&steering=0'   # 10:12 KST
10-24 10:12:27.262 I/navi_engine: [mm] org(127.094681,37.4127212:0), match(127.094681,37.4127212), head(0.00), speed(0.00), roadid(18446744073709551615)
```
- 결론: 에뮬레이터에서는 경로 진행(TBT 거리 감소, 도착 이벤트)을 재현할 수 없습니다. onDestinationArrived 같은 “도착” 계열 콜백은 **미확인**으로 남습니다.
- 대안: 경로 요청 직후의 정지 상태 이벤트로 화면을 만들고, 진행 장면은 프로브 JSONL(로그) 녹화를 재생하는 목업으로 데모합니다.

## 2. Q20 / Q23. 목적지 POI 유형과 TBT 범위

### 2-1. Q20 주유소 판별 — **안 됨**
| 확인 | 결과 | 근거 |
|---|---|---|
| DestinationInfo getter (실제 클래스) | getAddress, getDisplayLat/Lng, getLat/Lng, getName, getPoiId, getPoiProvider, getPoiSubId, getRemainDistance, getRemainTime, getTollFare — **업종·category·poiType 없음** | `ev/probe-logcat.txt`(sdk_shape), PR #6 |
| 실측 값 | poiId ""·poiProvider "" (좌표 경로 요청이라 POI 정보 없음) | onDestinationInfo |
| RoutePointPoiType | NORMAL, EV_CHARGING 두 값뿐 (BookmarkType은 목적지 유형이 아님) | PR #6, 문서 `constants__route-point-poi-type__entry` |
| 충전소 여부 | `DrivingInfo.DrivingState.isChargingStation / isRecommendedChargingStation` (getter 확인) | sdk_shape |
| poiName=“주유소”로 requestRoute | 같은 좌표로 경로만 다시 잡힘. poiName은 표시용 라벨일 뿐 검색이 아님 | `ev/shots/09_gas_station_search.png`, PR #6 |
| 키워드 검색 | `NavigationScreenRequest.SearchPlace`만 있음. 프로브는 `pleos.car.permission.NAVI_CUSTOM_MAP`을 선언하지 않아 “Permission [pleos.car.permission.NAVI_CUSTOM_MAP] is required”로 실패 | probe-logcat |
| 내비 앱 검색 카테고리 | 즐겨찾기 / 충전소 / 음식점 — 주유소 없음 (이미지 차량이 전기차라 그런 것으로 보임) | `ev/shots/09_gas_station_search.png` |
- 대안: 목적지 `name`·`address`에 “주유소”/정유사 브랜드 문자열 매칭. EV는 DrivingState.isChargingStation 사용.

### 2-2. Q23 TBT 범위 — **됨** 🧪
- 경로 시작 시 `onTBTInfo` 한 번에 **53개**가 왔고 마지막 항목은 `{"type":"TURN_ARRIVE","distance":210,"description":"도착"}`입니다. 즉 **다음 몇 개가 아니라 경로 전체**입니다.
- 유형별 개수(제주 경로): TURN_ENTER_SERVICEAREA 13, TURN_RESTAREA 9, TURN_KEEP_R 7, TURN_LEFT 5, TURN_RIGHT 5, **TURN_TOLL 5**(서울·서영암·목포톨게이트 등), TURN_KEEP_L 3, **TURN_EXIT_IC_R 2, TURN_ENTER_IC_R 1**, TURN_AVOID_UNDER_R 1, TURN_ENTER_ROUNDABOUT_12 1, TURN_ARRIVE 1. 이 경로에는 TURN_TUNNEL이 없었습니다.
```
I/PROBE_NAVI: part 1/3 {"tag":"PROBE_NAVI","event":"onTBTInfo","bg":false,"count":53,"items":[{"type":"TURN_LEFT","typeValue":3,"distance":80,...
I/PROBE_NAVI: part 2/3 ...{"description":"죽림IC - 남악 방면"},{"type":"TURN_TOLL","typeValue":40,"distance":17000,"description":"서영암톨게이트"},{"type":"TURN_ENTER_SERVICEAREA",...
```
- 따라서 P단(출발 전)에서 TurnType으로 IC·톨·휴게소 개수를 셀 수 있습니다. 전용 “개수” API는 없습니다(PR #6).
- 주의: `distance`는 직전 안내점부터의 구간 거리로 보입니다(누적 아님, 차량 이동이 안 돼 미확정). 경로 취소 시 TBT 0개 리스트가 옵니다.
- u1 정정: 앞선 중간본의 “Q23 미확인”을 **됨**으로 바꿉니다.

## 3. Q22. 권한과 백그라운드 수신

### 3-1. 권한 — **됨**
명령: `adb shell pm list permissions -f -g` → `ev/perms_selected.txt`, `ev/perms_pleos_all.txt`

| 권한 | 정의 패키지 | protectionLevel | 프로브 부여 |
|---|---|---|---|
| pleos.car.permission.NAVI_ROUTE | ai.pleos.playground.service.navi | normal | ✅ |
| pleos.car.permission.NAVI_ROUTE_SEARCH | 〃 | normal | ✅ |
| pleos.car.permission.NAVI_CUSTOM_MAP / NAVI_CUSTOM_ROUTE / NAVI_CUSTOM_ETC / NAVI_NOTIFY | 〃 | normal | (미선언) |
| pleos.car.permission.CAR_INFO / CAR_ENERGY / CAR_DRIVE | ai.pleos.playground.service.vehicle | normal | ✅ |
| ai.pleos.playground.service.vehicle.VEHICLE_BINDING | 〃 | normal | ✅ |
| android.car.permission.CAR_INFO / CAR_POWERTRAIN / CAR_ENERGY_PORTS | com.android.car.updatable | normal | ✅ |
| **android.car.permission.CAR_ENERGY** | com.android.car.updatable | **dangerous** | 런타임 요청/`pm grant` 후에만 ✅ (그 전엔 false) |
| pleos.car.permission.CAR_POWERTRAIN | — | 이미지에 정의 없음 | — |
- pleos.* 56개 중 normal 47, signature|privileged 8(HID·Wi‑Fi·PIN·ADAS 주차 제어), **dangerous 1(pleos.car.permission.FUSED_LOCATION)**.
- **u1 정정:** “CAR_ENERGY는 normal”은 `pleos.car.permission.CAR_ENERGY`에만 맞습니다. RANGE_REMAINING·EV_BATTERY_LEVEL 등 AAOS 에너지 속성용 `android.car.permission.CAR_ENERGY`는 **dangerous라 런타임 권한 요청 UI가 필요**합니다.
- `CarInfo.getCapabilityInfo`는 `SecurityException getCapabilityInfo need Permission`(필요 권한 이름 미표시)로 실패했습니다.
- 에뮬레이터 포그라운드 사용자는 **user 10**입니다. `pm grant --user 10 io.hmg.appchallenge.foundation android.car.permission.CAR_ENERGY` 처럼 `--user 10`이 있어야 적용됩니다.

### 3-2. 백그라운드 수신 — **안 됨** 🧪
| 시나리오 | 관찰 | 근거 |
|---|---|---|
| 경로 요청 → 내비 앱이 슬롯 차지(프로브는 가려짐) | bg=true로 onCurrentRoadSpeedLimit·EV 배터리 콜백이 약 10초 들어오다가 `freezing 8045 io.hmg.appchallenge.foundation`(10:01:48 KST). `dumpsys activity` isFrozen=true, curProcState=15. 10:02:19 KST에 난 onRouteStarted가 **10:03:13 복귀 시점에야** 전달 | `ev/navi_system_excerpt.txt`, probe-logcat, `ev/shots/06_bg_home.png` |
| 설정 앱을 띄움 | +3초 기어 D 수신, +25초 frozen. 10:07:42 KST 기어 R은 **10:07:57 복귀 때** 전달(CarPropertyManager 이벤트는 해동 순간 bg=true로 기록) | `ev/shots/07_bg_settings.png` |
| HOME 키만 | 프로브가 홈 화면에 계속 보여 얼지 않음(curProcState=2) | dumpsys |
```
10-23 18:01:39.216 I/PROBE_GEAR: {"event":"process_background","bg":true,"listeners":"kept"}
10-23 18:01:48.602 D/ActivityManager(765): freezing 8045 io.hmg.appchallenge.foundation
10-23 18:07:57.645 I/PROBE_GEAR: {"bg":true,"source":"CarPropertyManager.GEAR_SELECTION","name":"GEAR_REVERSE"}
```
- 결론: 리스너는 유지되지만 프로세스가 얼어 **실시간 수신은 안 됩니다**(손실은 아니고 지연·몰아서 전달).
- 대안: 앱이 화면에 남는 구성 유지, 필요하면 포그라운드 서비스 검토(심사 가이드 확인 필요), 복귀(onResume) 때 get*Info·requestTrip으로 상태를 다시 읽습니다.

## 4. Q24. 차량 정보 값 — **됨** 🧪
프로브 시작 덤프(PROBE_INFO, `ev/probe-logcat.txt`), CAR_ENERGY 부여 후:

| 속성 (AAOS) | 값 |
|---|---|
| INFO_FUEL_TYPE | [10] ELECTRIC |
| INFO_FUEL_DOOR_LOCATION | 3 REAR_RIGHT |
| INFO_EV_CONNECTOR_TYPE | [5] COMBO_2 |
| INFO_EV_PORT_LOCATION / INFO_MULTI_EV_PORT_LOCATIONS | 3 REAR_RIGHT / [3] |
| FUEL_LEVEL / INFO_FUEL_CAPACITY | 15000 / 15000 mL (EV 이미지의 더미 값) |
| EV_BATTERY_LEVEL / INFO_EV_BATTERY_CAPACITY | 77400 / 77400 Wh |
| RANGE_REMAINING | 500000 m (access=3 읽기/쓰기, CAR_ENERGY dangerous 필요) |
| CURRENT_GEAR / GEAR_SELECTION | 4 PARK / 4 PARK |

| Pleos SDK (Vehicle 2.3.2) | 결과 |
|---|---|
| Fuel.getFuelPercent | ❌ “property ID is not in carPropertyConfig list…: 0x21400be5” (이미지 미지원) |
| EvBattery.getEvBatteryStateOfCharge | 100 |
| getEvBatteryLevel / getEvBatteryCapacity (deprecated) | 77400 / 77400 |
| Vehicle.getGear (deprecated) | GEAR_PARK |
| CarInfo | VIN·CRN·getCapabilityInfo만 있음(getCapabilityInfo는 권한 오류) |
- PR #6과 일치: 연료 종류·주유구 위치·커넥터·항속거리는 Pleos getter가 없고 AAOS CarPropertyManager로 읽어야 합니다.
- 대안: getFuelPercent 대신 FUEL_LEVEL ÷ INFO_FUEL_CAPACITY, SOC는 EvBattery.getEvBatteryStateOfCharge 또는 EV_BATTERY_LEVEL ÷ INFO_EV_BATTERY_CAPACITY.
- 참고: 차량 제어 코드는 넣지 않았고 읽기만 했습니다.

## 5. 기어 R→D→R — **됨** 🧪
- `cmd car_service inject-vhal-event`는 **user 빌드라 막힙니다**: `SecurityException: The command 'inject-vhal-event' requires non-user build` (빌드: `Pleos/crp_avd_arm64/…:14/RELEASE.release_connect_v2.1.0/…:user/test-keys`, `ev/getprop.txt`).
- 대신 에뮬레이터가 내장한 **Virtual Console 웹 UI/REST**로 주입했습니다(설치 없음). 카탈로그상 gear-selection → VENDOR_GEAR_SELECTION 0x21400400 (`ev/vc_catalog.json`, 현재 상태 `ev/vc_state.json`).
```
adb forward tcp:8088 tcp:8088
while true; do curl -s -X POST 'http://127.0.0.1:8088/browser/session?client=hmgcheck' >/dev/null; sleep 1; done &   # 하트비트 없으면 {"error":"emulator_inactive"}
curl -X PUT 'http://127.0.0.1:8088/vehicle/gear?gear=Reverse'   # Park|Reverse|Neutral|Drive
```
- 수신(10:05:50 / 10:05:54 / 10:05:58 KST 주입):
```
18:05:50.457 bg=false Vehicle.getGear GEAR_REVERSE
18:05:50.838 bg=false CarPropertyManager.GEAR_SELECTION GEAR_REVERSE (raw 2)
18:05:54.641 bg=false Vehicle.getGear GEAR_DRIVE
18:05:55.273 bg=false CarPropertyManager.GEAR_SELECTION GEAR_DRIVE (raw 8)
18:05:58.811 bg=false Vehicle.getGear GEAR_REVERSE
18:05:59.100 bg=false CarPropertyManager.GEAR_SELECTION GEAR_REVERSE (raw 2)
```
- 지연: Vehicle.getGear 약 16~50 ms, CarPropertyManager 약 300~700 ms(PUT 기준).
- R이 되면 EVS 후방 카메라 앱이 작업 슬롯에 자동으로 뜹니다: `ev/shots/08_gear_R_evs_camera.png`. 앱의 R단 화면은 이 카메라에 가려질 수 있다는 점을 UX에 반영해야 합니다.

## 6. 근거 자료 목록
- 스파이크 PR: https://github.com/dohoon84/hmg-app-challenge-2026/pull/6 (브랜치 cursor/spike-navi-probe, 병합 안 함) — NaviHelper 3.0.5 / Vehicle 2.3.2 실제 클래스 확인
- 로그: `ev/probe-logcat.txt`(`adb logcat -s PROBE_NAVI PROBE_GEAR PROBE_INFO`, 912줄), `ev/navi_system_excerpt.txt`(전체 logcat에서 NaviHelper/NaviProvider/freezing 발췌), `ev/emulator-boot.log`
- 권한·패키지: `ev/perms_selected.txt`, `ev/perms_pleos_all.txt`, `ev/pkgs_navi.txt`(NaviService 3.0.4 = ai.pleos.playground.service.navi, Navi42 = ai.umos.maps.android.navigation.app), `ev/navi_service_pkg.txt`
- UI 덤프: `ev/ui1.xml`(프로브), `ev/ui3.xml`, `ev/ui6.xml`(내비 설정)
- 스크린샷 `ev/shots/`: 01_probe_start, 02_after_route_tap, 03_route_preview, 04_probe_fg_callbacks, 05_sim_poll, 06_bg_home, 07_bg_settings, 08_gear_R_evs_camera, 09_gas_station_search, 10_nav_menu, 11_nav_right_btn, 12_nav_settings, 13_nav_settings_scrolled, 14_final_state
- 문서: `docs/txt/docs__connect__guide__getting-started__application-development__use-vehicle-emulator.txt`, `docs__pleos-only__app-test-guide__virtual-console-guide.txt`, configure-pleos-connect-emulator, declaring-permissions, vehicle-permission
- 프로브 JSONL 파일(`/storage/emulated/10/Android/data/io.hmg.appchallenge.foundation/files/probe`)은 `adb pull`·`run-as`(외부 저장소라 불가) 모두 Permission denied였습니다. 같은 JSON이 logcat에 그대로 찍혀 있어 `probe-logcat.txt`로 대신합니다.

## 7. Mac 설치 정보 (MacBook Air, machineId 9b04813e…)
- SDK: `~/Library/Android/sdk` (emulator 33.1.24 → **37.2.12**로 업데이트)
- 시스템 이미지: `~/Library/Android/sdk/system-images/android-34/pleos_car_emulator/arm64-v8a/` (system.img CRC 정상화, 6.5GB)
- AVD: **`Pleos_Connect_API_34`** (`~/.android/avd/Pleos_Connect_API_34.avd`, 2560x1440, RAM 4096M, gpu host, 항상 콜드부트·스냅샷 끔 — 퀵부트는 VHAL이 꺼짐). 기존 AVD 3개는 손대지 않았습니다.
- 실행: `~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_API_34 -no-snapshot-load -no-snapshot-save -gpu host -no-audio` (약 22초 부팅, adb에서 emulator-5554)
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 브라우저로 http://127.0.0.1:8088/ (Motion → Gear Selection)
- 프로브 자료: `~/Downloads/hmg-probe/` (probe.apk, 로그, 스크린샷)
- 주의: Mac 스왑이 거의 가득(8.9/9.2GB)이고 디스크 여유 약 10GB입니다.

## 8. 남은 막힘
1. 주행 시뮬레이션 불가(API·UI 없음, GPS·VC 속도 입력 무시) → 도착·TBT 진행 이벤트 미확인
2. user 빌드라 `cmd car_service` 속성 읽기/주입 불가 → VC REST로 대체
3. 백그라운드 진입 후 약 10초 뒤 프리저로 콜백 지연
4. 프로브에 NAVI_CUSTOM_MAP 미선언 → SearchPlace 미확인(권한 자체는 normal이라 선언만 하면 될 것으로 보임)
5. getCapabilityInfo 권한 이름 불명, getFuelPercent 이미지 미지원
6. 내비 출발지 고정(37.4127212, 127.094681)

---

## 9. 추가 확인 (10/24 오후)

> 작성: 2026-10-24 10:40 KST · 같은 에뮬레이터·프로브(pid 17819)로 실측했습니다. 증거 폴더: `ev/followup/` (스크린샷은 `ev/followup/shots/`)
> 시각 표기: 이번 구간의 에뮬레이터 로그는 **KST(+09:00)** 로 찍혀 있습니다(§0의 PDT 안내와 다릅니다).
> 측정 환경 주의: 에뮬레이터 부하가 매우 높았습니다(게스트 load average 27~30, Mac 스왑 약 10/11 GB). 아래 지연 수치에는 이 부하가 포함되어 있어 실차보다 길게 나왔을 가능성이 큽니다.

### 9.0 CTO 결정 사항 (기록)

- 도착·TBT 진행 표현: 녹화한 프로브 JSON을 재생하고 화면에 **'시뮬레이션'** 캡션을 붙입니다.
- 주유소 판별: 이름·브랜드 문자열 매칭. EV 충전소는 `DrivingState.isChargingStation`을 씁니다.
- 백그라운드: MVP는 **복귀(onResume) 시 재조회**로 갑니다. 포그라운드 서비스 여부는 10/28 이후 결정합니다.

### 9.1 결과 요약

| 항목 | 상태 | 핵심 근거 | 안 될 때 대안 (한 줄) |
|---|---|---|---|
| 1 터널 TBT(TURN_TUNNEL) 🧪 | **안 됨** | 강릉(영동고속도로, 32개)·명동(남산1호터널 경유, 13개) 두 경로 모두 TURN_TUNNEL 0건 | 데모 첫 항목은 **톨게이트(TURN_TOLL)** 로 교체. 터널은 description의 "터널" 문자열로만 추정 |
| 2 분할 화면 | **안 됨** | `supports-split-screen-multi-window=false`, 홈 TaskView는 오른쪽 슬롯에 앱 하나만 표시 | 왼쪽 패널(주행 화면)+오른쪽 슬롯에 우리 앱 단독 표시 구성 |
| 2 우리 앱 표시 + 내비 백그라운드 안내 중 수신 🧪 | **됨** | 약 70초(10:28:41~10:29:52) 동안 기어 5회 모두 bg=false 수신, 프리저 없음 | — |
| 3 R→P 후 자동 복귀 (D3 카드) 🧪 | **안 됨** | P 후 2분 넘게 EVS 카메라 화면이 맨 위에 남음, 프로브는 bg로 P 수신 후 10:30:02 동결 | 사용자가 우리 앱으로 돌아오면 onResume 재조회로 카드 표시(복귀 후 약 1.7초에 그리기 가능) |
| 4a 주유구 FUEL_DOOR_OPEN | **안 됨** | 이 이미지(EV)에 속성 자체가 없음 | EV는 충전구 신호로 대체 |
| 4a 충전구 EV_CHARGE_PORT_OPEN/CONNECTED (시스템 수준) | **됨** | VHAL에 존재, VC 토글이 Pleos Vehicle 서비스까지 전달됨 | — |
| 4a 충전구를 우리 앱이 CarPropertyManager로 읽기 🧪 | **미확인** | 권한은 normal(CAR_ENERGY_PORTS)이지만 프로브가 구독하지 않아 앱 수준 읽기 미검증 | 프로브에 EV_CHARGE_PORT_OPEN 구독 추가 후 재확인 |
| 4b Pleos SDK 충전구/주유구 getter·listener | **안 됨** | EvBattery에 check…Capabilities·set…만 있고 get/register 없음, 주유구 API 없음 | AAOS CarPropertyManager 경로 사용 |
| 4c VC로 값 변경 | **됨** | `PUT /vehicle/ev-battery?field=charge_port_open&value=true/false` → 로그에 value=true/false 반영 | — |
| 5a 남은 거리·시간 필드 🧪 | **됨** | DestinationInfo.remainDistance(m)/remainTime(s), DrivingState.distance/duration, TBT 첫 항목 distance(m) | — |
| 5b 갱신 주기 (정차 중) 🧪 | **됨(정차 중만)** | 남은 거리·TBT는 경로 이벤트 때만 옴(주기적 갱신 없음), 제한속도만 약 1초 주기. **주행 중 주기는 미확인** | 주행 중에는 실차/주행 시뮬레이션 필요 |
| 5c 내비 음성 중 여부 — NaviHelper | **안 됨** | NaviHelper 3.0.5에 음성/TTS 관련 API·콜백 없음 | AAOS 오디오 재생 정보 사용 |
| 5c 내비 음성 중 여부 — AAOS (시스템 수준) | **됨** | `dumpsys audio`에 내비의 NAVIGATION_GUIDANCE 포커스 요청·재생 시작/종료 기록(‑no-audio여도 기록됨) | — |
| 5c 우리 앱이 AudioPlaybackCallback으로 관찰 🧪 | **미확인** | 권한 없이 쓸 수 있는 공개 API이지만 프로브에 미구현 | 프로브에 registerAudioPlaybackCallback 추가 후 재확인 |

### 9.2 터널 (TURN_TUNNEL) — **안 됨**

**TurnType 전체 (ordinal 0~42)**

| 범위 | 값 |
|---|---|
| 0~8 | TURN_UNKNOWN … TURN_UTURN (좌/우/직진/유지 등) |
| 9~14 | TURN_ENTER_IC_R/L/C, TURN_EXIT_IC_R/L/C |
| 15~20 | TURN_ENTER_UNDER_R/L/C, TURN_AVOID_UNDER_R/L/C (지하차도) |
| 21~26 | TURN_ENTER_OVER_R/L/C, TURN_AVOID_OVER_R/L/C (고가도로) |
| 27~35 | TURN_ENTER_ROUNDABOUT_02~12, TURN_EXIT_ROUNDABOUT |
| 36~42 | TURN_WAY, TURN_ARRIVE, **TURN_TUNNEL(38, 터널 관련 유일한 값)**, TURN_DEVIDE_MERGE, TURN_TOLL, TURN_ENTER_SERVICEAREA, TURN_RESTAREA |

- 주의: TBT 항목의 `typeValue`는 ordinal과 다를 수 있습니다(예: TURN_RIGHT=2, ENTER_IC_C=11, ENTER_OVER_L=22, AVOID_OVER_L=25, ARRIVE=37, TOLL=40, 앞선 측정의 RESTAREA=99). 앱에서는 이름(enum)으로 비교하는 편이 안전합니다.

**경로별 TBT 개수** (get*Info 폴링으로 수신, `ev/followup/tbt_routes.txt`, `fu-logcat.txt`)

| 경로 | 조건 | 개수 | 유형별 | TURN_TUNNEL |
|---|---|---|---|---|
| 판교 → 강릉씨티호텔 (10:23:18 KST) | 194 km, 7시간16분, 통행료 6,860원, 영동고속도로(초월IC·원주TG·횡성/평창/강릉대관령휴게소·강릉IC·강릉TG) | 32 | LEFT 5, ENTER_SERVICEAREA 5, RESTAREA 5, RIGHT 4, KEEP_R 3, TOLL 3, ENTER_OVER_L 1, ENTER_UNDER_L 1, ENTER_IC_R 1, ENTER_IC_C 1, EXIT_IC_R 1, KEEP_L 1, ARRIVE 1 | **0** |
| 판교 → 명동거리 (10:25:01 KST, 남산1호터널 경유) | 21 km, 44분 | 13 | RIGHT 5, ENTER_OVER_L 3, TOLL 2, ENTER_IC_C 1, AVOID_OVER_L 1, ARRIVE 1 | **0** |

- 터널은 description 문자열에만 나옵니다: `TURN_ENTER_OVER_L 2100 "남산1호터널 방면"`, `TURN_ENTER_OVER_L 850 "남산1호터널 방면"`, `TURN_TOLL 2300 "남산1호터널톨게이트"`.
- 결론: **TURN_TUNNEL은 실제로 내려오지 않습니다.** 데모 첫 항목은 톨게이트로 바꿉니다. 터널을 꼭 써야 하면 description에 "터널"이 들어간 항목으로 추정할 수 있지만, 터널 진입 지점이 아니라 "방면" 안내라 정확도는 낮습니다.
- 참고: 강릉 경로는 검색 결과에서 고르면 DestinationInfo.poiId가 채워졌습니다(`36kpf223scmfcm5nqhe5`). poiProvider는 빈 값, 업종 필드는 여전히 없습니다.
- 경로를 바꾸면 새 TBT 전에 빈 목록(count 0)이 3번 먼저 옵니다(10:25:00). 앱에서 빈 목록을 "경로 종료"로 오인하지 않도록 해야 합니다.

### 9.3 분할 화면 / 프리저

**분할 화면 — 안 됨**
- `cmd activity supports-multiwindow` = true, `supports-split-screen-multi-window` = **false**.
- 홈(`ai.umos.homescreen`)은 TaskView 런처입니다. 왼쪽 패널은 `ai.umos.drivingview`(0,0~853,1300) 고정, 오른쪽 슬롯(853,90~2535,1300)에는 **앱 하나만** 보입니다. 모든 앱 태스크가 multi-window 모드로 이 슬롯에 들어갑니다.
- `am task resize`로 프로브·내비 태스크 크기를 나눠도 화면에는 프로브만 전체 크기로 보였고 내비는 visible=false였습니다. `am start --windowingMode 5`도 효과가 없었습니다(`shots/f05_split_try.png`). 시험 후 두 태스크 모두 원래 크기(853 90 2535 1300)로 되돌렸습니다.
- 지원되는 구성: **왼쪽 주행 화면 + 오른쪽 슬롯의 앱 하나.** 내비와 우리 앱을 동시에 띄울 수는 없습니다.

**우리 앱을 보이게 두고 내비는 백그라운드에서 안내 중 — 됨** (`vis_test.txt`, `shots/f06_visible_70s.png`)

| 기어 (VC 전송 KST) | 프로브 bg | Vehicle.getGear | CarPropertyManager |
|---|---|---|---|
| D 10:28:41.9 | false | 10:28:42.367 | 42.378 |
| R 10:28:55.8 | false | 56.326 | 56.589 |
| D 10:29:12.05 | false | 12.759 | 15.207 |
| R 10:29:26.4 | false | 28.374 | 34.874 |
| D 10:29:40.3 | false | 41.103 | 42.070 |

- 약 70초 동안 동결 없이(isFrozen=false) 모든 기어 변경을 bg=false로 받았습니다. 지연 0.4~8초는 부하 탓으로 보입니다.
- R을 넣으면 EVS 카메라가 뜨므로, "보이는 상태 유지" 시험은 R 화면이 실제로 뜨기 전까지만 유효합니다. 이 시험에서도 10:29:52에 EVS가 표시되며 프로브가 백그라운드로 내려갔습니다(R 후 +20.9초 표시).
- **새 발견 (Q22 보강):** 오래 동결된 프로세스(이전 pid 8045)가 앞으로 불려 나올 때 binder 버퍼 부족(DeadObjectException "running out of binder buffer space")으로 죽었습니다(10:22:47). 그동안 보류된 콜백(경로 시작 onTBTInfo 등)은 사라졌습니다. → 복귀 시 재조회 결정이 더 중요해졌습니다.

### 9.4 후방 카메라 R → P 복귀 (D3 카드) — **안 됨**

- R을 넣으면 `ai.umos.car.evs/.CarEvsCameraPreviewActivity`가 새 태스크로 뜹니다. 이 앱은 SYSTEM_ALERT_WINDOW 덕분에 백그라운드 실행이 허용됩니다(BAL_ALLOW_SAW).
- P 전송 10:29:53.87 → 프로브는 **bg=true**로 P 수신(getGear 54.377, CarPropertyManager 54.878) → **10:30:02.357 동결**.
- 10:30:37, 10:31:02, 10:31:58(P 후 약 2분)에 확인한 ResumedActivity는 계속 **EVS 카메라(t1000015)** 였고, 프로브 태스크는 visible=false였습니다. 화면에도 P 상태로 카메라 영상이 그대로 남아 있습니다(`shots/f07_after_P.png`, `shots/f08_after_P_70s.png`, 좌측 상단 기어 표시 P).
- 결론: **이 이미지에서는 P로 돌아와도 카메라 화면이 닫히지 않고, 우리 앱도 자동으로 돌아오지 않습니다.** 따라서 "P 직후 자동으로 D3 카드 표시"는 불가합니다.
- 앱이 스스로 돌아올 수 있는지:
  - `ActivityManager.moveTaskToFront`에는 REORDER_TASKS가 필요하고 protectionLevel은 **normal**입니다(`pm list permissions -f`).
  - 하지만 Android 10+ 백그라운드 활동 시작 제한이 moveTaskToFront에도 적용되고, 우리 앱은 SYSTEM_ALERT_WINDOW가 없으며(appops 기본값, 권한은 signature|appop…), 게다가 P 시점엔 이미 동결 직전/동결 상태입니다. **실제로 막히는지는 프로브로 미확인 🧪**이지만 막힐 가능성이 높습니다.
- 사용자가 우리 앱으로 돌아왔을 때(셸 `am start`로 대신 실행, 10:32:28.53 KST):

| 단계 | 시각 | 경과 |
|---|---|---|
| am start | 10:32:28.530 | 0 |
| 프로세스 동결 해제 (quick sync unfreeze) | 10:32:29.600 | +1.07초 |
| process_foreground (bg=false, 리스너 유지) | 10:32:30.279 | +1.75초 |
| 기어 재조회 (CarPropertyManager / Vehicle.getGear) | 30.828 / 31.015 | +2.3 / +2.5초 |
| am start -W TotalTime (HOT) | — | 2,378 ms |

- D3 카드 정리: **P 직후 자동 표시 = 안 됨.** 사용자가 홈/앱 아이콘으로 돌아오면 약 2초 안에(부하 포함) 카드 표시 가능합니다. 대안 한 줄: **onResume 재조회로 카드 표시, P 감지 자체는 다음 포그라운드 때 처리.** (`after_return.txt`, `shots/f09_probe_returned.png`)

### 9.5 주유구 / 충전구 열림 신호

**a) AAOS 속성** (`dumpsys_car_service.txt`, `/workspace/hmg-contest/docs/vpi.html`)

| 속성 | ID | 이 이미지 | 접근 | 읽기 권한 (보호 수준) |
|---|---|---|---|---|
| FUEL_DOOR_OPEN | 0x11200308 | **없음** (AAOS 문서: EV에는 구현하지 않음) | — | CAR_ENERGY_PORTS |
| EV_CHARGE_PORT_OPEN | 0x1120030A | 있음, on-change, 구독 클라이언트 6 | read/write | `android.car.permission.CAR_ENERGY_PORTS` (**normal**) 또는 CONTROL_ENERGY_PORTS |
| EV_CHARGE_PORT_CONNECTED | 0x1120030B | 있음, 구독 클라이언트 2 | read | CAR_ENERGY_PORTS (**normal**) |

- 쓰기는 CONTROL_ENERGY_PORTS(signature|privileged)라 우리 앱은 쓸 수 없고, 써서도 안 됩니다.
- INFO_FUEL_DOOR_LOCATION(0x11400108)은 있지만 위치 정보일 뿐 열림 여부가 아닙니다.
- 앱 수준 읽기는 **미확인 🧪**입니다. 권한·속성 문제가 아니라 **프로브가 이 속성을 구독하지 않아서**입니다. 권한은 normal이고 속성도 있으므로 될 가능성이 높습니다.

**b) Pleos Vehicle SDK 2.3.2**
- EvBattery에는 `checkEvChargePortOpenCapabilities`(CAR_ENERGY)와 `setEvChargePortOpen`(제어, 사용 금지)만 있습니다. **get/register 계열이 없고 주유구 API도 없습니다** → 안 됨.
- 다만 시스템 내부(`[Pleos]b: Vehicle onChangeEvent`)에서는 이 값이 오갑니다. 공개 SDK로 노출된 것은 아닙니다.

**c) Virtual Console 토글 — 됨** (`charge_port_excerpt.txt`, `shots/f10_charge_port_open.png`)
```
10:26:17.668 sdv_vhal_rust  Received updated property: 287310602 from sdv os controller
10:26:18.293 PS_PropertyDocument: Set evChargePortOpen: true
10:26:18.294 [Pleos]b: Vehicle onChangeEvent: propertyId=287310602, areaId=0, value=true
10:26:27.663 … value=false (닫기)
```
- 사용법: 하트비트 유지 중 `PUT /vehicle/ev-battery?field=charge_port_open&value=true|false`. VC 카탈로그상 feature "ev-charge-port" = EV_CHARGE_PORT_OPEN.
- 의미: 앱 수준 읽기가 확인되면 **내비 목적지 없이도 충전 정차를 감지**할 수 있습니다. 내연기관 주유구는 이 이미지로는 검증할 수 없습니다.

### 9.6 남은 거리 · 내비 음성 신호 (주유소 약 300 m 전 음성 안내 목표)

**a) 필드** (`fu-logcat.txt`, NaviHelper 3.0.5 문서. 문서에는 단위 설명이 없어 화면 값과 대조해 단위를 정했습니다)

| 출처 | 필드 | 타입 | 단위 | 정차 중 실측값 (명동 경로) |
|---|---|---|---|---|
| onDestinationInfo / getDestinationInfo | remainDistance | Int | m | 21000 (화면 21 km) |
| 〃 | remainTime | Int | 초 | 2640 (화면 44분) |
| onDrivingInfo → destination(DrivingState) | distance / duration | Int | m / 초 | 21000 / 2640 → 10:29:47에 2820 |
| WaypointInfo (onWayPointInfo) | remainDistance / remainTime | Int | m / 초 (추정) | 경유지 없음(count 0) |
| onTBTInfo / getTBTInfo | items[i].distance | Double | m | 첫 항목 700 (= **다음 안내 지점까지 거리**) |
| onTripInfo (requestTrip 응답) | distance / eta | Int | m / 초 | 515789 / 40470 — 프로브에 고정된 제주 좌표 요청 결과라 현재 경로 값이 아닙니다 |
| onCameraAlert / onCautionPointInfo | remainDistance | Int | m (추정) | 프로브 미구독, 미확인 |

- TBT distance는 **앞 지점에서 다음 지점까지의 구간 길이**입니다. 13개 합계 20,590 m ≈ 남은 거리 21,000 m. 따라서 i번째 지점까지의 거리 = items[0..i].distance의 누적합입니다.
- 주유소가 목적지라면 remainDistance ≤ 300으로 판단하면 됩니다. 경로 중간 주유소는 TBT에 나오지 않으므로(휴게소는 ENTER_SERVICEAREA로 나옴) 경유지로 넣거나 휴게소 TBT를 쓰는 방식이 필요합니다.

**b) 갱신 주기**

| 콜백 | 정차 중 관찰 | 문서 |
|---|---|---|
| onDestinationInfo | 경로 시작/변경 때만 (10:22:57, 10:23:18, 10:25:00, 10:25:06) | "destination information is changed" 때 호출 |
| onDrivingInfo | 경로 시작/변경 때 + 10:29:47(교통 반영으로 duration 2640→2820, 시작 후 약 4분 46초) | "is notified" — 주기 명시 없음 |
| onTBTInfo | 경로 이벤트 때만 + 10:29:48 재전송 | 주기 명시 없음 |
| onCurrentLocationInfo | 경로 이벤트 때만 2회 | 주기 명시 없음 |
| onCurrentRoadSpeedLimit | **약 1초 주기** (397개 구간이 1초에 1건, 몰림은 동결 해제 직후) | 주기 명시 없음 |

- 정차 중에는 남은 거리를 주기적으로 갱신하지 않습니다. **주행 중 갱신 주기는 미확인**입니다(이 이미지에서는 주행 시뮬레이션 불가, §1). 300 m 트리거를 쓰려면 실차/주행 가능한 환경에서 remainDistance·TBT distance가 몇 초 간격으로 오는지 반드시 재측정해야 합니다.

**c) 내비가 지금 말하고 있는가**
- **NaviHelper: 안 됨.** 3.0.5 API·리스너 목록에 음성/TTS/안내 발화 관련 항목이 없습니다(NavigationStatus의 ROUTE_DRIVE = "안내 활성"뿐).
- **AAOS 시스템 수준: 됨** (`dumpsys_audio.txt`, 10:33:47 KST). `-no-audio`로 실행 중이었지만 재생 이벤트는 그대로 기록됐습니다(출력 장치 deviceId 5).
  - 내비(`ai.umos.maps.android.navigation.app`)가 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE / CONTENT_TYPE_SPEECH`로 포커스를 요청하고 놓습니다.
  - 안내 한 번은 **효과음(내비 MediaPlayer piid 247, 약 1초)** → **음성(TTS 엔진 `ai.pleos.playground.caas`의 AudioTrack, 2~3초)** 순서입니다.
  - 예: 강릉 안내 시작 10:22:29.845 효과음 시작 → 10:22:34.158 포커스 요청 → 34.269 음성 시작 → 36.750 종료 → 36.745 포커스 반납. 명동 경로도 10:24:09~11, 10:24:46~47에 같은 패턴.
  - 참고: `ai.umos.drivingview`가 기어 변경마다 USAGE_SAFETY 효과음(piid 63)을 냅니다. 내비 음성과 구분해야 합니다.
- **서드파티 앱이 볼 수 있는 것**
  - `AudioManager.getActivePlaybackConfigurations()` / `registerAudioPlaybackCallback()`: 권한 없이 사용 가능. 일반 앱에는 uid/pid 등 식별 정보가 가려지지만 **AudioAttributes의 usage는 보입니다** → "NAVIGATION_GUIDANCE 재생 중"을 판단할 수 있습니다(누가 재생하는지는 모름). **프로브 미구현이라 미확인 🧪.**
  - 포커스 보유자 목록·`dumpsys audio`는 일반 앱이 볼 수 없습니다(DUMP 권한 필요). 오디오 라우팅 관련 MODIFY_AUDIO_ROUTING은 signature|privileged|role입니다.
  - 우리 음성 안내는 Pleos 오디오 포커스 정책상 내비와 공존하려면 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`으로 요청해야 합니다. 내비 발화 중이면 재생 콜백을 보고 끝난 뒤 말하는 방식을 권합니다.
- 주의: 실제 소리 출력은 `-no-audio`라 확인하지 않았습니다. 재생 이벤트 기록만 근거입니다.

### 9.7 증거 목록 (`ev/followup/`)

| 파일 | 내용 |
|---|---|
| `fu-logcat.txt` | 이번 구간 전체 프로브·ActivityManager 로그 (10:19~10:34 KST) |
| `tbt_routes.txt`, `tbt.py` | 경로별 TBT 개수·유형 집계와 집계 스크립트 |
| `vis_test.txt` | 70초 기어 시험 로그 |
| `after_return.txt` | 프로브 복귀 후 ResumedActivity |
| `dumpsys_car_service.txt` | 속성 설정(EV_CHARGE_PORT_*·FUEL_DOOR 없음) |
| `charge_port_excerpt.txt` | 충전구 토글 로그 발췌 |
| `catalog.json` | VC 기능 카탈로그 |
| `dumpsys_audio.txt` | 오디오 포커스·재생 이력 (`dumpsys_car_audio.txt`는 "Can't find service: car_audio") |
| `nav*.xml`, `n.xml`, `probe1.xml` | uiautomator 덤프 |
| `shots/f00~f10` | 검색·경로·분할 시도·70초 표시·P 후·복귀·충전구 화면 |

### 9.8 남은 막힘 / 다음 할 일

1. 주행 중 remainDistance·TBT 갱신 주기 미확인 → 300 m 트리거의 핵심 전제라 실차/주행 가능 환경 필요.
2. 프로브 보강 필요 🧪: EV_CHARGE_PORT_OPEN/CONNECTED 구독, registerAudioPlaybackCallback, moveTaskToFront 시도, onCameraAlert/onCautionPointInfo 로그.
3. 이 이미지의 EVS는 P 후에도 닫히지 않습니다. 실차 동작(속도 조건·타임아웃으로 닫히는지)은 별도 확인 필요.
4. 장시간 동결 후 프로세스가 죽어 콜백이 사라질 수 있습니다 → 복귀 시 재조회는 필수.
5. 정리 상태: logcat 캡처 종료 확인(`pgrep` 결과 없음), 에뮬레이터는 실행 중으로 두었습니다. 프로브는 오른쪽 슬롯에 떠 있고 내비는 명동 경로 안내 중(백그라운드), VC 기어는 P, 충전구는 닫힘입니다.

---

## 10. 추가 측정 (probe 2차)

> 측정: 2026-10-24 11:02~11:26 KST · 프로브 APK = PR #6 스파이크 브랜치 `cursor/spike-navi-probe` 커밋 `5886154` (**머지 금지**) · 에뮬레이터 emulator-5554 (AVD Pleos_Connect_API_34, user 10)
> 증거: `evidence/navi/round2/` (전체 로그 `r2-logcat.txt`, 11:02~11:26)

### 10.0 CTO 최종 결정 (기록)

1. **onResume 시 재조회는 필수**입니다. (근거 보강: 이번에도 백그라운드 프로브가 수 초 만에 동결되어 로그가 끊겼고, 한 번은 프로세스가 죽고 새로 떴습니다 — 11:02:43 이후 동결, 11:05:14 새 pid.)
2. **TBT 목록이 비어도 안내 종료로 보지 않습니다.** (근거 보강: 경로를 바꾸면 `onRouteCancelled` → FREE_DRIVE → 새 경로 순서로 오고, 11:13:21에는 TBT는 이미 강릉 경로 13개인데 `getDestinationInfo`는 `remainDistance 0, name ""`이었습니다. 하나만 보고 판단하면 틀립니다.)
3. **D3는 onResume 때 보여 줍니다.**

### 10.1 결과 요약

| 항목 | 결과 | 한 줄 요약 |
|---|---|---|
| **B0** 주행 중 남은 거리 갱신 주기 | **미확인** | 이 에뮬레이터에서는 내비 위치를 움직일 방법이 없음(geo fix·D단 모두 무반응). 정지 상태에서는 내비가 스스로 콜백을 보내지 않음. 1초 폴링 응답은 중앙값 1.5초 간격 |
| **B1** 충전구 열림 | **됨** (OPEN) / **안 됨** (CONNECTED — VC에 필드 없음) | VC 토글 → 앱 수신 0.6~1.2초(정상 시), 시스템 부하 중 2.2~5.3초 |
| **B2** 내비 음성 감지 | **됨** | `ASSISTANCE_NAVIGATION_GUIDANCE` / `SPEECH`로 들어옴. 기어 안전음(USAGE_SAFETY)은 콜백에 아예 안 들어와 헷갈릴 일 없음 |
| **B3** 앞으로 가져오기 | **안 됨** | moveTaskToFront·REORDER_TO_FRONT 모두 **Background activity launch blocked (BAL_BLOCK)**. 예외는 안 남 |
| **B4** 카메라·주의 지점 | **미확인** | 리스너 등록은 됐지만 3개 경로에서 이벤트 0건(위치가 안 움직임) |

### 10.2 B0 — 주행 중 remainDistance 갱신 주기 (미확인)

**방법**
- `am start … --ez cadencePoll true --ef destLat 37.4290 --ef destLon 127.1010 --es destName GasTest` (11:02:13). 출발점은 고정 위치 37.4127, 127.0947.
- 경로가 바로 잡혔습니다: `remainDistance 2500`, `remainTime 600`, 화면 "10분 · 2.5km", TBT 5개 `[80, 390, 1500, 150, 370]` (`b0-route.png`).
- VC에는 위치·속도·경로 재생 기능이 없습니다(카탈로그에 GPS/vehicle speed 쓰기 항목 없음, driving-mode는 크루즈 목표속도만). 그래서 아래 세 가지로 움직여 봤습니다(`b0-geo.txt`).

| 시도 | 시간 (KST) | 결과 |
|---|---|---|
| `adb emu geo fix` 1초 간격 41점, 출발점→목적지 직선 (프로브 백그라운드) | 11:03:52~11:04:48 | Android 위치는 바뀜(`dumpsys location`: gps 37.429000, 127.100998). **내비는 그대로** — 이 구간 프로브는 동결되어 로그 없음 |
| geo fix 1초 간격 26점 (프로브 포그라운드, 1초 폴링 중) | 11:06:10~11:06:36 | `remainDistance` 20회 모두 **2500**, TBT 20회 모두 `[80,390,1500,150,370]` |
| VC 기어 D 25초 → P | 11:07:12~11:07:37 | 32회 모두 **2500** |

**관찰된 수치**
- **폴링 없이(cadencePoll false)** 75초 동안: `onDestinationInfo`는 등록 직후 **1회뿐**(11:08:41, deltaSameMs null). → 정지 상태에서 내비는 남은 거리를 주기적으로 푸시하지 않습니다.
- **1초 폴링 중**: `get*Info()` 호출에 대한 응답이 콜백(`origin: callback`)으로 옵니다. `poll_tick` 255회, 응답 간격 `deltaSameMs`:

| 콜백 | n | 최소 | 중앙값 | 평균 | 최대 |
|---|---|---|---|---|---|
| onDestinationInfo | 69 | 556 ms | 1500 ms | 1739 ms | 5282 ms |
| onTBTInfo | 71 | 204 ms | 1387 ms | 1705 ms | 5201 ms |
| onWayPointInfo | 69 | 883 ms | 1452 ms | 1738 ms | 4955 ms |

  이 값은 **폴링 응답 간격**이지 내비가 거리를 새로 계산하는 주기가 아닙니다(에뮬레이터 부하가 커서 1초 요청이 1.5초로 늘어진 것으로 보입니다).
- **거리 해상도(정지 상태 값 기준)**
  - 남은 거리: 2500 (TBT 합계 2490 → **100 m 단위로 반올림된 값**으로 보임), 강릉 192000 (**1 km 단위**), 남은 시간 600 s(10분), 23280 s.
  - TBT 거리: 1 km 미만은 **10 m 단위**(80, 390, 50, 110, 40, 280), 그 이상은 **100 m 단위**(1500, 2100, 13000).
  - 화면 표시 값과 같은 반올림을 쓰는 것으로 보입니다. 300 m 부근에서도 100 m 단위라면 오차는 ±50 m 정도입니다.

**300 m 음성(D2-b) 판단** — (11:45 KST) 트리거는 §10.10에서 "500 m 또는 45초 [가정]"로 바뀌었습니다. 아래는 당시 판단 기록입니다.
- **갱신 주기를 못 쟀으므로 판단 보류(미확인)** 입니다.
- 그래도 지금 확인된 사실로 설계 안전장치를 둘 수 있습니다:
  1. 내비가 알아서 보내는 콜백에만 기대지 말고, 안내 중에는 **`getDestinationInfo()`를 1초마다 직접 폴링**합니다(이번에 1초 폴링이 문제없이 동작함을 확인).
  2. 트리거는 "≤ 300 m가 처음 관측될 때 1회"로 하고, 100 m 반올림을 고려해 "400 m 이하 + 다음 값 ≤ 300 m" 같은 여유를 둡니다.
  3. 고속(예: 80 km/h ≈ 22 m/s)에서 갱신이 5초라면 110 m를 건너뛸 수 있으므로, 실제 주기를 받기 전까지는 "300 m 근처"라는 표현을 씁니다.

**Q28 초안 (10/28 Q&A)** — 최종 문안은 u1-context-signals.md §7 Q28(①모의 주행 ②남은 거리 갱신 ③B4 카메라·주의 지점)에 합쳤습니다.
> Q28. NaviHelper의 `onDestinationInfo` / `getDestinationInfo()`가 주는 `remainDistance`는 **주행 중 몇 초(또는 몇 m) 간격으로 갱신**되나요? 정지 중에는 콜백이 오지 않았습니다. 또 이 값은 화면 표시처럼 반올림(1 km 미만 10 m, 10 km 미만 100 m 등)된 값인가요? 에뮬레이터에서 주행(위치 이동)을 재현할 방법(SIMULATION_GUIDING 진입 API, 위치 주입, VC 경로 재생 등)이 있다면 알려 주세요.

### 10.3 B1 — 충전구 (EV_CHARGE_PORT_OPEN / CONNECTED)

- **초기값** (11:02:18~19): OPEN = `false`, CONNECTED = `false`, 둘 다 status AVAILABLE, 권한 `android.car.permission.CAR_ENERGY_PORTS` 허용, 등록 성공(propertyId 287310602 / 287310603).
- **VC 토글** `PUT /vehicle/ev-battery?field=charge_port_open&value=…` (모두 HTTP 200, `b1-put.txt`). 지연 = VC 요청 시각 → 프로브 `change` 이벤트 시각.

| 회차 | 보낸 시각 (KST) | 값 | 앱 수신 (KST) | 지연 |
|---|---|---|---|---|
| 1차 | 11:14:40.99 | true | 11:14:43.21 | **2.2 s** |
| 1차 | 11:14:48 / 11:14:55 | false / true | 수신 없음 | — (11:15:02 프로브가 밀려남, 11:15:54 이후 `com.android.car`·SystemUI 연쇄 재시작 — 시스템 불안정) |
| 3차 | 11:20:22.13 | true | 11:20:27.40 | 5.3 s (프로브가 onResume 전환 중) |
| 3차 | 11:20:29.72 | false | 11:20:30.30 | **0.6 s** |
| 3차 | 11:20:38.56 | true | 11:20:39.74 | **1.2 s** |
| 3차 | 11:20:46.83 | false | 11:20:47.56 | **0.7 s** |

- **CONNECTED**: VC에 "충전 커넥터 연결" 필드가 없습니다(ev-battery 필드: battery_level, soc, capacity, current_limit, percent_limit, charge_switch, charge_port_open). `charge_switch` true/false(11:21:07, 11:21:16)로도 **CONNECTED는 바뀌지 않았습니다** → CONNECTED 변화는 [미확인].
- 판정: **OPEN 수신 됨**(정상 시 1초 안팎). 마지막 상태는 닫힘(false)으로 돌려놨습니다.

### 10.4 B2 — 오디오 (내비 음성 vs 기어 안전음)

- 프로브는 `registerAudioPlaybackCallback` 등록 성공(11:02:14). usage 표에 `12 = ASSISTANCE_NAVIGATION_GUIDANCE`, `1001 = SAFETY`가 있습니다.
- **내비 음성 (됨)** — 로그에 찍힌 정확한 이름: `usageName: "ASSISTANCE_NAVIGATION_GUIDANCE"`(usage 12), `contentTypeName: "SPEECH"`, `playerStateName: "STARTED"` (`b2-audio-events.txt`)

| 시각 (KST) | 상황 | 이벤트 |
|---|---|---|
| 11:02:36.986, 11:02:39.763 → 11:02:40.014 count 0 | GasTest 경로 안내 시작 | NAVIGATION_GUIDANCE / SPEECH 2회 (차임 + 음성) → 종료 |
| 11:14:12.714, 11:14:12.987 → 11:14:13.143 count 0 | 강릉 경로 | 같은 패턴 |
| 11:14:27.03~.06 → 11:14:27.08 count 0 | 강릉 경로 안내 | 같은 패턴 |

  일반 앱이라 재생 주체(uid/pid)는 `-1`로 가려지지만 usage는 보입니다.
- **기어 안전음** — VC D(11:21:47) / N(11:21:57) / P(11:22:04):
  - `dumpsys audio`에는 `ai.umos.drivingview`의 SoundPool(piid 37519, `USAGE_SAFETY` / `CONTENT_TYPE_SONIFICATION`)이 11:21:49.658, 11:22:00.202, 11:22:07.869에 `event:started`로 남았습니다(`b2-dumpsys-audio.txt`).
  - 그런데 **프로브 콜백에는 한 번도 안 들어왔습니다**(11:07 D/P 때도 0건). SoundPool은 짧게 재생되고 재생 설정 목록 상태가 idle로 남아 콜백이 안 오는 것으로 보입니다.
- 그 밖에 11:20:22~23에 `MEDIA` / `MOVIE`가 여러 번 잡혔습니다(미디어 앱 재시작 시점).
- 판정: **내비 음성 감지 됨.** 기어 안전음과는 usage 자체가 다르고(12 vs 1001), 이 이미지에서는 안전음이 콜백에 오지도 않으므로 **구분 가능**합니다. "usage == ASSISTANCE_NAVIGATION_GUIDANCE 재생 중이면 우리 음성은 기다렸다가 말하기"로 구현하면 됩니다.

### 10.5 B3 — 앞으로 가져오기 (moveTaskToFront / REORDER_TO_FRONT) — 안 됨

순서(`b3-front.txt`, `b3-atm.txt`):

| 시각 (KST) | 일 |
|---|---|
| 11:22:39 | `--ez frontTest true --ez reorderToFront true`로 실행 → `front_test enabled`, `reorder_to_front enabled` |
| 11:23:16 | `KEYCODE_HOME` → **프로브는 그대로 ResumedActivity** (이 런처에서는 홈 키로 오른쪽 슬롯 앱이 내려가지 않음) |
| 11:23:25 | VC R → 11:23:26.874 EVS 카메라(`ai.umos.car.evs/.CarEvsCameraPreviewActivity`)가 위로 뜸 (BAL_ALLOW_SAW_PERMISSION) |
| 11:23:33 | VC P → 프로브 `scheduled` (11:23:37.850, 1.5초 지연), CarPropertyManager 쪽은 `debounced` |
| 11:23:39.783 | `moveTaskToFront` 호출 → **result "called", 예외 없음** |
| 11:23:40.038 | system_server: **`Background activity launch blocked [callingPackage: io.hmg.appchallenge.foundation … callingUidProcState: TOP; callingUidHasAnyVisibleWindow: false …]`** |
| 11:23:40.357 | REORDER_TO_FRONT `startActivity` → **`START u10 … (BAL_BLOCK) result code=3`** |
| 11:23:45.000 | 프로브 `onResume` (`followedAttempt: true`, 5253 ms) — 11:23:45.143 EVS 액티비티가 종료(finishing) 되면서 **밑에 있던 프로브가 드러난 것**이지 호출이 성공한 것이 아님 |
| 11:23:46.065 | 프로브 `onPause` — 최상위는 `DrivingViewActivity` |

- 판정: **안 됨.** 두 방법 모두 Android 14의 백그라운드 액티비티 시작 제한(BAL)에 막히고, 앱 쪽에는 **예외도 결과 코드도 안 옵니다**(조용히 무시). REORDER_TASKS 권한(normal)이 있어도 소용없습니다. → R→P 후 화면 복귀는 **사용자가 앱을 다시 열 때(onResume)** 처리하는 현재 결정(10.0-3)이 맞습니다.
- 참고: 9절에서는 P 후에도 EVS가 닫히지 않았는데, 이번에는 P 약 12초 뒤 닫혔습니다(직후 `CAR.EVS: DeadObjectException` 반복, 시스템 불안정 시기). 실차 동작은 여전히 [미확인]입니다.

### 10.6 B4 — 과속카메라 / 주의 지점 (onCameraAlert / onCautionPointInfo) — 미확인

- 프로브 `road_callbacks`: `["onCameraAlert","onCautionPointInfo","onCurrentRoadSpeedLimit","onCurrentLocationInfo"]` 등록, "NaviHelper 3.0.5에는 다른 도로 정보 리스너 없음".
- 경로: GasTest 2.5 km(11:02~11:11, 이동 시험 포함), 명동(11:11:35 요청 → 기존 경로 `onRouteCancelled`), 강릉 192 km(11:12:35, 화면 "6시간 28분 · 192km", `b4-screen.png`).
- 결과: **onCameraAlert 0건, onCautionPointInfo 0건, onCurrentLocationInfo 0건.** `onCurrentRoadSpeedLimit`은 계속 `speedLimit: 0`.
- 이유: 둘 다 현재 위치가 지점에 가까워질 때 오는 이벤트로 보이는데, 이 에뮬레이터에서는 위치가 안 움직입니다(10.2).
- 그래서 **터널 항목을 대신할 유형 후보를 로그로 뽑지 못했습니다.** → Q&A 질문 후보: "onCameraAlert / onCautionPointInfo의 유형(type) 목록과 발생 거리(몇 m 전), 에뮬레이터에서 재현하는 방법".
- `simulation_api`: "NaviHelper에 NaviMode·시뮬레이션·모의 위치 관련 메서드 없음" — `NaviMode.SIMULATION_GUIDING` 열거값만 있습니다.

### 10.7 시험 환경 상태 (결과 해석에 영향)

- 10:42:00 KST에 Android 런타임이 소프트 재시작했습니다(`boot_progress_start`, system_server pid 765 → 6950). 그래서 9절 끝의 명동 경로 상태는 없어졌습니다.
- 11:15~11:18 KST에 `com.android.car`·SystemUI가 여러 번 죽었다 다시 떴고(`am_proc_died`), mediaapp은 `Car.getCarManager` NPE로 크래시했습니다. 에뮬레이터 부하(load average)는 35~56이었습니다.
- Mac 스왑 사용량은 10 GB 중 9.85 GB였습니다. 메모리가 부족해 지연값(특히 B1의 2~5초)이 늘어났을 수 있습니다. Mac 설정은 바꾸지 않았습니다.
- 앱을 백그라운드로 보내면 수 초 안에 동결되어 콜백 로그가 끊깁니다(11:02:43).

### 10.8 증거 목록 (`evidence/navi/round2/`)

| 파일 | 내용 |
|---|---|
| `r2-logcat.txt` | PROBE_* 전체 로그 (10:56~11:26 KST, 음성 시험 포함) |
| `b0-geo.txt` | geo fix 좌표·시각, D단 시험, 폴링 끈 시험, 경로 요청 시각 |
| `b0-route.png` | GasTest 경로 안내 화면 (2.5 km) |
| `b1-put.txt` | 충전구·charge_switch VC 요청 시각(epoch) |
| `b2-audio-events.txt` | PROBE_AUDIO 재생 이벤트 요약 |
| `b2-gear.txt`, `b2-dumpsys-audio.txt` | 기어 변경 때 `dumpsys audio` (SoundPool USAGE_SAFETY started 기록) |
| `b3-front.txt`, `b3-atm.txt` | 앞으로 가져오기 시험 순서와 ActivityTaskManager 로그 (BAL 차단) |
| `b3-after-P.png` | P 후 화면 (프로브 오른쪽 슬롯) |
| `b4-screen.png` | 강릉 경로 화면 |

### 10.9 정리 상태

- logcat 캡처와 VC 하트비트를 껐습니다(`pgrep` 결과 없음 확인).
- 에뮬레이터는 켜 둔 채로 두었습니다.
  - VC 기어는 P, 충전구는 닫힘(false), charge_switch는 false입니다.
  - 내비는 강릉 경로를 안내하는 중입니다.
- 주행 화면에 "프렁크를 닫으세요"가 떠 있습니다. VC 상태에도 `frunk: true`가 있습니다. 이번 작업에서 body/frunk 값은 건드리지 않았습니다.

### 10.10 결정 메모 (2026-10-24 11:45 KST)

| # | 결정 | 근거 / 비고 |
|---|---|---|
| 1 | **D2-b 트리거 변경:** 300 m → **`remainDistance` ≤ 500 m 또는 `remainTime` ≤ 45초 중 먼저 오는 때, 경로당 1회** **[가정]** | 주행 중 갱신 주기를 못 쟀고(§10.2), 남은 거리가 100 m 단위로 반올림되며 1초 폴링 응답이 1.5초로 늘어졌습니다. 고속에서는 시간 조건이 먼저 걸려 늦게 말하는 일을 막습니다. 500 m·45초 값 자체는 실차/주행 환경에서 다시 정합니다(Q28 ②) |
| 2 | **`getDestinationInfo()`는 주행 중에만 1초 간격 폴링** | 정차 중에는 내비가 스스로 보내지 않고(§9.6), 폴링도 필요 없습니다. P·N 또는 경로 안내가 아닐 때는 폴링을 멈춥니다 |
| 3 | **내비 음성이 나오는 중이면 끝날 때까지 기다림, 최대 5초** | `AudioPlaybackCallback`에 usage `ASSISTANCE_NAVIGATION_GUIDANCE`가 활성인 동안 대기(§10.4 B2). 5초가 지나도 끝나지 않으면 이번 안내는 건너뜁니다. 문장은 녹음 클립 "잠시 후 주유소(충전소)에 도착해요." + 250 ms + "주유구(충전구)는 {왼쪽/오른쪽}이에요."(voice-check.md §3-d, 거리 숫자를 말하지 않음) |
| 4 | **B4(onCameraAlert / onCautionPointInfo)는 다시 시도하지 않음 → Q28 ③으로 이관** | §10.6에서 0건. 주행 재현이 안 돼 재시도해도 의미가 없습니다. 터널 항목을 대신할 후보인지(유형 목록·발생 거리)는 10/28 Q&A로 묻습니다(u1-context-signals.md §7 Q28) |
| 5 | **추가 측정 중단** | Mac 스왑이 가득 찼고(10 GB 중 9.85 GB, §10.7) 대표님이 Mac을 쓰고 있어 에뮬레이터·Mac은 건드리지 않습니다. B0 갱신 주기는 [미확인]으로 남깁니다 |
| 6 | **프렁크 값은 그대로 둠** | VC의 `frunk: true`("프렁크를 닫으세요" 표시, §10.9)는 이번 작업에서 생긴 것이 아니며 바꾸지 않습니다 |

- 이 메모가 §9.6 제목·§9.8 1번·§10.2 "300 m 음성 판단"의 "300 m"를 대신합니다(앞 절은 기록으로 남겨 둠).
- 관련 문서: u1-context-signals.md v1.7(§3 2번 행, §7 Q26·Q28·Q30), voice-check.md §3-d(녹음 문장 목록).
