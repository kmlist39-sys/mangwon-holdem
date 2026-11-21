# 망원동 홀덤

**[Android 테스트용 APK 다운로드 · v0.7.5](https://drive.google.com/file/d/1SJGQp0e7fY-oT4pPx28QwWRSiomO8syI/view)**

**동네에서 즐기는 방식 그대로.** 망원동, 성산동, 당산의 홀덤펍에서 50명의 동네 단골과 한 테이블에 앉는 안드로이드 싱글플레이 게임입니다. 플레이어의 자리는 항상 6시 방향입니다.

| 홀덤펍 | 게임 | 바이인 |
| --- | --- | --- |
| 망원동 | 데일리 토너먼트 · 2테이블, 18명 | 5만~10만 P |
| 성산동 | 데일리 토너먼트 · 4테이블, 36명 | 10만~20만 P |
| 당산 | 원보드 또는 더블보드 · 6인 또는 9인 | 20만~70만 P |

자리에 앉으면 상대가 콜을 고민하며 시선을 옮기고, 폴드할 때는 손을 거두며 아쉬운 표정을 짓습니다. 더블보드에서는 각자 홀카드 세 장을 받고, 각 보드에서 최대 두 장을 사용합니다.

쇼다운에서 남은 참가자들의 패가 모두 공개되면 각 자리 옆에서 보드별 승률을 볼 수 있습니다. 공개된 카드로 동률 지분까지 나눠 계산합니다. 프리플랍에는 2,048회 표본 추정값을 표시하고, 플랍부터는 가능한 모든 런아웃을 계산합니다. 사이드 팟별 실제 지급액은 정산표에서 확인할 수 있습니다.

## Android Studio에서 빌드하기

저장소 루트를 Android Studio에서 열고 Android SDK 36을 설치합니다. Windows에서는 `gradlew.bat assembleDebug`, macOS와 Linux에서는 `./gradlew assembleDebug`를 실행하면 됩니다. 생성된 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 있습니다. Android 8.0 이상에서 실행할 수 있습니다.

디버그 빌드는 Google 테스트 광고를 사용합니다. 충전소에서 보상형 광고를 끝까지 시청하면 게임 포인트 50,000 P를 받습니다.

테이블과 캐릭터 애니메이션은 `app/src/main/java/kr/mangwon/holdem`, 규칙과 AI는 `app/src/main/assets/club.js`, 딜러 음성은 `app/src/main/assets/dealer-voice`에 있습니다.
