import java.math.BigInteger;
public class Calculation {
  public static long[] compute(long seed, long baseSeed) {
    BigInteger base = BigInteger.valueOf(baseSeed);
    BigInteger exponent = BigInteger.valueOf(seed & Integer.MAX_VALUE);
    BigInteger modulus = new BigInteger("65537");
    BigInteger odd = exponent.multiply(BigInteger.TWO).add(BigInteger.ONE);
    BigInteger left = base.modPow(odd, modulus);
    BigInteger right = base.modPow(exponent.add(BigInteger.ONE), modulus);
    return new long[] {left.longValue(), right.longValue()};
  }
}
