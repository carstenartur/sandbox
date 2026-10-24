import java.math.BigInteger;
public class Calculation {
  public static long[] compute(long seed, long baseSeed) {
    BigInteger base = BigInteger.valueOf(baseSeed);
    BigInteger exponent = BigInteger.valueOf(seed & Integer.MAX_VALUE);
    final java.math.BigInteger _math0 = new java.math.BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951");
    final java.math.BigInteger _math1 = base.modPow(exponent, _math0);
    final java.math.BigInteger _math2 = _math1.multiply(base).mod(_math0);
    BigInteger left = (_math2.multiply(_math1).mod(_math0));
    BigInteger right = _math2;
    return new long[] {left.longValue(), right.longValue()};
  }
}
