#!/usr/bin/env bash
#
# Checks the web client's own server: that it serves the page, and that every
# call the page makes reaches Tomcat and comes back unchanged.
#
# The engine itself is covered by the other suites in this folder; nothing here
# re-tests the market. What is new in exercise 4 is the hop through Node, so
# that is what this measures - the same requests, sent to port 3000 instead of
# 8080, have to give the same answers and carry the same session.
#
# Needs both running:
#   run-server.bat   (Tomcat, port 8080)
#   run-web.bat      (the client's server, port 3000)
#
# Start the server FRESH: names are held for as long as it runs, so a second
# pass would be refused the names this one takes.

WEB=${WEB:-http://localhost:3000}
MARKET=${MARKET:-http://localhost:8080}
JAR=$(mktemp -t gmweb.XXXXXX)
STAMP=$$

passed=0
failed=0

ok() { passed=$((passed + 1)); printf '  ok   %s\n' "$1"; }
no() { failed=$((failed + 1)); printf '  FAIL %s\n' "$1"; printf '       %s\n' "$2"; }

check() {
    local what=$1 expected=$2 actual=$3
    if [ "$expected" = "$actual" ]; then ok "$what"; else no "$what" "wanted [$expected], got [$actual]"; fi
}

contains() {
    local what=$1 needle=$2 haystack=$3
    case $haystack in
        *"$needle"*) ok "$what" ;;
        *) no "$what" "[$needle] is not in [$haystack]" ;;
    esac
}

status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo
echo "The page itself"
check "the page is served"           200 "$(status "$WEB/")"
check "the script is served"         200 "$(status "$WEB/app.js")"
check "the stylesheet is served"     200 "$(status "$WEB/style.css")"
check "a missing file is a 404"      404 "$(status "$WEB/nope.js")"
contains "the page is html" "<title>Guess Market</title>" "$(curl -s "$WEB/")"
contains "the script is javascript" "javascript" \
    "$(curl -s -o /dev/null -D - "$WEB/app.js" | tr 'A-Z' 'a-z')"

echo
echo "It does not hand out anything outside its own folder"
# 403, not 404: the path resolves to a real file that simply is not ours to
# hand out, and the server says so rather than pretending it is missing.
check "..%2f is refused"    403 "$(status "$WEB/..%2fserver.js")"
check "..%5c is refused"    403 "$(status "$WEB/..%5cserver.js")"
contains "its own source is not served" "Not found" "$(curl -s "$WEB/../server.js")"

echo
echo "Calls reach the market"
check "unauthenticated sync is 401 through the proxy" 401 "$(status "$WEB/guess-market/sync")"
check "and 401 straight at Tomcat too"                401 "$(status "$MARKET/guess-market/sync")"

name="WebTest$STAMP"
login=$(curl -s -c "$JAR" -d "name=$name" "$WEB/guess-market/login")
contains "login through the proxy works" "\"name\":\"$name\"" "$login"
contains "a new user starts with nothing" '"balance":0.0' "$login"

contains "the same name is refused the second time" "already taken" \
    "$(curl -s -d "name=$name" "$WEB/guess-market/login")"

echo
echo "The session cookie survives the hop"
sync=$(curl -s -b "$JAR" "$WEB/guess-market/sync")
contains "sync now answers"              '"user"' "$sync"
contains "and knows who is asking"       "\"name\":\"$name\"" "$sync"
contains "and carries the event list"    '"events"' "$sync"
contains "and the chat count"            '"chatTotal"' "$sync"

echo
echo "Money and refusals come back whole"
contains "funds load" '"balance":250.0' "$(curl -s -b "$JAR" -d "amount=250" "$WEB/guess-market/funds")"
contains "a refusal keeps its message" "must be greater than zero" \
    "$(curl -s -b "$JAR" -d "amount=-5" "$WEB/guess-market/funds")"
check    "a refusal keeps its status" 400 "$(status -b "$JAR" -d "amount=-5" "$WEB/guess-market/funds")"
contains "a refusal keeps its type" '"type":"TradingException"' \
    "$(curl -s -b "$JAR" -d "amount=-5" "$WEB/guess-market/funds")"

echo
echo "A JSON body gets through as JSON"
made=$(curl -s -b "$JAR" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Proxy check $STAMP\",\"description\":\"\",\"commissionPercent\":5,\"commissionTypeLabel\":\"on-purchase\",\"option1Name\":\"Yes\",\"option2Name\":\"No\",\"orderBook\":false,\"b\":10,\"baseValue\":0,\"initialInvestment\":0,\"allowMint\":false}" \
    "$WEB/guess-market/event/new")
contains "the event is created" "\"name\":\"Proxy check $STAMP\"" "$made"
contains "with this user as market maker" "\"marketMakerName\":\"$name\"" "$made"
contains "and it is not started yet" '"statusLabel":"Not started"' "$made"

id=$(printf '%s' "$made" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
contains "opening it works" '"statusLabel":"Active"' \
    "$(curl -s -b "$JAR" -d "eventId=$id" "$WEB/guess-market/event/open")"

echo
echo "A quote in the query string survives"
quote=$(curl -s -b "$JAR" "$WEB/guess-market/trade/lmsr-quote?eventId=$id&option=0&quantity=5")
contains "a quote comes back"      '"totalCost"' "$quote"
contains "and says it is affordable" '"affordable":true' "$quote"

echo
echo "Both ways round give the same answer"
# The whole point of the proxy: it must be a pipe, not a translator.
direct=$(curl -s -b "$JAR" "$MARKET/guess-market/trade/lmsr-quote?eventId=$id&option=0&quantity=5")
check "the proxied quote matches the direct one" "$direct" "$quote"

echo
echo "When the market is unreachable, the proxy says so in the servlets' own shape"
down=$(MARKET=http://127.0.0.1:9 curl -s "$WEB/guess-market/sync" 2>/dev/null)
# Only meaningful if this server was started against a dead market; skipped
# otherwise rather than reported as a failure.
case $down in
    *ServerUnreachable*) ok "a dead market reads as ServerUnreachable" ;;
    *) printf '  skip the dead-market answer (this server has a live market)\n' ;;
esac

rm -f "$JAR"

echo
echo "-----------------------------------------"
printf '%d passed, %d failed\n' "$passed" "$failed"
[ "$failed" -eq 0 ] || exit 1
