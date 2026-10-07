#!/usr/bin/env bash
# Exercises every endpoint of the deployed war the way the checker would, and
# the way the lecturer recommends verifying servlets independently of a client.
BASE="http://localhost:8080/guess-market"
# The sample files that ship with the project. curl here is a Windows build
# and cannot open a /c/... path, so the Windows form is asked for.
ITEMS="$(cd "$(dirname "$0")/../test-files/ex3" && { pwd -W 2>/dev/null || pwd; })"
# curl here is a Windows build, so the scratch has to be somewhere it can see.
JARS="${TMP:-${TEMP:-/tmp}}/gm-api-test"
rm -rf "$JARS"; mkdir -p "$JARS"
A="$JARS/alice.jar"; B="$JARS/bob.jar"; N="$JARS/nobody.jar"
# Unique per run: the market keeps its users until the server stops.
RUN="$(date +%s | tail -c 6)"
ALICE="Alice$RUN"; BOB="Bob$RUN"

pass=0; fail=0
check() { # check <label> <expected-status> <actual-status> [body]
  if [ "$2" = "$3" ]; then
    printf "  [ok]   %-58s %s\n" "$1" "$3"; pass=$((pass+1))
  else
    printf "  [FAIL] %-58s got %s want %s\n" "$1" "$3" "$2"
    [ -n "$4" ] && echo "         body: $4"
    fail=$((fail+1))
  fi
}
contains() { # contains <label> <needle> <haystack>
  if printf '%s' "$3" | grep -qF -- "$2"; then
    printf "  [ok]   %-58s\n" "$1"; pass=$((pass+1))
  else
    printf "  [FAIL] %-58s missing %s\n" "$1" "$2"
    echo "         body: $3"
    fail=$((fail+1))
  fi
}
req() { # req <jar> <method> <path> [data...] ; sets S and BODY
  local jar="$1" method="$2" path="$3"; shift 3
  local args=(-sS -o "$JARS/body" -w "%{http_code}" -b "$jar" -c "$jar" -X "$method")
  for d in "$@"; do args+=(--data-urlencode "$d"); done
  S=$(curl "${args[@]}" "$BASE$path")
  BODY=$(cat "$JARS/body")
}
upload() { # upload <jar> <file>
  S=$(curl -sS -o "$JARS/body" -w "%{http_code}" -b "$1" -c "$1" -F "file=@$2;type=application/xml" "$BASE/upload")
  BODY=$(cat "$JARS/body")
}

# The course sample files carry fixed event names, and event names are unique
# for the life of the server, so this suite wants a market nobody has uploaded
# into yet. Saying so beats a cascade of confusing failures.
curl -sS -o "$JARS/probe" -c "$JARS/probe.jar" -b "$JARS/probe.jar"      -X POST --data-urlencode "name=Probe$RUN" "$BASE/login" > /dev/null
EXISTING=$(curl -sS -b "$JARS/probe.jar" -c "$JARS/probe.jar" "$BASE/sync"   | python -c "import sys,json; print(json.load(sys.stdin).get('totalEventCount', -1))" 2>/dev/null)
if [ "$EXISTING" != "0" ]; then
  echo "This suite needs a freshly started server (the market already holds $EXISTING events)."
  echo "Restart Tomcat and run it again."
  exit 2
fi

echo "=== A. login ==="
req "$A" POST /login "name=$ALICE"
check "Alice logs in" 200 "$S" "$BODY"
contains "and starts with nothing in the account" '"balance":0' "$BODY"
contains "and is not a market maker yet" '"marketMaker":false' "$BODY"

req "$A" POST /login "name=$ALICE"
check "the same name again is refused" 400 "$S" "$BODY"
contains "and says it is taken" "already taken" "$BODY"

req "$N" POST /login "name=  $(printf %s "$ALICE" | tr A-Z a-z)  "
check "the same name in another case is refused too" 400 "$S" "$BODY"

req "$N" POST /login "name=   "
check "a blank name is refused" 400 "$S" "$BODY"

req "$B" POST /login "name=$BOB"
check "Bob logs in" 200 "$S" "$BODY"

echo
echo "=== B. a request with no session ==="
rm -f "$N"
req "$N" GET /sync
check "sync without logging in is refused" 401 "$S" "$BODY"
contains "and says so" "not logged in" "$BODY"

echo
echo "=== C. uploading events ==="
upload "$A" "$ITEMS/small.xml"
check "Alice uploads small.xml" 200 "$S" "$BODY"
contains "it names the event added" "Mujtaba is Dead" "$BODY"

upload "$A" "$ITEMS/small.xml"
check "the same file again is refused" 400 "$S" "$BODY"
contains "and says the name is taken" "already in the system" "$BODY"

upload "$B" "$ITEMS/small.xml"
check "and refused for a different user too" 400 "$S" "$BODY"

upload "$B" "$ITEMS/multiple.xml"
check "Bob uploads multiple.xml" 200 "$S" "$BODY"
contains "three events named" "World Cap Winner" "$BODY"

echo
echo "=== D. the files accumulate, and the uploader is the market maker ==="
req "$A" GET /sync
check "Alice can see the market" 200 "$S" "$BODY"
contains "all four events are there" '"totalEventCount":4' "$BODY"
contains "Alice is market maker of hers" "\"marketMakerName\":\"$ALICE\"" "$BODY"
contains "Bob is market maker of his" "\"marketMakerName\":\"$BOB\"" "$BODY"
contains "Alice sees Bob in the user list" "\"name\":\"$BOB\"" "$BODY"
contains "and Alice counts as a market maker now" '"marketMaker":true' "$BODY"
contains "her account shows an opening line" "Opening balance" "$BODY"

echo
echo "=== E. a bad file changes nothing ==="
printf 'not xml at all' > "$JARS/bad.xml"
upload "$A" "$JARS/bad.xml"
check "a file that is not XML is refused" 400 "$S" "$BODY"
contains "and says why" "well-formed" "$BODY"

printf '<?xml version="1.0"?><Guess-Market><GM-events/></Guess-Market>' > "$JARS/empty.xml"
upload "$A" "$JARS/empty.xml"
check "a file with no events is refused" 400 "$S" "$BODY"

cat > "$JARS/twins.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Guess-Market><GM-events>
  <GM-event name="Twin"><description>d</description><commission type="on-close">5</commission>
    <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
    <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method></GM-event>
  <GM-event name="Twin"><description>d</description><commission type="on-close">5</commission>
    <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
    <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method></GM-event>
</GM-events></Guess-Market>
XML
upload "$A" "$JARS/twins.xml"
check "two events with one name in a file is refused" 400 "$S" "$BODY"
contains "and says they must differ" "two events named" "$BODY"

cat > "$JARS/badfee.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Guess-Market><GM-events>
  <GM-event name="Fine One"><description>d</description><commission type="on-close">5</commission>
    <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
    <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method></GM-event>
  <GM-event name="Bad Fee"><description>d</description><commission type="on-close">91</commission>
    <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
    <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method></GM-event>
</GM-events></Guess-Market>
XML
upload "$A" "$JARS/badfee.xml"
check "commission above 90 is refused" 400 "$S" "$BODY"
contains "and names the range" "0 to 90" "$BODY"
req "$A" GET /sync
contains "the good event in that file was NOT added" '"totalEventCount":4' "$BODY"
if printf '%s' "$BODY" | grep -qF '"name":"Fine One"'; then
  printf "  [FAIL] %-58s half the file was taken in\n" "nothing from a bad file is kept"; fail=$((fail+1))
else
  printf "  [ok]   %-58s\n" "nothing from a bad file is kept"; pass=$((pass+1))
fi

cat > "$JARS/ex2file.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Guess-Market><GM-events>
  <GM-event name="Old Format"><id>1</id><description>d</description>
    <commission type="on-close">5</commission>
    <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
    <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method></GM-event>
</GM-events><GM-users><GM-user name="Zoe"><initial-cash>100</initial-cash></GM-user></GM-users></Guess-Market>
XML
upload "$A" "$JARS/ex2file.xml"
check "an exercise 2 file is refused" 400 "$S" "$BODY"
contains "and says which format it is" "exercise 2 file" "$BODY"

echo
echo "=== F. money, and opening an event ==="
EVENT_ID=$(printf '%s' "$BODY" > /dev/null; req "$A" GET /sync; printf '%s' "$BODY" | python -c "
import sys, json
snap = json.load(sys.stdin)
print(next(e['id'] for e in snap['events'] if e['name'] == 'Mujtaba is Dead'))
")
echo "  (Alice's event id is $EVENT_ID)"

req "$A" POST /event/open "eventId=$EVENT_ID"
check "opening with an empty account is refused" 400 "$S" "$BODY"
contains "and says the money is short" "the account holds only" "$BODY"

req "$A" POST /funds "amount=-5"
check "loading a negative amount is refused" 400 "$S" "$BODY"
req "$A" POST /funds "amount=0"
check "loading nothing is refused" 400 "$S" "$BODY"
req "$A" POST /funds "amount=abc"
check "loading something that is not a number is refused" 400 "$S" "$BODY"

req "$A" POST /funds "amount=1000"
check "Alice loads 1000" 200 "$S" "$BODY"
contains "and the balance says so" '"balance":1000.0' "$BODY"

req "$B" POST /event/open "eventId=$EVENT_ID"
check "somebody who is not the market maker cannot open it" 400 "$S" "$BODY"
contains "and is told who can" "$ALICE" "$BODY"

req "$A" POST /event/open "eventId=$EVENT_ID"
check "Alice opens her event" 200 "$S" "$BODY"
contains "it is active now" '"statusLabel":"Active"' "$BODY"

req "$A" POST /event/open "eventId=$EVENT_ID"
check "opening it twice is refused" 400 "$S" "$BODY"

echo
echo "=== G. trading, and what the other user sees ==="
req "$B" GET "/trade/lmsr-quote?eventId=$EVENT_ID&option=0&quantity=20"
check "Bob asks what 20 shares would cost" 200 "$S" "$BODY"
contains "and is told it is more than he has" '"affordable":false' "$BODY"

req "$B" POST /funds "amount=500"
check "Bob loads 500" 200 "$S" "$BODY"

req "$B" POST /trade/lmsr-buy "eventId=$EVENT_ID" "option=0" "quantity=20"
check "Bob buys 20 shares" 200 "$S" "$BODY"
contains "he paid for the shares" '"sharesCost":10.499' "$BODY"
contains "and the commission" '"commissionPaid":0.524' "$BODY"

req "$A" GET /sync
contains "Alice sees the commission arrive in her account" "Commission received" "$BODY"

req "$B" GET "/sync?eventId=$EVENT_ID"
contains "Bob's own involvement is reported" '"option1Quantity":20' "$BODY"
contains "and the event detail came with it" '"tradesNewestFirst"' "$BODY"

echo
echo "=== H. the order book ==="
BOOK_ID=$(req "$B" GET /sync; printf '%s' "$BODY" | python -c "
import sys, json
snap = json.load(sys.stdin)
print(next(e['id'] for e in snap['events'] if e['name'] == 'World Cap Winner'))
")
echo "  (Bob's order book event id is $BOOK_ID)"
req "$B" POST /event/open "eventId=$BOOK_ID"
check "Bob opens the order book event" 200 "$S" "$BODY"

req "$B" POST /trade/order "eventId=$BOOK_ID" "option=0" "side=SELL" "price=0.50" "quantity=30"
check "Bob offers 30 at 0.50" 200 "$S" "$BODY"
contains "nothing filled, it rests" '"restingQuantity":30' "$BODY"

req "$A" GET "/trade/order-quote?eventId=$BOOK_ID&option=0&side=BUY&price=0.50&quantity=30"
check "Alice asks about buying 30 at 0.50" 200 "$S" "$BODY"
contains "and is told it would trade at once" '"wouldTradeNow":true' "$BODY"

req "$A" POST /trade/order "eventId=$BOOK_ID" "option=0" "side=BUY" "price=0.50" "quantity=30"
check "Alice buys them" 200 "$S" "$BODY"
contains "it filled" '"fills":[' "$BODY"
contains "and nothing was left resting" '"restingQuantity":0' "$BODY"

req "$B" POST /trade/order "eventId=$BOOK_ID" "option=0" "side=BUY" "price=1.05" "quantity=5"
check "a price above d minus a cent is refused" 400 "$S" "$BODY"
contains "and names the limit" "0.99" "$BODY"

req "$A" POST /trade/order "eventId=$BOOK_ID" "option=1" "side=SELL" "price=0.40" "quantity=5"
check "selling shares nobody holds is refused" 400 "$S" "$BODY"

req "$B" POST /trade/order "eventId=$BOOK_ID" "option=0" "side=BUY" "price=0.50" "quantity=0"
check "an order for nothing is refused" 400 "$S" "$BODY"

echo
echo "=== I. closing ==="
req "$A" POST /event/close "eventId=$EVENT_ID" "winningOption=0"
check "Alice closes her event" 200 "$S" "$BODY"
contains "and the winner is named" '"winningOptionName"' "$BODY"
req "$B" GET /sync
contains "Bob sees it closed" '"statusLabel":"Closed"' "$BODY"

req "$A" POST /event/close "eventId=$EVENT_ID" "winningOption=0"
check "closing it twice is refused" 400 "$S" "$BODY"
req "$B" POST /trade/lmsr-buy "eventId=$EVENT_ID" "option=0" "quantity=1"
check "trading in a closed event is refused" 400 "$S" "$BODY"

echo
echo "=== J. creating an event, and chat ==="
S=$(curl -sS -o "$JARS/body" -w "%{http_code}" -b "$A" -c "$A" -H "Content-Type: application/json" \
  -d '{"name":"Made Up '"$RUN"'","description":"from nothing","commissionPercent":10,"commissionTypeLabel":"on-close","option1Name":"Yes","option2Name":"No","orderBook":false,"b":50,"baseValue":0,"initialInvestment":0,"allowMint":false}' \
  "$BASE/event/new")
BODY=$(cat "$JARS/body")
check "Alice creates an event from nothing" 200 "$S" "$BODY"
contains "with herself as market maker" "\"marketMakerName\":\"$ALICE\"" "$BODY"

req "$B" POST /chat "text=hello everyone"
check "Bob says something" 200 "$S" "$BODY"
req "$A" GET "/sync?chatFrom=0"
contains "Alice sees it" "hello everyone" "$BODY"
contains "and is told how many there are" '"chatTotal":1' "$BODY"
req "$A" GET "/sync?chatFrom=1"
contains "asking again from 1 sends nothing new" '"newChatMessages":[]' "$BODY"
req "$B" POST /chat "text="
check "an empty message is refused" 400 "$S" "$BODY"

echo
echo "========================================"
echo "API: $pass passed, $fail failed"
echo "========================================"
rm -rf "$JARS"
[ "$fail" -eq 0 ]
