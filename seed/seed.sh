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

MEMBERS=$(curl -sf "${BACKEND_URL}/api/members" -H "$AUTH")
COUNT=$(printf '%s' "$MEMBERS" | grep -o '"memberId"' | wc -l | tr -d ' ')
if [ "$COUNT" -gt 0 ]; then
  echo "Seed skipped: members already exist ($COUNT)"
  exit 0
fi

echo "Seeding members..."
M1=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Alpha Bank"}')
M2=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Beta Securities"}')
M3=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Gamma Clearing"}')

ID1=$(printf '%s' "$M1" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')
ID2=$(printf '%s' "$M2" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')
ID3=$(printf '%s' "$M3" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')

SETTLE_DATE=$(date -u +%Y-%m-%d 2>/dev/null || echo "2026-09-10")
TRADE_DATE="$SETTLE_DATE"

echo "Seeding OPEN obligations for settleDate=${SETTLE_DATE} USD..."
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"USD\",\"amount\":100000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"USD\",\"amount\":60000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"USD\",\"amount\":40000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"USD\",\"amount\":25000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null

echo "Seeding FX rates effective ${SETTLE_DATE}..."
post_rate() {
  curl -sf -X POST "${BACKEND_URL}/api/fx-rates" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"baseCurrency\":\"$1\",\"quoteCurrency\":\"$2\",\"rate\":$3,\"effectiveDate\":\"${SETTLE_DATE}\"}" >/dev/null
}
post_rate USD CNY 7.1000000000
post_rate CNY USD 0.1408450704
post_rate EUR USD 1.0800000000
post_rate USD EUR 0.9259259259
post_rate EUR CNY 7.6680000000
post_rate CNY EUR 0.1304121022

echo "Seeding OPEN obligations for settleDate=${SETTLE_DATE} EUR/CNY..."
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"EUR\",\"amount\":50000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"EUR\",\"amount\":30000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"EUR\",\"amount\":20000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"CNY\",\"amount\":800000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"CNY\",\"amount\":500000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"CNY\",\"amount\":300000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null

echo "Running EUR/CNY netting so the FX report has converted data..."
curl -sf -X POST "${BACKEND_URL}/api/netting-runs" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"settleDate\":\"${SETTLE_DATE}\",\"currency\":\"EUR\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/netting-runs" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"settleDate\":\"${SETTLE_DATE}\",\"currency\":\"CNY\"}" >/dev/null

echo "Seed completed successfully"
exit 0
