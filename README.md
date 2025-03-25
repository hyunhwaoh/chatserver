#  실시간 대규모 서비스 아키텍처
WebSocket은 실시간 양방향 통신에 적합하지만, 대규모 서비스에서는 몇 가지 한계가 있음.

## ❌ WebSocket 단독 사용의 한계

### 1. 서버 확장 문제 (수평 확장 어려움)

- WebSocket 연결은 특정 서버와 유지됨.
- __🔥 클라이언트가 A 서버에 연결되어 있는데, B 서버에서 메시지를 보내야 하면 직접 전송할 수 없음.__
- 해결책: Redis Pub/Sub, Kafka 를 사용해 여러 서버에서 메시지를 공유.

### 2. 메시지 손실 가능성

- WebSocket 연결이 끊어지면 해당 클라이언트는 메시지를 받을 수 없음.
- 서버가 다운되면 진행 중이던 메시지가 유실될 수도 있음.
- 해결책: Kafka, RabbitMQ 같은 메시지 큐를 사용해 메시지 영속성 보장.

### 3. 이전 메시지 불러오기 어려움

- WebSocket은 기본적으로 실시간 전송만 가능하고, 과거 메시지를 저장하지 않음.
- 해결책: MySQL, Cassandra 같은 DB를 사용해 채팅 기록 저장.

## 📌 2. WebSocket + 메시지 브로커(Kafka, Redis pub/sub, RabbitMQ) 조합이 필요한 이유

대규모 서비스에서는 WebSocket을 단독으로 쓰는 게 아니라, 메시지 브로커와 조합해서 사용.
이렇게 하면 서버 확장성 문제를 해결하면서도 실시간 채팅이 가능함.

### ✅ WebSocket + Redis Pub/Sub
- 클라이언트는 WebSocket을 통해 서버와 연결.
- 서버는 메시지를 Redis Pub/Sub을 이용해 다른 서버로 전달.
- 다만, Redis는 메시지를 영구 저장하지 않기 때문에 메시지 손실 가능성이 있음.

### ✅ WebSocket + Kafka (or RabbitMQ)
- Kafka나 RabbitMQ 같은 메시지 큐를 추가하면 메시지를 저장하고, 필요한 경우 다시 전달할 수 있음.
- 서버가 다운되더라도 Kafka가 메시지를 저장하고 있으므로 데이터 유실이 없음.

## 📌 3. 실제 서비스에서는 WebSocket을 어떻게 사용할까?

### ✅ Kacao Entertainment (WebSocket + Kafka + Redis)
- WebSocket: 클라이언트와 서버 간의 통신
- Kafka: 메시지 브로커. 채팅 서버간 메시지 전달 및 제어 Command, Event 메시지 처리.
- Redis: 실시간 데이터 저장

https://kakaoentertainment-tech.tistory.com/110

### ✅ Line (WebSocket + Redis Pub/Sub)
- WebSocket: 클라이언트와 서버 간의 통신
- Akka toolkit 을 통한 고속 병행 처리
- Redis Pub/Sub: 메시지 브로커. 서버 간의 코멘트 동기화

https://engineering.linecorp.com/ko/blog/the-architecture-behind-chatting-on-line-live

### ✅ Slack (WebSocket + Redis + Kafka + MySQL)
- WebSocket: 클라이언트와 서버 간 실시간 연결.
- Redis Pub/Sub: 여러 서버 간 실시간 메시지 전파.
- Kafka: 메시지를 영구 저장해서 서버 장애 시에도 복구 가능.
- MySQL: 채팅 메시지를 저장해 검색 및 재전송 지원.

### ✅ WhatsApp (WebSocket + RabbitMQ + Cassandra)
- WebSocket: 실시간 채팅 전송.
- RabbitMQ: 메시지를 영구 저장하고, 안정적으로 전송.
- Cassandra: 분산 DB로 메시지 저장, 빠른 읽기/쓰기 성능.

### ✅ Discord (WebSocket + Redis + ScyllaDB)
- WebSocket: 사용자가 음성/텍스트 채팅방에 접속하면 서버와 연결.
- Redis: 실시간 메시지 전파.
- ScyllaDB: 대량의 채팅 메시지를 저장하고 빠르게 불러오기.

## 4. 🚀 이슈 사항

### 🔥 대규모 실시간 서비스에서 룸(채널)이 많아질 경우 발생할 수 있는 메시지 브로커 과부하 문제, 확장성 이슈.

웹소켓 서버가 모든 룸의 메시지를 구독하게 되면:

#### 1. 불필요한 메시지 처리: 특정 웹소켓 서버에 연결된 클라이언트가 없는 룸의 메시지도 받게 됩니다.
#### 2. 메모리 사용량 증가: 관련 없는 메시지까지 처리해야 하므로 메모리 소비가 늘어납니다.
#### 3. 네트워크 대역폭 낭비: 필요 없는 메시지도 전송되어 네트워크 자원이 낭비됩니다.

### 🔥 대규모 서비스의 해결책

#### 1. 동적 구독 패턴:

웹소켓 서버는 현재 연결된 클라이언트가 있는 룸만 구독합니다.
클라이언트가 룸에 입장/퇴장할 때 구독 상태를 동적으로 변경합니다.


#### 2. 샤딩 및 파티셔닝:

룸을 여러 파티션으로 나누고, 웹소켓 서버가 특정 파티션만 담당합니다.
예: Discord는 "Guild(서버)" 단위로 샤딩을 구현했습니다.


#### 3. 계층형 메시징 구조:

슬랙의 아키텍처처럼 Gateway Server → Channel Server 구조로 나누어 부하를 분산합니다.
각 Gateway Server는 자신의 클라이언트와 관련된 채널 정보만 관리합니다.


#### 4. 메시지 필터링:

메시지 브로커 수준에서 필터링 메커니즘을 구현합니다.
예: RabbitMQ의 topic exchange, Kafka 의 consumer group 활용


#### 5. 로컬 라우팅 최적화:

✔ 같은 서버 인스턴스에 연결된 클라이언트 간의 메시지 전송을 최적화.

✔ 메시지 브로커가 병목 지점이 되지않게 방지 가능.

➡ 지역성 인식(Locality Awareness):

서버는 현재 자신에게 연결된 모든 클라이언트와 그들이 참여 중인 룸을 추적 구독합니다.
메시지 전송 시, 목적지 클라이언트가 동일한 서버에 있는지 확인합니다.


➡ 로컬 우회 라우팅(Local Bypass Routing):

메시지 발신자와 수신자가 같은 서버에 있다면, 메시지 브로커(Redis, RabbitMQ 등)를 거치지 않고 직접 전달합니다.
외부 서버의 클라이언트를 위한 메시지만 메시지 브로커에 발행합니다.


## 📌 5. 고려사항

### ➡ 1안: 로컬 우회 라우팅으로 강의별 라우팅을 하여 같은 강의를 들을 클라이언트를 몰아 연결.

클라이언트가 A 서버에 연결되어 있는데, B 서버에서 메시지를 보내야 하면 직접 전송할 수 없음.



### ➡ 2안: WebSocket을 메시지 브로커와 함께 라우팅 최적화하여 사용!
✔ WebSocket 단독 사용은 대규모 서비스에서 한계가 있음.

✔ Redis, Kafka, RabbitMQ 같은 메시지 브로커와 함께 사용해서 서버 확장성을 확보.

✔ MySQL, Cassandra 같은 DB를 추가해 메시지 저장 및 검색 기능 제공.

➡ WebSocket을 사용하지만, 대규모 트래픽을 감당하기 위해 메시지 브로커와 결합하는 구조가 일반적! 
동적 구독 패턴 적용, 로컬 라우팅을 사용하여 웹소켓을 최적화를 하여 부하를 줄이고 성능을 향상시킬수 있다.

➡ 현재 RabbitMQ 에 STOMP 플러그인하여 사용중이라 웹소켓 서버를 수평 확장하기 어려움.
따라서 지금 가지고 있는 RabbitMQ 또는 Redis pub/sub 활용하여 자체 socket.io 서버를 만드는 것이 확장성면에서 좋겠다.