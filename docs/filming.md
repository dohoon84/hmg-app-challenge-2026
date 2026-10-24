# 촬영 모드

디버그 빌드에서만 켜는 촬영 모드입니다. 릴리스에는 `BuildConfig.FILMING`이 `false`이고, 수신기도 매니페스트에 없습니다. 새 권한은 없습니다. 화면 버튼은 없습니다.

촬영이 켜지면 테마는 시스템 설정과 상관없이 다크로 고정됩니다. 디버그 패널, 길게 누르기, `값 적용`, 데모 값, 자막, 하이라이트, 점선 `반납 장소로 안내`, 기초 화면 오버레이는 숨습니다. 자막은 편집에서 올립니다. 앱 안에는 넣지 않습니다. 제품 화면은 평소 다크 모드와 같은 배치입니다.

자동 재생은 실제 기어가 P일 때만 시작하고, P를 벗어나면 바로 멈춘 뒤 실제 차량 값으로 돌아갑니다. 스크립트가 바꾸는 기어는 이 조건에 들어가지 않습니다. 순서와 카드 내용은 벽시계와 실제 내비 목적지에 기대지 않습니다. D1은 톨게이트 2곳 · 고속도로 진입 1번의 데모 경로이고, D2는 그 다음의 데모 충전소입니다.

앱을 연 뒤 브로드캐스트를 보냅니다. 프로세스가 아직 없으면 명령은 화면이 뜰 때까지 기다립니다.

플레오스 패키지는 `com.krazylabs.miri`입니다. fake 빌드는 `com.krazylabs.miri.fake`로 바꿔 같은 명령을 보냅니다.

CF 녹화 전에 촬영을 켭니다.

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.FILMING --ez on true
```

끄기:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.FILMING --ez on false
```

## storyboard v1

프로필 이름은 `storyboard-v1`입니다. 컷 표는 2560×1440이고, 앱이 만드는 화면만 스텝으로 둡니다. C1의 닫힘 확인 1.5초 때문에 앱 구간은 표의 80초보다 0.5초 깁니다. 각 스텝은 그 컷의 길이만큼 유지됩니다. 앞 컷은 대기 없이 적용되어 차 상태를 맞춘 뒤, 고른 스텝부터 보입니다. 스텝을 빼면 앱 컷이 F1부터 C2까지 이어집니다. 편집 카드는 끼워 넣지 않습니다.

`seconds`는 시작하는 컷의 길이를 초 단위로 바꿉니다. C1은 문 열림 6초와 닫힘 확인 1.5초라, 초를 바꾸면 그 비율을 유지한 채 합이 그 초가 됩니다. `hold`는 그 컷부터의 배수이고, 없거나 0 이하면 1입니다. 컷 표 그대로 찍을 때는 둘 다 빼면 됩니다.

F2와 F3는 같은 반납 시각을 씁니다. F2에 넣는 값은 모레 12:00이고, F3의 `반납`도 12:00입니다. 출발은 그 시각에서 이동 42분과 여유 15분을 뺀 11:03입니다. 날짜가 보이면 F2와 같습니다.

SOC는 앱 카드가 80입니다. 충전 컷만 60이고, 그 값은 왼쪽 패널과 같게 VC에 씁니다. 앱은 `EvBatteryStateOfCharge`가 있으면 그 퍼센트를 그리고, 없으면 그 컷의 80 또는 60을 씁니다. 쓰는 값은 80과 60뿐입니다. 반납 기준은 80입니다. C2의 주행거리는 가짜 적산거리의 차이입니다. 시작 값은 12000이고, 주행 기어로 바뀌는 스텝마다 30이 더해집니다. 고정 km는 없습니다.

```
PUT /vehicle/ev-battery?field=soc&value=80
PUT /vehicle/ev-battery?field=soc&value=60
```

데모 패널의 `자동 재생`은 이 프로필이 아닙니다. 그 버튼은 F1, F2, D1, D2, F3, C1, C3, C2를 약 90초 재생하는 리허설입니다.

## 컷 표

기대 화면은 제품 화면입니다. 앱이 만들지 않는 컷의 명령은 없습니다.

| 컷 | 타임코드 | 길이 | 시작 스텝 | 기대 화면 | adb |
| --- | --- | --- | --- | --- | --- |
| O1 | 0:00–0:03 | 3초 | 없음 | 앱 화면 없음. 편집 타이틀 `미리(Miri)` · `크레이지랩스` | 없음 |
| O2 | 0:03–0:06 | 3초 | 없음 | 앱 화면 없음. 편집 숫자 9.9% | 없음 |
| O3 | 0:06–0:09 | 3초 | 없음 | 앱 화면 없음. 편집 숫자 49.7% | 없음 |
| H | 0:09–0:13 | 4초 | 없음 | 앱 화면 없음. 편집 실루엣과 `처음 타는 차, 반납까지 충전 될까?` | 없음 |
| 탑승 | 0:13–0:15 | 2초 | 없음 | 앱 화면 없음. Pleos 홈과 시동 손은 앱이 만들지 않음 | 없음 |
| F1 | 0:15–0:20 | 5초 | `F1` | `어떤 차로 타고 계세요?` P, SOC 80% | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step F1` |
| F2 | 0:20–0:27 | 7초 | `F2` | `언제, 어디로 반납하세요?` 10/26 12:00, `다 됐어요` P, SOC 80% | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step F2` |
| D1 | 0:27–0:33 | 6초 | `D1` | `오늘 길` 톨게이트 2곳, 고속도로 진입 1번. 데모 경로. P, SOC 80% | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step D1` |
| 주행 1 | 0:33–0:39 | 6초 | `DRIVE1` | 카드 없음, 주행 아이콘. D, SOC 80%. 지도·속도·도로는 편집 | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step DRIVE1` |
| D2 | 0:39–0:46 | 7초 | `D2` | `충전소에 가요`. 충전구·커넥터는 차 정보, `80% 남음`. P. D2-b 음성은 없음 | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step D2` |
| 주행 2 | 0:46–0:49 | 3초 | `DRIVE2` | 카드 없음, 주행 아이콘. D, SOC 80%. D2-b 없음 | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step DRIVE2` |
| 충전 | 0:49–0:53 | 4초 | 없음 | 앱 화면 없음. 이 컷만 SOC 60. `PUT /vehicle/ev-battery?field=soc&value=60`. 충전구 클로즈업과 시스템 배터리 패널은 앱이 만들지 않음. 다음 앱 컷 전에 다시 80 | 없음 |
| F3 | 0:53–1:02 | 9초 | `F3` | 제품 F3 한 장. 히어로는 `11:03쯤 출발하면`, 아래 `반납 12:00`, `가는 데 약 42분`. F2와 같은 시각. 점선 안내 버튼은 숨김. SOC 80 | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step F3` |
| 주행 3 | 1:02–1:04 | 2초 | 없음 | 앱 화면 없음. 편집용 운전석 도로. SOC 80 | 없음 |
| C1 | 1:04–1:11.5 | 7.5초 | `C1` | `반납 전 점검`, 오른쪽 뒷문 열림. 6초 뒤 문을 닫으면 차 그림이 닫힌 탑뷰로 바뀌고 배지는 `닫힘`. 그 상태를 1.5초 유지한 뒤 점검 끝. SOC 80 | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step C1` |
| C2 | 1:11.5–1:15.5 | 4초 | `C2` | `점검 끝. 좋은 하루 보내세요.` 주행거리 줄은 적산 차이. 차이가 0이면 줄을 숨김. SOC 80 | `adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step C2` |
| 엔딩 | 1:15.5–1:20.5 | 5초 | 없음 | 앱 화면 없음. 편집 카드 `미리가 먼저 챙겨 줘요` | 없음 |

앱 컷을 이어서 재생합니다. 편집 컷의 시간만큼 멈추지 않습니다.

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1
```

D1만 8초로 늘릴 때:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es profile storyboard-v1 --es step D1 --ei seconds 8
```

정지:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start false
```

모르는 프로필, 편집 컷 id, `C3`를 이 프로필에 넣으면 재생을 시작하지 않고 이미 돌고 있는 스크립트도 멈추지 않습니다.

## 리허설 스텝

데모 패널과, 프로필 없는 브로드캐스트가 쓰는 id입니다.

| id | 화면 |
| --- | --- |
| `F1` | 차 종류 |
| `F2` | 반납 시각 |
| `D1` | 오늘 길 |
| `D2` | 충전 |
| `F3` | 반납 준비 |
| `C1` | 반납 점검 |
| `C3` | 다녀왔어요 |
| `C2` | 점검 끝 |

`C3`는 주행거리를 보여주는 반납 화면입니다. `C2`는 그 다음의 점검 끝입니다. 프로필이 없으면 `DRIVE1`과 `DRIVE2`는 스텝이 아닙니다.

`hold`는 그 스텝부터의 대기 시간 배수입니다. 없거나 0 이하이면 1입니다. 고른 스텝 앞의 동작은 대기 없이 적용됩니다.

리허설 시작:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true
```

F3부터:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es step F3
```

C1부터, 대기를 1.5배:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es step C1 --ef hold 1.5
```

C3(다녀왔어요):

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true --es step C3
```

다른 리허설 스텝도 같은 형식입니다. `step`에 `F1`, `F2`, `D1`, `D2`, `F3`, `C1`, `C3`, `C2` 중 하나를 넣습니다.
