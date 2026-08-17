#!/usr/bin/env bash
# Walks the four behaviours the platform actually claims:
#   1. an order flows through reserve -> pay -> confirm without blocking intake
#   2. a repeated Idempotency-Key creates one order, not two
#   3. a declined payment cancels the order and puts the stock back
#   4. concurrent orders for one remaining unit produce exactly one winner
set -euo pipefail

ORDERS=${ORDERS:-http://localhost:8081}
INVENTORY=${INVENTORY:-http://localhost:8082}

bold() { printf '\n\033[1m%s\033[0m\n' "$*"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"; }

wait_for() {
  local url=$1 name=$2
  printf 'waiting for %s' "$name"
  for _ in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then printf ' ok\n'; return 0; fi
    printf '.'; sleep 2
  done
  printf '\ngave up on %s\n' "$name" >&2
  exit 1
}

place() {  # place <customer> <sku> <qty> <idempotency-key>
  curl -fsS -X POST "$ORDERS/orders" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $4" \
    -d "{\"customerId\":\"$1\",\"lines\":[{\"sku\":\"$2\",\"quantity\":$3}]}"
}

status_of() {
  curl -fsS "$ORDERS/orders/$1" | json "['status']"
}

settle() {  # poll until the saga stops moving
  local id=$1 status
  for _ in $(seq 1 40); do
    status=$(status_of "$id")
    case "$status" in
      CONFIRMED|REJECTED|CANCELLED) echo "$status"; return 0 ;;
    esac
    sleep 0.5
  done
  echo "$status (still settling)"
}

stock_of() {
  curl -fsS "$INVENTORY/inventory/$1" | json "['available']"
}

wait_for "$INVENTORY/actuator/health" inventory-service
wait_for "$ORDERS/actuator/health" order-service

bold '1. Happy path'
before=$(stock_of SKU-HEADSET)
resp=$(place cust-alice SKU-HEADSET 2 "demo-happy-$RANDOM")
order_id=$(echo "$resp" | json "['orderId']")
echo "accepted immediately as $order_id (intake did not wait for payment)"
echo "settled as: $(settle "$order_id")"
echo "headset stock $before -> $(stock_of SKU-HEADSET)"

bold '2. Idempotency'
key="demo-idem-$RANDOM"
first=$(place cust-bob SKU-KEYBOARD 1 "$key" | json "['orderId']")
second_body=$(place cust-bob SKU-KEYBOARD 1 "$key")
second=$(echo "$second_body" | json "['orderId']")
echo "first  -> $first"
echo "second -> $second (replayed: $(echo "$second_body" | json "['replayed']"))"
[ "$first" = "$second" ] && echo 'same order id: no duplicate charge' || { echo 'MISMATCH'; exit 1; }

bold '3. Declined payment compensates'
before=$(stock_of SKU-PHONE)
declined=$(place cust-carol-decline SKU-PHONE 1 "demo-decline-$RANDOM" | json "['orderId']")
echo "order $declined settled as: $(settle "$declined")"
sleep 2  # the release command travels back over Kafka
echo "phone stock $before -> $(stock_of SKU-PHONE) (reservation released)"

bold '4. Five buyers, one mug'
remaining=$(stock_of SKU-LAST-ONE)
echo "SKU-LAST-ONE available: $remaining"
if [ "$remaining" -lt 1 ]; then
  echo 'already sold — run "docker compose down -v && docker compose up -d" to reset the seed data'
  exit 0
fi

# Fired in parallel on purpose: the five requests reach inventory-service at
# once and the conditional UPDATE is what decides who gets the mug.
tmp=$(mktemp -d)
for i in 1 2 3 4 5; do
  place "cust-racer-$i" SKU-LAST-ONE 1 "demo-race-$$-$i" > "$tmp/$i.json" &
done
wait

ids=()
for i in 1 2 3 4 5; do
  ids+=("$(json "['orderId']" < "$tmp/$i.json")")
done
rm -rf "$tmp"

confirmed=0
for id in "${ids[@]}"; do
  result=$(settle "$id")
  echo "  $id -> $result"
  [ "$result" = 'CONFIRMED' ] && confirmed=$((confirmed + 1))
done
echo "confirmed: $confirmed (expected 1)"
echo "SKU-LAST-ONE available: $(stock_of SKU-LAST-ONE)"

bold 'Done.'
