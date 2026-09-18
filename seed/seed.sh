#!/bin/sh
set -e

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
echo "Waiting for backend at ${BACKEND_URL}..."

i=0
until curl -sf "${BACKEND_URL}/api/health" >/dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Backend not ready after 60 attempts"
    exit 1
  fi
  sleep 2
done
echo "Backend is up"

LOGIN=$(curl -sf -X POST "${BACKEND_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"operator","password":"op123456"}')
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "Failed to login for seed"
  echo "$LOGIN"
  exit 1
fi
AUTH="Authorization: Bearer ${TOKEN}"

post_obligation() {
  curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" -d "$1" >/dev/null
}

put_rate() {
  # $1 = payload; idempotent: skip if the same pair+effectiveDate already exists.
  PAYLOAD="$1"
  PAIR_BASE=$(printf '%s' "$PAYLOAD" | sed -n 's/.*"baseCurrency":"\([^"]*\)".*/\1/p')
  PAIR_QUOTE=$(printf '%s' "$PAYLOAD" | sed -n 's/.*"quoteCurrency":"\([^"]*\)".*/\1/p')
  EFF=$(printf '%s' "$PAYLOAD" | sed -n 's/.*"effectiveDate":"\([^"]*\)".*/\1/p')

  EXISTS=""
  for OBJ in $(curl -sf "${BACKEND_URL}/api/fx-rates" -H "$AUTH" | grep -o '{[^{}]*}'); do
    case "$OBJ" in
      *"\"pair\":\"${PAIR_BASE}/${PAIR_QUOTE}\""*"\"effectiveDate\":\"${EFF}\""*)
        EXISTS="yes"
        break
        ;;
    esac
  done

  if [ "$EXISTS" = "yes" ]; then
    echo "  rate ${PAIR_BASE}/${PAIR_QUOTE} @${EFF} already exists, skip"
    return 0
  fi
  curl -sf -X POST "${BACKEND_URL}/api/fx-rates" -H "$AUTH" -H "Content-Type: application/json" -d "$PAYLOAD" >/dev/null
  echo "  seeded rate ${PAIR_BASE}/${PAIR_QUOTE}"
}

MEMBERS=$(curl -sf "${BACKEND_URL}/api/members" -H "$AUTH")
COUNT=$(printf '%s' "$MEMBERS" | grep -o '"memberId"' | wc -l | tr -d ' ')
NEED_OBLIGATIONS=0
if [ "$COUNT" -gt 0 ]; then
  echo "Seed skipped: members already exist ($COUNT)"
else
  NEED_OBLIGATIONS=1
  echo "Seeding members..."
  M1=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Alpha Bank"}')
  M2=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Beta Securities"}')
  M3=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Gamma Clearing"}')

  ID1=$(printf '%s' "$M1" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')
  ID2=$(printf '%s' "$M2" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')
  ID3=$(printf '%s' "$M3" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')

  SETTLE_DATE=$(date -u +%Y-%m-%d 2>/dev/null || echo "2026-09-17")
  TRADE_DATE="$SETTLE_DATE"

  echo "Seeding OPEN USD obligations for settleDate=${SETTLE_DATE}..."
  post_obligation "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"USD\",\"amount\":100000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"USD\",\"amount\":60000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"USD\",\"amount\":40000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"USD\",\"amount\":25000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"

  echo "Seeding OPEN EUR obligations..."
  # Triangle: A -50000, B +30000, C +20000 (Σ = 0)
  post_obligation "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"EUR\",\"amount\":80000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"EUR\",\"amount\":50000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"EUR\",\"amount\":30000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"

  echo "Seeding OPEN CNY obligations..."
  # Triangle: A +100000, B -200000, C +100000 (Σ = 0)
  post_obligation "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"CNY\",\"amount\":300000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"CNY\",\"amount\":200000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"
  post_obligation "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"CNY\",\"amount\":100000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}"

  echo "Seed completed: 10 obligations across USD/EUR/CNY"
fi

# Demo FX rates are ensured on every run (idempotent by pair + effective date),
# so existing databases also receive them after an upgrade.
SEED_DATE=$(date -u +%Y-%m-%d 2>/dev/null || echo "2026-09-17")
echo "Seeding demo FX rates effective ${SEED_DATE}..."
put_rate "{\"baseCurrency\":\"EUR\",\"quoteCurrency\":\"USD\",\"rate\":1.10000000,\"effectiveDate\":\"${SEED_DATE}\"}"
put_rate "{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"CNY\",\"rate\":7.20000000,\"effectiveDate\":\"${SEED_DATE}\"}"

echo "Seed completed successfully"
exit 0
