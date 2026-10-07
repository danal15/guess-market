#!/usr/bin/env bash
# The awkward corners: empty states, boundary values, and the cases an
# instructor pokes at after the happy path works.
BASE="http://localhost:8080/guess-market"
T="${TMP:-${TEMP:-/tmp}}/gm-edge"
rm -rf "$T"; mkdir -p "$T"
S1="$T/a.jar"; S2="$T/b.jar"
N="$(date +%s | tail -c 6)"

pass=0; fail=0
check() {
  if [ "$2" = "$3" ]; then printf "  [ok]   %-58s %s\n" "$1" "$3"; pass=$((pass+1))
  else printf "  [FAIL] %-58s got %s want %s\n" "$1" "$3" "$2"; echo "         $BODY"; fail=$((fail+1)); fi
}
has() {
  if printf '%s' "$3" | grep -qF -- "$2"; then printf "  [ok]   %-58s\n" "$1"; pass=$((pass+1))
  else printf "  [FAIL] %-58s missing %s\n" "$1" "$2"; echo "         $3"; fail=$((fail+1)); fi
}
hasnt() {
  if printf '%s' "$3" | grep -qF -- "$2"; then printf "  [FAIL] %-58s unexpectedly has %s\n" "$1" "$2"; fail=$((fail+1))
  else printf "  [ok]   %-58s\n" "$1"; pass=$((pass+1)); fi
}
req() {
  local jar="$1" method="$2" path="$3"; shift 3
  local args=(-sS -o "$T/body" -w "%{http_code}" -b "$jar" -c "$jar" -X "$method")
  for d in "$@"; do args+=(--data-urlencode "$d"); done
  S=$(curl "${args[@]}" "$BASE$path"); BODY=$(cat "$T/body")
}
up() { S=$(curl -sS -o "$T/body" -w "%{http_code}" -b "$1" -c "$1" -F "file=@$2;type=application/xml" "$BASE/upload"); BODY=$(cat "$T/body"); }
evid() { printf '%s' "$2" | python -c "
import sys,json
print(next((e['id'] for e in json.load(sys.stdin)['events'] if e['name']==sys.argv[1]), -1))
" "$1"; }

echo "=== A. a user who logs in and does nothing ==="
req "$S1" POST /login "name=Quiet$N"
check "logs in" 200 "$S" "$BODY"
req "$S1" GET /sync
check "the screen can be drawn" 200 "$S" "$BODY"
has "with no events of their own" '"participatingEventIds":[]' "$BODY"
has "one account line, the opening balance" 'Opening balance' "$BODY"
has "and they are not a market maker" '"marketMaker":false' "$BODY"

echo
echo "=== B. names that differ only by space or case ==="
req "$S2" POST /login "name=Quiet$N  "
check "a trailing space is the same name" 400 "$S" "$BODY"
req "$S2" POST /login "name=QUIET$N"
check "and so is another case" 400 "$S" "$BODY"
req "$S2" POST /login "name=Loud$N"
check "a genuinely different name is fine" 200 "$S" "$BODY"

echo
echo "=== C. boundary values in a file ==="
mk() { # mk <file> <name> <commission> <method-xml>
cat > "$T/$1" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<Guess-Market><GM-events><GM-event name="$2">
<description>edge</description><commission type="on-close">$3</commission>
<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
<GM-method>$4</GM-method></GM-event></GM-events></Guess-Market>
XML
}
mk fee0.xml "Fee0 $N" 0 "<GM-LMSR><b>50</b></GM-LMSR>"
up "$S1" "$T/fee0.xml";  check "commission 0 is allowed" 200 "$S" "$BODY"
mk fee90.xml "Fee90 $N" 90 "<GM-LMSR><b>50</b></GM-LMSR>"
up "$S1" "$T/fee90.xml"; check "commission 90 is allowed" 200 "$S" "$BODY"
mk feeneg.xml "FeeNeg $N" -1 "<GM-LMSR><b>50</b></GM-LMSR>"
up "$S1" "$T/feeneg.xml"; check "commission -1 is refused" 400 "$S" "$BODY"
mk b0.xml "B0 $N" 5 "<GM-LMSR><b>0</b></GM-LMSR>"
up "$S1" "$T/b0.xml";    check "b of 0 is refused" 400 "$S" "$BODY"
mk d0.xml "D0 $N" 5 '<GM-order-book allow-mint="true" initial="100" d="0"/>'
up "$S1" "$T/d0.xml";    check "d of 0 is refused" 400 "$S" "$BODY"
mk init0.xml "Init0 $N" 5 '<GM-order-book allow-mint="false" initial="0" d="1"/>'
up "$S1" "$T/init0.xml"; check "an initial investment of 0 is allowed" 200 "$S" "$BODY"

cat > "$T/three.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<Guess-Market><GM-events><GM-event name="Three $N">
<description>edge</description><commission type="on-close">5</commission>
<GM-options><GM-option>A</GM-option><GM-option>B</GM-option><GM-option>C</GM-option></GM-options>
<GM-method><GM-LMSR><b>50</b></GM-LMSR></GM-method></GM-event></GM-events></Guess-Market>
XML
up "$S1" "$T/three.xml"
check "three options is refused" 400 "$S" "$BODY"
has "and says how many are allowed" "exactly 2 options" "$BODY"

printf '' > "$T/zero.xml"
up "$S1" "$T/zero.xml"
check "a zero byte file is refused" 400 "$S" "$BODY"

echo
echo "=== D. an order book event opened with no stock at all ==="
req "$S1" GET /sync
INIT0=$(evid "Init0 $N" "$BODY")
req "$S1" POST /funds "amount=200"
req "$S1" POST /event/open "eventId=$INIT0"
check "it opens even though it costs nothing" 200 "$S" "$BODY"
req "$S1" GET "/sync?eventId=$INIT0"
has "and holds no shares" '"sharesOutstanding":0' "$BODY"
has "with an empty account" '"accountBalance":0.0' "$BODY"

echo
echo "=== E. closing with nobody holding the winning option ==="
req "$S1" GET /sync
FEE0=$(evid "Fee0 $N" "$BODY")
req "$S1" POST /event/open "eventId=$FEE0"
check "an LMSR event opens" 200 "$S" "$BODY"
req "$S1" POST /event/close "eventId=$FEE0" "winningOption=0"
check "and closes with no participants at all" 200 "$S" "$BODY"
has "nobody was paid" '"winnersPaid":0' "$BODY"
req "$S1" GET "/sync?eventId=$FEE0"
has "the event account is emptied back out" '"accountBalance":0.0' "$BODY"

echo
echo "=== F. a winning option index that does not exist ==="
req "$S1" GET /sync
FEE90=$(evid "Fee90 $N" "$BODY")
req "$S1" POST /event/open "eventId=$FEE90"
req "$S1" POST /event/close "eventId=$FEE90" "winningOption=5"
check "an option index past the end is refused" 400 "$S" "$BODY"
req "$S1" POST /event/close "eventId=$FEE90" "winningOption=-1"
check "and a negative one too" 400 "$S" "$BODY"

echo
echo "=== G. an event id that is not there ==="
req "$S1" GET "/sync?eventId=999999"
check "a stale selection is answered, not refused" 200 "$S" "$BODY"
hasnt "with no event detail attached" '"lmsrState"' "$BODY"
req "$S1" POST /event/open "eventId=999999"
check "but acting on it is refused" 400 "$S" "$BODY"
req "$S1" GET "/sync?eventId=notanumber"
check "and a nonsense id is refused clearly" 400 "$S" "$BODY"

echo
echo "=== H. privacy: what one user may learn about another ==="
req "$S2" GET /sync
has "the other user is listed" "Quiet$N" "$BODY"
python - "$BODY" "Quiet$N" <<'PY'
import sys, json
snap = json.loads(sys.argv[1])
other = next(u for u in snap["users"] if u["name"] == sys.argv[2])
allowed = {"name", "balance", "blocked", "marketMaker"}
extra = set(other) - allowed
print("  [%s]   %-58s %s" % ("ok" if not extra else "FAIL",
      "a user row carries nothing beyond the allowed fields",
      sorted(other) if not extra else "EXTRA: " + str(extra)))
PY
hasnt "and the movements shown are only the caller's own" "Funds loaded" "$(req "$S2" GET /sync; printf '%s' "$BODY" | python -c "
import sys, json
print(json.dumps(json.load(sys.stdin)['movements']))
")"

echo
echo "========================================"
echo "EDGE: $pass passed, $fail failed"
echo "========================================"
rm -rf "$T"
[ "$fail" -eq 0 ]
