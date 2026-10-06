import java.math.BigInteger;
public class Calculation {
  public static long[] compute(long seed, long baseSeed) {
    BigInteger base = BigInteger.valueOf(baseSeed);
    BigInteger exponent = BigInteger.valueOf(seed & Integer.MAX_VALUE);
    final java.math.BigInteger _math0 = new java.math.BigInteger("65537");
    final java.math.BigInteger _math1 = exponent;
    final java.math.BigInteger _math2 = new java.math.BigInteger("2");
    final java.math.BigInteger _math3 = _math1.multiply(_math2);
    final java.math.BigInteger _math4 = java.math.BigInteger.ONE;
    final java.math.BigInteger _math5 = _math3.add(_math4);
    final java.math.BigInteger _math6 = base;
    final java.math.BigInteger _math7 = _math6.modPow(_math1, _math0);
    final java.math.BigInteger _math8 = _math6.modPow(_math4, _math0);
    final java.math.BigInteger _math9 = _math7.multiply(_math8).mod(_math0);
    final java.math.BigInteger _math10 = _math9.multiply(_math7).mod(_math0);
    BigInteger modulus = _math0;
    BigInteger odd = _math5;
    BigInteger left = _math10;
    BigInteger right = _math9;
    return new long[] {left.longValue(), right.longValue()};
  }
}
