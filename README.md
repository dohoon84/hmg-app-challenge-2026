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
| 제출 APK 파일명 | 빌드 후 `apk/placeholder-team_foundation_release.apk` (git에는 없음) | `apk/팀명_앱이름_release.apk` |

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

릴리스 서명은 `keystore.properties`(저장소 루트, git 제외) 또는 환경 변수에서 읽습니다. 키 이름은 `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`입니다. `STORE_FILE`은 프로젝트 루트 기준 경로입니다. 네 값이 모두 있고 키스토어 파일이 있을 때만 release가 서명됩니다. 하나라도 없으면 release APK는 서명되지 않으며 설치할 수 없습니다. 그 경우 debug 변형으로 실행합니다. debug 키로 release를 서명하지 않습니다.

`*.jks`, `*.keystore`, `keystore.properties`, `signing.properties`는 git에 올리지 않습니다. 제출 APK도 마감 직전에 한 번만 커밋합니다. 지금은 `apk/.gitkeep`만 있습니다.

Vehicle / NaviHelper / Car 연결은 `ProcessLifecycleOwner`의 시작과 중지에 묶여 있습니다. 프로세스가 백그라운드로 가면 `release()`와 `Car.disconnect()`가 호출되고, 다시 전면에 오면 새 `Vehicle`과 새 NaviHelper로 다시 시작합니다.

백그라운드에서 SDK를 유지할지는 아직 정하지 않았습니다. 지금 방식은 백그라운드로 나간 뒤 약 700ms가 지나면 SDK를 해제하므로, 그 동안의 도착·기어·도어 이벤트는 받지 못합니다. 운행 기록이나 도착 트리거가 필요하면 아이디어가 확정된 뒤에 foreground service로 옮길지 결정합니다. Phase 0은 포그라운드에서만 SDK를 붙입니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 대상 기기 | Pleos Connect AVD, API 34, Automotive, arm64, 콜드 부팅 |
| 시스템 이미지 | `crp-arm64-sys-img.xml` (위 URL) |
| 개발 PC | Apple Silicon Mac, Android Studio |
| minSdk / targetSdk / compileSdk | 28 / 34 / 34. Car 리스너 API는 30 이상에서만 동작합니다. |
| 언어·UI | Kotlin, Jetpack Compose, MVVM, Coroutines/Flow, Hilt, Room |
| 이 저장소를 빌드한 환경 | Linux VM, JDK 21, Android SDK platform 34. Pleos AVD는 여기서 실행하지 않습니다. |

fake 화면에서 확인할 것:

- 정차: 세로 1열, 가로(또는 너비 800dp 이상) 2열. 좌표 입력은 정차 화면에만 있습니다.
- 주행: 큰 버튼(높이 88dp)만 있는 화면. 키보드 없음. 시뮬레이션 빌드의 버튼은 높이에 맞춰 1~3열로 배치되어 세로와 가로 모두에서 닿습니다. 한 열일 때는 22sp 한 줄이고, 두 열 이상에서 버튼을 나눠 가질 때는 18sp로 두 줄까지 표시합니다. 화면이 그 열보다 낮을 때만 동작 영역이 스크롤됩니다.
- 운전석 문 열림, SOC ±10, 도착 이벤트.

주행 규제 adb (에뮬레이터):

```bash
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
adb shell cmd hmg_car_service --service drivingstate driving_regulation off
```

## APK 위치

git에는 APK를 넣지 않습니다. 아래 경로는 로컬 빌드 결과입니다.

| 용도 | 경로 |
|---|---|
| 제출용 pleos release | 키스토어가 있을 때 `./gradlew exportContestApk` → `apk/placeholder-team_foundation_release.apk` |
| 서명된 Android Studio 산출물 | `app/build/outputs/apk/pleos/release/app-pleos-release.apk` |
| 키스토어 없이 만든 release | `app/build/outputs/apk/pleos/release/app-pleos-release-unsigned.apk` (제출하지 않음) |
| 폰에서 볼 fake debug | `app/build/outputs/apk/fake/debug/app-fake-debug.apk` |
| 에뮬레이터용 pleos debug | `app/build/outputs/apk/pleos/debug/app-pleos-debug.apk` |

제출하는 파일은 서명된 pleos release뿐입니다. fake debug APK와 unsigned release는 제출하지 않습니다. `fakeRelease` variant는 꺼 두었습니다. `checkPleosReleaseContents`는 pleos release APK dex에 fake 클래스 이름이 있으면 실패하고, `exportContestApk`는 그 검사에 의존합니다. 서명이 없으면 `exportContestApk`는 release 빌드를 시작하기 전에 실패합니다.

## 성능 예산

런타임으로 강제하지 않습니다. 심사 기준은 주행 중 CPU 5% 미만, 정차 중 25% 이하, RAM 400MB 이하, 터치 반응 0.25초, 콜드 스타트 10초 이내입니다. debug 빌드는 프로세스 시작부터 첫 프레임까지를 `LaunchTiming`으로 로그합니다.

```bash
adb shell dumpsys cpuinfo
adb shell top -n 1
adb shell dumpsys meminfo io.hmg.appchallenge.foundation
adb shell am start -W -n io.hmg.appchallenge.foundation/.MainActivity
adb shell am start -W -n io.hmg.appchallenge.foundation.fake/io.hmg.appchallenge.foundation.MainActivity
adb shell dumpsys gfxinfo io.hmg.appchallenge.foundation
```

## 구성

- `VehicleRepository`: 시작마다 새 `Vehicle`을 만들어 `initialize`하고, 중지할 때 `release`합니다. 도어, 창문, 연료 %, SOC, 적산거리(km), VIN. 각 항목은 `check*Capabilities`가 READ 또는 READ_WRITE일 때만 get/register 합니다. 좌석은 점유·벨트 capability만 조회하고, 값이 없거나 뒷좌석이 목록에 없으면 미지원입니다. 시트 이동, HVAC, 조명 같은 Vehicle Control API는 호출하지 않습니다.
- `DrivingStateRepository`: AAOS `VehiclePropertyIds.GEAR_SELECTION`과 `CarUxRestrictionsManager`. 기어 P이고 주의 분산 최적화가 필요 없으면 정차, 그 외 알려진 기어이거나 UX가 최적화를 요구하면 주행입니다. pleos는 기어가 UNKNOWN이거나 UX를 아직 못 읽으면 축소 화면입니다. 그때 문자열 제한은 AAOS `car_ux_restrictions` 기본값 120자(`UNKNOWN_MAX_STRING_LENGTH`)이고, 0자 이하는 문구를 비우지 않습니다. 문 열림 경고는 자르지 않습니다. 키보드는 `UX_RESTRICTIONS_NO_KEYBOARD` 비트가 있으면 숨깁니다. `Vehicle.getGear()`는 호출하지 않습니다. `Car.createCar` 리스너와 `VehiclePropertyIds`는 API 30/29입니다. minSdk 28 기기에서는 Car API를 호출하지 않고 축소 화면을 유지합니다. 공모전 이미지는 API 34입니다.
- `NavigationRepository`: `requestRoute(RouteInfo)`로 좌표 목적지, `onDestinationInfo` / `onDestinationArrived`, `requestTrip` → `onTripInfo`.
- Room `foundation.db`: 마지막 차량 스냅샷과 내비 캐시 한 행. 스키마는 `app/schemas`에 내보냅니다. 이 테이블은 캐시라서 버전을 올리면 `fallbackToDestructiveMigration()`으로 지웁니다. 아이디어 데이터를 영구 저장하기 전에 명시적 마이그레이션으로 바꿔야 합니다. VIN은 로컬 DB와 화면에 끝 4자리만 남기고 저장·표시합니다.
- 주행 중 화면은 Activity `distractionOptimized=true` 위에서 큰 버튼만 보여 줍니다. 선언만 하고 긴 목록이나 키보드를 그대로 두지 않습니다.
- 최상위에서 시스템 Back은 Activity를 끝내지 않고 `moveTaskToBack(true)`입니다. 화면 안의 Back 버튼은 없습니다. 하위 화면이 생기면 그 화면이 보이는 동안 루트 콜백의 `isEnabled`를 끕니다. CCP 로터리 메타데이터 `com.hmg.rotary.ROTARY_SUPPORTED`는 false입니다.
- 좌표 입력 칸은 `rememberSaveable`, 검증 오류 문구는 `SavedStateHandle`에 둡니다.
- 창은 일반 Activity입니다. 기본은 세로 840×1184, 가로는 1700×1184이고 단위는 dp입니다. 주행 뷰 840×1280은 시스템 영역이라 앱이 덮지 않습니다. 회전은 구성을 다시 만들며 입력과 검증 오류는 유지됩니다. 서체는 Asta Sans 가변 폰트 한 파일입니다(OFL, `res/font/asta_sans.ttf`, 약 5.5MB). 웨이트 400/700/800은 `wght` 축입니다. 예전 정적 파일 세 개는 OS/2 웨이트가 실제로 400/700/800이었고, 한글 음절이 글리프의 대부분이라 크기가 거의 같았습니다(합계 7,790,400바이트). 서브셋은 라틴, 문장부호, 한글 음절이고 OFL 고지는 `assets/asta-sans/OFL.txt`에 둡니다. 글자 토큰은 `ivi_`입니다. 색의 단일 출처는 `res/values/colors.xml`과 `res/values-night/colors.xml`이고 Compose는 그 리소스를 읽습니다. 오류 빨강은 앱 정의 `ivi_informative_negative`이자 `ivi_status_error`입니다(라이트 #BE250A, 다크 #FF4C28, 공식 Pleos 토큰 아님). 라이트 주의 글자는 `ivi_status_caution`(#9A4A00)이고, 주의 아이콘 전용은 `ivi_status_caution_icon`(라이트 #BD6600, 다크 #FF8A00, 글자에 쓰지 않음)입니다. 라이트 글자·아이콘에 #FF8A00·#FF4C28을 쓰지 않습니다. 주행 문 열림 경고는 예외로 `ivi_driving_alert`(#FF8A00, 라이트·다크 동일)입니다. 가변 폰트 기본 wght는 300이라 XML에서 variation 없이 쓰면 Light입니다. 라이트와 다크를 모두 그립니다. 버튼은 Large 100 / Medium 80 / Small 64dp이고, 축소 화면의 Small은 Medium으로 올립니다. 주행·정차 액션은 Large만 요청합니다. 눌림은 95%로 80ms 안에 반응합니다. 공식 아이콘 42종은 2026-10-27 이후에 넣습니다. Figma "Official Icons (Pleos)"를 그 때 봅니다.
- 안내 음성은 Pleos TTS SDK를 쓰지 않습니다. `android.speech.tts.TextToSpeech`는 포그라운드로 돌아올 때 미리 묶고, 준비 전에는 포커스를 받은 시점부터 최대 1.5초(앞 무음 175ms를 포함한 값)를 기다린 뒤 차임으로 넘깁니다. 백그라운드에서는 메모리 예산 때문에 `shutdown` 합니다. 오디오 속성은 포커스와 같이 `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SPEECH`입니다. 내비 안내 usage는 쓰지 않습니다. 우리 발화를 내비 겹침으로 보면 안 되기 때문입니다. 엔진이 없거나 초기화·재생이 실패하거나 비동기 `onError`가 오면 `res/raw` 차임으로 넘깁니다. 차임 content type은 `SONIFICATION`입니다. 둘 다 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, 175ms 앞 무음입니다. 발화나 차임이 끝나면 포커스를 놓고, 다음 안내 전에 이전 요청을 abandon 합니다. 내비 안내·어시스턴트가 겹치거나 포커스를 잃으면 즉시 중지합니다. `LOSS_TRANSIENT_CAN_DUCK`도 끊습니다.

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
- 홈으로 나갔다가 복귀하거나 프로세스를 다시 띄울 때 리스너가 중복되지 않는지 (`adb shell dumpsys car_service`)
- CarService가 끊겼다가 다시 붙을 때 화면이 축소됐다가 정차 신호와 함께 복구되는지
- 세로/가로 전환 시 좌표 입력이 유지되는지
- 팀 키로 서명한 release APK의 설치와 실행
- 좌석 capability가 실제로 어느 열을 돌려주는지, TTS 엔진 유무, 내비 안내와 겹칠 때의 오디오 포커스
- 콜드 스타트 10초, 주행 CPU, RAM 예산의 실측

## 10/27 이후 권한 정리

읽기 권한은 `app/src/pleos/AndroidManifest.xml`에만 있습니다. fake 빌드에는 합쳐지지 않습니다. 승인 목록은 2026-10-27 이후에 나오므로, 지금 1:1 대조는 할 수 없습니다. 목록에 없는 줄은 그때 매니페스트와 `allowedPleosReleasePermissions`에서 함께 지웁니다. 아래 여섯 Pleos Vehicle 권한과 내비·AAOS 줄은 모두 읽기이고 제어 API를 부르지 않습니다. `CAR_SEATS`는 좌석 점유·벨트 capability 조회가 필요해서 남깁니다. 조회를 빼면 권한도 뺍니다. Vehicle Control, Pleos TTS, STT, FusedLocation, `CAR_LIGHTS`, `CONTROL_CAR_*`, `PERF_VEHICLE_SPEED`, `PRIVATE_CAR_SAFETY`는 선언하지 않습니다. 정차 판정에 속도를 쓰지 않습니다. `android.car.permission.CAR_ENERGY`는 `RANGE_REMAINING`을 쓸 때만 남기고, 빼면 매니페스트와 권한 버튼도 같이 제거합니다. `checkPleosReleaseContents`가 합쳐진 매니페스트의 uses-permission을 허용 목록과 비교합니다.

| 권한 | 쓰는 곳 | 정리 |
|---|---|---|
| `pleos.car.permission.CAR_DOORS` | 도어 위치 읽기 | 제어 권한이 아님. 문을 안 쓰면 제거 |
| `pleos.car.permission.CAR_WINDOWS` | 창문 위치 읽기 | 창문 제어는 Coming Soon. 창문을 안 쓰면 제거 |
| `pleos.car.permission.CAR_ENERGY` | 연료·SOC 읽기 | 배터리를 안 쓰면 제거 |
| `pleos.car.permission.CAR_DRIVE` | 적산거리 읽기 | 주행 제어가 아님. 적산을 안 쓰면 제거 |
| `pleos.car.permission.CAR_INFO` | VIN 읽기 | VIN을 안 쓰면 제거 |
| `pleos.car.permission.CAR_SEATS` | `checkSeatOccupancyCapabilities`, `checkSeatBeltBuckledCapabilities` | 상태 읽기. 시트 이동·HVAC가 아님. 조회를 유지하는 동안 유지. 승인 목록에 없으면 제거 |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | `requestRoute` | 경로 안내를 안 쓰면 제거 |
| `pleos.car.permission.NAVI_ROUTE` | 목적지·도착·ETA | 내비를 안 쓰면 제거 |
| `android.car.permission.CAR_POWERTRAIN` | `GEAR_SELECTION` 읽기 | 정차 판정을 유지하는 동안 유지 |
| `android.car.permission.CAR_ENERGY` | `RANGE_REMAINING` | 위험 권한. 주행가능거리를 안 쓰면 제거 |
