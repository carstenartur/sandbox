#!/usr/bin/env bash
# Capture the distribution Maven JVM only while Tycho is still discovering the reactor.
set -eu
log=$1
dump=$2
if ! grep -Fq '[INFO] Scanning for projects...' "$log" || grep -Fq '[INFO] Reactor Build Order:' "$log"; then
  exit 0
fi
while read -r pid main args; do
  if [[ "$pid" =~ ^[0-9]+$ && "$main" == org.codehaus.plexus.classworlds.launcher.Launcher && " $args " == *" -Pdistribution,cli-dist,swtbot "* ]]; then
    mkdir -p "$(dirname "$dump")"
    if [[ "${RUNNER_OS:-}" == Linux ]]; then
      # HotSpot prints SIGQUIT diagnostics to Maven's log even if attach is unavailable.
      offset=$(wc -c < "$log")
      kill -QUIT "$pid"
      for ((i = 0; i < 10; i++)); do
        sleep 1
        tail -c "+$((offset + 1))" "$log" > "$dump"
        if grep -q 'JNI global refs' "$dump"; then break; fi
      done
    else
      jcmd "$pid" Thread.print -l > "$dump"
      cat "$dump"
    fi
    exit 0
  fi
done < <(jcmd -l)
