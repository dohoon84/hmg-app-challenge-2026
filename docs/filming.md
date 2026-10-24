# 촬영 모드

디버그 빌드에서만 켜는 촬영 모드입니다. 릴리스에는 `BuildConfig.FILMING`이 `false`이고, 수신기도 매니페스트에 없습니다. 새 권한은 없습니다. 화면 버튼은 없습니다.

촬영이 켜지면 테마는 시스템 설정과 상관없이 다크로 고정됩니다. 디버그 패널, 길게 누르기, `값 적용`, 데모 값, 자막, 하이라이트, 점선 `반납 장소로 안내`, 기초 화면 오버레이는 숨습니다. 제품 화면은 평소 다크 모드와 같은 배치입니다.

자동 재생은 실제 기어가 P일 때만 시작하고, P를 벗어나면 바로 멈춘 뒤 실제 차량 값으로 돌아갑니다. 스크립트가 바꾸는 기어는 이 조건에 들어가지 않습니다.

앱을 연 뒤 브로드캐스트를 보냅니다. 프로세스가 아직 없으면 명령은 화면이 뜰 때까지 기다립니다.

플레오스 패키지는 `com.krazylabs.miri`입니다. fake 빌드는 `com.krazylabs.miri.fake`로 바꿔 같은 명령을 보냅니다.

## 스텝 id

스크립트에 있는 id입니다.

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

`C3`는 주행거리를 보여주는 반납 화면입니다. `C2`는 그 다음의 점검 끝입니다. 모르는 id는 재생을 시작하지 않습니다.

`hold`는 그 스텝부터의 대기 시간 배수입니다. 없거나 0 이하이면 1입니다. 고른 스텝 앞의 동작은 대기 없이 적용되어 차량 상태를 맞춘 뒤, 그 스텝부터 배수가 적용됩니다.

## adb

촬영 켜기:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.FILMING --ez on true
```

촬영 끄기:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.FILMING --ez on false
```

자동 재생 시작:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start true
```

자동 재생 정지:

```
adb shell am broadcast -n com.krazylabs.miri/.mvp.FilmingReceiver -a com.krazylabs.miri.action.AUTOPLAY --ez start false
```

F3부터 시작:

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

다른 스텝도 같은 형식입니다. `step`에 `F1`, `F2`, `D1`, `D2`, `F3`, `C1`, `C3`, `C2` 중 하나를 넣습니다.
