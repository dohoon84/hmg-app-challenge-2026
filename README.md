# Foundation (가칭)

현대차그룹 차량용 앱 개발 공모전(Pleos Connect / AAOS) Phase 0 공통 기반입니다. 앱 아이디어는 2026-10-27에 확정할 예정이므로, 화면과 저장소는 특정 기획에 묶여 있지 않습니다. 표시 이름 `Foundation`과 패키지 `io.hmg.appchallenge.foundation`은 자리표시자이며, 제출 전에 팀명과 앱 이름으로 바꿉니다.

- 공모전: https://appdevchallenge.auto-hmg.io (마감 2026-11-15 23:59 KST)
- Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`
- NaviHelper `ai.pleos.playground:NaviHelper:3.0.5`
- Maven: https://nexus-playground.pleos.ai/repository/maven-releases/

## 앱 이름

| 항목 | 현재 값 | 제출 전 |
|---|---|---|
| 표시 이름 | Foundation | 확정된 앱 이름 |
| applicationId | `io.hmg.appchallenge.foundation` | 팀 패키지 |
| 제출 APK 파일명 | `apk/placeholder-team_foundation_release.apk` | `apk/팀명_앱이름_release.apk` |

## 실행 방법

Apple Silicon Mac의 Android Studio에서 엽니다. JDK 17 이상, Gradle 8.11.1.

1. Pleos Connect AVD를 추가합니다. 시스템 이미지: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml (API 34, Automotive). 콜드 부팅합니다.
2. Build Variant를 고릅니다.
   - `pleosDebug`: 에뮬레이터 또는 실차. Vehicle SDK, NaviHelper, AAOS `GEAR_SELECTION`, `CarUxRestrictions`를 사용합니다.
   - `fakeDebug`: Pleos 없이 폰이나 데스크톱 에뮬레이터에서 UI를 확인합니다. 정차/주행, 운전석 문, SOC, 도착을 큰 버튼으로 바꿉니다.
3. Run.

명령:

```bash
./gradlew assembleDebug
./gradlew testFakeDebugUnitTest testPleosDebugUnitTest
./gradlew assemblePleosRelease
./gradlew exportContestApk
```

`assembleDebug`는 `fakeDebug`와 `pleosDebug`를 모두 만듭니다. `fake`는 `android.hardware.type.automotive`가 필수가 아니라서 일반 기기에 설치됩니다. `pleos`는 오토모티브 기기 전용입니다.

릴리스 APK는 팀 키스토어가 생기기 전까지 debug 키로 서명합니다. 제출 전에 서명 설정을 교체하세요.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 대상 기기 | Pleos Connect AVD, API 34, Automotive, arm64, 콜드 부팅 |
| 시스템 이미지 | `crp-arm64-sys-img.xml` (위 URL) |
| 개발 PC | Apple Silicon Mac, Android Studio |
| minSdk / targetSdk / compileSdk | 28 / 34 / 34 |
| 언어·UI | Kotlin, Jetpack Compose, MVVM, Coroutines/Flow, Hilt, Room |
| 이 저장소를 빌드한 환경 | Linux VM, JDK 21, Android SDK platform 34. Pleos AVD는 여기서 실행하지 않습니다. |

fake 화면에서 확인할 것:

- 정차: 세로 1열, 가로(또는 너비 800dp 이상) 2열. 좌표 입력은 정차 화면에만 있습니다.
- 주행: 큰 버튼만 있는 화면. 키보드 없음. 시뮬레이션 빌드에서는 정차로 돌아가는 버튼이 같이 있습니다.
- 운전석 문 열림, SOC ±10, 도착 이벤트.

주행 규제 adb (에뮬레이터):

```bash
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
adb shell cmd hmg_car_service --service drivingstate driving_regulation off
```

## APK 위치

| 용도 | 경로 |
|---|---|
| 제출용 pleos release | `apk/placeholder-team_foundation_release.apk` |
| Android Studio 산출물 | `app/build/outputs/apk/pleos/release/` |
| 폰에서 볼 fake debug | `app/build/outputs/apk/fake/debug/app-fake-debug.apk` |
| 에뮬레이터용 pleos debug | `app/build/outputs/apk/pleos/debug/app-pleos-debug.apk` |

## 구성

- `VehicleRepository`: `Vehicle.initialize` / `release`. 도어, 창문, 연료 %, SOC, 적산거리(km), VIN. 각 항목은 `check*Capabilities`가 READ 또는 READ_WRITE일 때만 get/register 합니다.
- `DrivingStateRepository`: AAOS `VehiclePropertyIds.GEAR_SELECTION`과 `CarUxRestrictionsManager`. 기어 P이고 주의 분산 최적화가 필요 없으면 정차, 그 외 알려진 기어이거나 UX가 최적화를 요구하면 주행입니다. `Vehicle.getGear()`는 호출하지 않습니다.
- `NavigationRepository`: `requestRoute(RouteInfo)`로 좌표 목적지, `onDestinationInfo` / `onDestinationArrived`, `requestTrip` → `onTripInfo`.
- Room `foundation.db`: 마지막 차량 스냅샷과 내비 캐시 한 행. 아이디어별 테이블은 아직 없습니다.
- 주행 중 화면은 Activity `distractionOptimized=true` 위에서 큰 버튼만 보여 줍니다. 선언만 하고 긴 목록이나 키보드를 그대로 두지 않습니다.

주행가능거리는 Vehicle SDK에 getter가 없습니다. `setRangeRemainingDistance`는 deprecated라 쓰지 않습니다. pleos flavor만 AAOS `RANGE_REMAINING`(위험 권한 `CAR_ENERGY`)을 시도하고, 없거나 권한이 없으면 "SDK 미제공"으로 둡니다. fake flavor는 시뮬레이션 값을 보여 줍니다.

## 바이너리와 문서가 다른 점

Vehicle 2.3.2 AAR을 풀어 확인했습니다.

- `getDoor()` 등 도메인 getter는 JVM에서 public인데, Kotlin 메타데이터는 해당 프로퍼티를 private으로 표시합니다. pleos 소스는 Java 브리지 `VehicleDomains`로 호출합니다.
- `VehicleListener`에 `onFailed(Exception)`이 있어 모든 리스너가 구현합니다. 문서 발췌에는 콜백 메서드만 보였습니다.
- 문서의 `VehicleException`은 AAR 안에서 `callback.exception.a`로 난독화되어 있습니다. 실패 메시지만 저장합니다.
- `Vehicle.getGear()`는 AAR에 남아 있지만 호출하지 않습니다.

## 에뮬레이터에서 아직 확인하지 못한 것

이 VM은 Pleos AVD를 실행하지 못합니다. 유닛 테스트와 `assembleDebug`로 확인한 범위는 PR 본문에 적습니다. 에뮬레이터에서 직접 볼 항목:

- NaviHelper `initialize` 성공 여부와 `onDestinationArrived` / `onTripInfo` 발생 여부
- `GEAR_SELECTION`, `RANGE_REMAINING` 노출 여부와 `CAR_ENERGY` 동의 UX
- 도어 position `0` = 닫힘이라는 해석 (레퍼런스는 Int만 정의)
- `RouteInfo.poiId`를 빈 문자열로 둬도 경로 요청이 되는지
- `TripInfo` / `DestinationInfo` 정수 단위 (거리 m, 시간 s로 읽는 근거는 검토 문서. 필드 페이지에는 단위 문장이 없음)
- `Vehicle.initialize()`에 연결 콜백이 없어, 반환 성공을 ready로 보는 해석
