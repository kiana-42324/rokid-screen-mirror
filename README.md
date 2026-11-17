# Rokid Screen Mirror

Android 휴대폰 화면을 디스플레이가 있는 Rokid Glasses에 Bluetooth로 미러링합니다. 휴대폰에서 화면 공유를 승인하면 안경 수신 앱이 JPEG 프레임을 표시합니다. 두 APK는 [Releases](../../releases/latest)에서 받을 수 있습니다.

## 구성

| 경로 | 역할 |
| --- | --- |
| `phone/` | 화면 캡처, 폴더블 화면 비율 변경 처리, Bluetooth 송신 |
| `glasses/` | 프레임 수신 및 표시, 탭으로 90도 회전, 방향 저장 |
| `shared/` | 두 앱의 Bluetooth 서비스 UUID와 연결 권한 처리 |
| `build.ps1` | Windows에서 두 APK 빌드 및 서명 검증 |

Android 9 이상 휴대폰과 Bluetooth Classic RFCOMM을 사용할 수 있는 Rokid Glasses용입니다. 송신 앱 v1.2는 전송 차례에 최신 프레임을 선택하고 전송 시간에 따라 JPEG 품질을 조절합니다.
## 설치
1. Hi Rokid에서 안경과 휴대폰을 먼저 페어링합니다.
2. 휴대폰으로 mirror-phone.apk를 옮겨 열고 설치합니다.
3. 안경에서 개발자 모드와 USB 디버깅을 활성화한 뒤 데이터 케이블로 PC에 연결합니다. 디버깅 승인을 요청하면 연결한 PC를 확인하고 승인합니다.
4. Android SDK Platform-Tools의 adb를 사용할 수 있는 터미널에서 APK가 있는 폴더로 이동합니다. 다음 명령의 SERIAL은 adb devices -l에 표시된 안경의 일련번호로 바꿉니다.

~~~powershell
adb devices -l
adb -s SERIAL install -r -g mirror-glasses.apk
adb -s SERIAL shell am start -n dev.local.rokidmirror.glasses/mirror.Glasses
~~~

## 화면 공유
1. 안경에 ‘휴대폰 연결 대기 중’이 나타나면 PC 연결을 해제해도 됩니다. 수신 앱을 열어 두세요.
2. 휴대폰에서 ‘화면 미러 · 휴대폰’을 열고 ‘안경 선택 및 공유’를 누릅니다. 근처 기기 권한을 허용하고 페어링된 안경을 선택합니다.
3. Android 화면 공유 창에서 전체 화면 또는 지원되는 경우 앱 하나를 선택해 승인합니다. 홈 화면이나 다른 앱으로 이동해 안경에서 내용을 확인합니다.
4. 휴대폰 앱의 ‘공유 종료’ 또는 공유 알림의 ‘종료’를 누르면 캡처와 연결이 닫힙니다.

## 표시 및 연결
긴 변 640픽셀, 초당 최대 약 12.5프레임(실제 속도는 연결 상태에 따라 달라짐)으로 영상만 전송합니다. 가로로 긴 콘텐츠는 휴대폰을 먼저 가로로 돌린 뒤 공유를 시작하면 더 크게 볼 수 있습니다. 화면 보호가 적용된 앱은 Android가 검은 화면으로 처리할 수 있습니다. 인터넷 권한과 화면 저장 기능은 없습니다.

안경이 선택 목록에 없으면 휴대폰 Bluetooth의 페어링 목록을 확인하세요. 목록에 보이는데 연결에 실패하면 안경의 수신 앱을 다시 열고 근처 기기 권한을 확인하세요. 계속 실패하면 해당 펌웨어가 일반 앱의 RFCOMM 연결을 허용하는지 실기기 확인이 필요합니다. 실제 종단 간 지연은 아직 측정하지 않았습니다.

## 소스 빌드
저장소 루트에서 JDK 17, Android SDK Platform 35 및 Build Tools를 준비합니다. PowerShell에서 다음과 같이 SDK 위치를 지정합니다. 빌드 결과는 apk 폴더에 생성됩니다.

~~~powershell
.\build.ps1 -Sdk 'C:\Android\Sdk'
~~~

앱 업데이트 시에는 최초 빌드의 서명 키가 필요합니다. 빌드 스크립트는 로컬 개발 키를 build 폴더에 생성합니다.

