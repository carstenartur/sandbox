# Quellenbelegte Feld-Aufrufkontexte

## Auftrag

Carsten beauftragt die Umsetzung des Kontextnachweises: mathematisch
gleichwertige Zwischenwerte dürfen sich unterscheiden, wenn ihre anschließende
Beobachtung gleich bleibt. Bereits aus dem Quellpfad belegte Voraussetzungen
sollen unnötige Laufzeitprüfungen vermeiden. Der allgemeine mathematische
Beleg liegt in Regelsuche, die Java-Quelle und ihre Effekte bleiben in Sandbox.

## Gewählte erste Scheibe: Aufrufkontext und Guard-Fakten

Die bestehende lokale EXACT_PAIR-Optimierung prüft alle Voraussetzungen im
erzeugten Helfer. Eine getrennte Aufrufanalyse kann einzelne Fakten aus den
tatsächlich ausgeführten vorherigen Anweisungen ableiten. Sie darf den
ursprünglichen Helfer deshalb nicht für beliebige andere Aufrufer abschwächen.

Für eine belegte Aufrufstelle wird ein privater statischer tryCandidate-Helfer
erzeugt. Er liefert nur bei allen verbleibenden Voraussetzungen einen
unabhängig geprüften EXACT_PAIR-Kandidaten; andernfalls null. Der ursprüngliche
Aufruf bleibt als direkter Fallback an derselben Stelle erhalten. Seine drei
Argumente sind stabile lokale Referenzen; die Umformung darf sie nicht erneut
mit beobachtbaren Effekten berechnen. Eine einfache lokale Array-Zuweisung mit
konstantem Index oder eine lokale Deklaration ist die erste unterstützte
Zielgrammatik. Der Fabrik-Sink muss bei normaler Rückkehr nachweisbar nie null
liefern. Direktaufrufe des ursprünglichen Helfers bleiben exakt.

`JavaFieldCallSite` bindet Methoden, Argumentrollen, Einfügeort, ursprünglichen
Fallback und die neue private Methode. `JavaFieldGuardFacts` rekonstruiert
normal-return- und Dominanzfakten. Neue Fakten werden als eigene, untrusted
Evidenz aufgezeichnet und beim Replay aus dem kombinierten erzeugten AST und
den gebundenen Quellabhängigkeiten erneut hergeleitet. Ein selbst behaupteter
Guard-Maskenwert genügt nicht.

## Zulässige Fakten

- Eine tatsächliche `new BigInteger(1, bytes)`-Rückgabe beweist exakte Klasse,
  Nichtnull und Nichtnegativität; statische/finale Quell-Forwarder dürfen diesen
  Nachweis erhalten. Nichtfinale Dispatchziele und unbekannte Rückgaben nicht.
- Numerische JDK-Postbedingungen wie die kanonische Restklasse werden von der
  Frage nach der exakten Laufzeitklasse getrennt. Ohne gebundenen Runtime-Audit
  beweist ein mod/modPow-Returntyp allein keine exakte Klasse.
- Eine dominante lokale Gleichheit `g*g mod p == (a*a+b*b) mod p` kann auf dem
  wahren Pfad die Normbedingung belegen. Modulus, Rollen und Flag müssen gebunden
  sein; spätere abweichende Writes, Aliase oder Effekte entkräften den Beleg.
- Zyklische Klasseninitialisierung darf keine angenommene Nichtnull-Konstante
  erzeugen. Nicht bewiesene Bedingungen bleiben im Laufzeitguard.
- Alle virtuellen Zugriffe bleiben hinter den erforderlichen Klassen-/Null-
  Bedingungen. Bei Unterklassen und fremden Eingaben bleibt der direkte
  Originalpfad mit seinen Effekten und Ausnahmen erhalten.

## Grenzen und Folgearbeit

Diese Scheibe nutzt den unveränderten SDK-Pin und EXACT_PAIR. Die Jacobi-
Korrektur bleibt erhalten. Die folgende Scheibe verbindet den Aufrufnachweis
mit dem neuen allgemeinen Normalisierungsbeleg: ein frisches skalares Paar
wird vor jeder weiteren Beobachtung gemeinsam auf ein unverändertes Zielbit
normalisiert und erst dann an einen nachgewiesenen Wertsink übergeben.

Bouncy Castles aktuelle Montgomery-Limb-Methoden erhalten keine algebraische
Freigabe aus ihren Namen oder aus Testvektoren. Eine spekulative Doppelrechnung
würde einen eigenen vollständigen Wiederholbarkeits-, Eigentums- und
Ausnahmebeweis benötigen und ist nicht Bestandteil dieser Scheibe.

## Abnahme

Normale MathematicalAnalysis, Apply, unabhängiger Quellen-Replay und Undo sind
der einzige Produktweg. Umbenannte Quellen, beide Normzweige, Null/reelle
Eingaben, BigInteger-Unterklassen, geänderte Getter/Flags/Moduli und manipulierte
Belege bilden die Gegenproben. Der unveränderte Bouncy-Castle-Commit wird durch
die vorhandene JGit-Fixture qualifiziert; direkte Rohwurzeln bleiben exakt.
Java 25 `-Xlint:all -Werror` gilt auch für Tests. Maven/JUnit bleiben die
ausführbare Autorität. Zeitgewinne werden nur nach echter Messung behauptet.
