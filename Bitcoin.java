package main;

import java.io.*;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import java.security.KeyFactory;
import java.security.Security;
import java.security.MessageDigest;

public class Bitcoin {

	// These are constants in secp256k1
	private static BigInteger curve_p = new BigInteger(
			"115792089237316195423570985008687907853269984665640564039457584007908834671663");
	private static BigInteger curve_a = new BigInteger("0");
	private static BigInteger curve_b = new BigInteger("7");
	// https://github.com/credentials/bouncycastle-ext/blob/master/src/org/bouncycastle/math/ec/pairing/ECCurveWithPairing.java

	private static final String EC_GEN_PARAM_SPEC = "secp256k1";
	private static final String KEY_PAIR_GEN_ALGORITHM = "ECDSA";

	public Bitcoin()

	{

	} // end func

	// https://stackoverflow.com/questions/4582277/biginteger-powbiginteger
	public static BigInteger bigPow(BigInteger base, BigInteger exponent) {

		// This method is to allow exponents on Big Integers.

		BigInteger result = BigInteger.ONE;
		while (exponent.signum() > 0) {
			if (exponent.testBit(0))
				result = result.multiply(base);
			base = base.multiply(base);
			exponent = exponent.shiftRight(1);
		}
		return result;
	}

	public static String[] ECC_YfromX(String x, boolean odd) {

		// This method will return a Y value from a corresponding X value on an elliptic
		// curve.

		String[] retval = new String[2];
		BigInteger bi_x = new BigInteger(x);
		BigInteger bi_Mx, bi_My2, bi_My;
		BigInteger bi_4 = new BigInteger("4");
		BigInteger bi_offset;
		int offset;
		boolean curve_contains_point = false;

		for (offset = 0; offset < 128; offset++) {
			bi_offset = BigInteger.valueOf(offset);

			bi_Mx = bi_x.add(bi_offset);
			// since a = 0 for secp256k1, we can simplify:
			bi_My2 = ((bi_Mx.pow(3)).mod(curve_p)).add(curve_b.mod(curve_p));
			bi_My = (bigPow(bi_My2, ((curve_p.add(BigInteger.ONE)).divide(bi_4)))).mod(curve_p);

			curve_contains_point = curveContainsPoint(bi_Mx, bi_My);

			if (curve_contains_point) {
				if (odd) {
					retval[0] = bi_My.toString();
					retval[1] = bi_offset.toString();
				} else {
					retval[0] = curve_p.subtract(bi_My).toString();
					retval[1] = bi_offset.toString();
				}
			}

			return retval;
		} // end for loop
		System.out.println("ECC_YfromX: No Y found");
		System.exit(0);
		return retval;
	} // end function

	public static String[] ser_to_point(String Aser) {

		// This method used for ECC derivation

		String[] retval = new String[2];
		String x, y;

		if (Aser.substring(0, 2).equals("04")) {
			x = Aser.substring(1, 33);
			y = Aser.substring(33, Aser.length());
			return retval;
		}

		boolean odd = false;
		if (Aser.substring(0, 2) == "03") {
			odd = true;
		}
		String Mx = Aser.substring(1, Aser.length());

		String[] y_parts = ECC_YfromX(Mx, odd);
		retval[0] = Mx;
		retval[1] = y_parts[0];
		return retval;
	}

	public static String base_decode_58(String v) {

		// This method using to decode a string for base58.

		BigInteger bi_58 = new BigInteger("58");
		BigInteger bi_256 = new BigInteger("256");

		String b58chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

		byte[] vBytes = v.getBytes();

		BigInteger val1 = BigInteger.ZERO;
		BigInteger val2 = BigInteger.ZERO;
		BigInteger long_value = BigInteger.ZERO;

		BigInteger cc = BigInteger.ZERO;

		int c = 0;
		int charpos = 0;
		int i = 0;
		BigInteger bi_charpos = BigInteger.ZERO;
		int kounter = vBytes.length - 1;
		int kkk = Util.unsignedToBytes(vBytes[kounter]);

		for (int counter = vBytes.length - 1; counter >= 0; counter--) {
			c = vBytes[counter];
			int ccc = Util.unsignedToBytes(vBytes[counter]);
			cc = BigInteger.valueOf(ccc);
			charpos = b58chars.indexOf(ccc);
			bi_charpos = BigInteger.valueOf(charpos);
			val1 = bi_58.pow(i);
			i++;
			val2 = val1.multiply(bi_charpos);
			long_value = long_value.add(val2);

		}

		BigInteger[] divMod = null;
		BigInteger div = null;
		BigInteger mod = null;
		String result = "";
		String modhex = "";

		// Main decoding loop

		while (long_value.compareTo(bi_256) > -1) {
			divMod = long_value.divideAndRemainder(bi_256);
			div = divMod[0];
			mod = divMod[1];

			modhex = Integer.toHexString(mod.intValue());

			// ensure hexbyte contains leading 0 if necessary.
			if (modhex.length() == 1) {
				modhex = "0" + modhex;
			}

			result = result + modhex;

			long_value = div;
		}
		String long_value_string = long_value.toString();
		if (long_value_string.length() == 1) {
			long_value_string = "0" + long_value_string;
		}
		result = result + long_value_string;

		// Extra zero padding if necessary.
		Byte oneByte = new Byte("1");
		boolean has_leading_zeroes = true;
		int nPad = 0;
		Byte myByte;
		while (has_leading_zeroes) {
			myByte = vBytes[nPad];
			if (myByte.compareTo(oneByte) == 0) {
				nPad++;
			} else {
				has_leading_zeroes = false;
			}

		} // end while

		for (int n = 0; n < nPad; n++) {
			result = result + "00";
		}

		byte[] finalBytes = Util.hexStringToByteArray(result);

		// REVERSE IN PLACE
		for (i = 0; i < finalBytes.length / 2; i++) {
			byte temp = finalBytes[i];
			finalBytes[i] = finalBytes[finalBytes.length - i - 1];
			finalBytes[finalBytes.length - i - 1] = temp;
		}
		// END REVERSE CODE

		result = Util.bytesToHex(finalBytes);
		result = result.substring(0, (result.length() - 8));
		return result;

	}

	public static String base_encode_58(String v) {

		// This method is used to encode a string for base58.

		int base = 58;
		BigInteger bi_58 = new BigInteger("58");
		BigInteger bi_256 = new BigInteger("256");
		String b58chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

		String suffixHash = null;
		suffixHash = Hash(v);

		byte[] vBytes = Util.hexStringToByteArray(v);

		v = v + suffixHash.substring(0, 8);
		int c = 0, i = 0;
		BigInteger val1 = BigInteger.ZERO;
		BigInteger val2 = BigInteger.ZERO;
		BigInteger long_value = BigInteger.ZERO;
		BigInteger cc = BigInteger.ZERO;
		byte[] zz = Util.hexStringToByteArray(v);

		// First prepare main variable "long_value"
		for (int counter = zz.length - 1; counter >= 0; counter--) {

			c = zz[counter];
			int ccc = Util.unsignedToBytes(zz[counter]);

			cc = BigInteger.valueOf(ccc);
			val1 = bi_256.pow(i);

			val2 = val1.multiply(cc);

			long_value = long_value.add(val2);
			i++;

		}

		BigInteger[] divMod = null;
		BigInteger div = null;
		BigInteger mod = null;
		String mychar = "";
		String result = "";

		// Main encoding loop
		while (long_value.compareTo(bi_58) > -1) {
			divMod = long_value.divideAndRemainder(bi_58);
			div = divMod[0];
			mod = divMod[1];
			mychar = b58chars.substring(mod.intValue(), mod.intValue() + 1);
			result = result + mychar;
			long_value = div;
		}

		// handle final character
		mychar = b58chars.substring(long_value.intValue(), long_value.intValue() + 1);
		result = result + mychar;

		// Extra zero padding if necessary.
		Byte zeroByte = new Byte("0");
		boolean has_leading_zeroes = true;
		int nPad = 0;
		Byte myByte;
		while (has_leading_zeroes) {
			myByte = vBytes[nPad];
			if (myByte.compareTo(zeroByte) == 0) {
				nPad++;
			} else {
				has_leading_zeroes = false;
			}

		} // end while

		for (int n = 0; n < nPad; n++) {
			result = result + "0";
		}

		StringBuilder retval = new StringBuilder();
		retval.append(result);
		retval = retval.reverse();
		return retval.toString();
	}

	public static String Hash(String x) {

		// This method will perform a double SHA-256 Hash

		String out = null;
		MessageDigest digest = null;
		byte[] xBytes = null;

		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NO SUCH ALGORITHM EXCEPTION");
			System.exit(0);
		}

		xBytes = Util.hexStringToByteArray(x);
		// --- Hash twice:
		xBytes = digest.digest(xBytes);
		xBytes = digest.digest(xBytes);

		// end Double Hashing--------
		out = Util.bytesToHex(xBytes);
		return out;
	}

	public static String ripeHash(String x) {

		// This method will peform a RIPEMD160 Hash of the SHA256 Hash.
		String out = null;
		MessageDigest digest = null;
		MessageDigest digest2 = null;
		byte[] xBytes = null;

		try {
			digest = MessageDigest.getInstance("SHA-256");
			digest2 = MessageDigest.getInstance("RIPEMD160");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NO SUCH ALGORITHM EXCEPTION");
			System.exit(0);
		}

		xBytes = Util.hexStringToByteArray(x);
		// --- Hash sha256
		xBytes = digest.digest(xBytes);
		// ripemd160
		xBytes = digest2.digest(xBytes);
		out = Util.bytesToHex(xBytes);
		return out;
	}

	public static String getHexPubKeyfromECkeys(ECPublicKey ecPublicKey, boolean compressed) {

		// This method will get a string version of Pubkey from ECC object

		ECPoint ec = ecPublicKey.getQ();
		BigInteger affineXCoord = ec.getAffineXCoord().toBigInteger();
		BigInteger affineYCoord = ec.getAffineYCoord().toBigInteger();
		if (!compressed) {
			return String.format("%064x", affineXCoord) + String.format("%064x", affineYCoord);
		} else {
			// If odd, use 03 otherwise 02 - standard key compression rule
			if (affineYCoord.and(BigInteger.ONE).compareTo(BigInteger.ONE) == 0) {
				return "03" + String.format("%064x", affineXCoord);
			} else {
				return "02" + String.format("%064x", affineXCoord);
			}
		}
	}

	public static String serializeXprv(byte[] c, byte[] k) {

		String xprv = "0488ADE4000000000000000000" + Util.bytesToHex(c) + "00" + Util.bytesToHex(k);
		return xprv;
	}

	public static String serializeXpub(byte[] c, byte[] Ck) {

		String xpub = "0488B21E000000000000000000" + Util.bytesToHex(c) + Util.bytesToHex(Ck);
		return xpub;
	}

	public static String[] deserialize_xkey(String xkey, boolean prv) {
		xkey = base_decode_58(xkey);
		String depth = xkey.substring(8, 10);
		String fingerprint = xkey.substring(10, 18);
		String child_number = xkey.substring(18, 26);
		String c = xkey.substring(26, 90);

		if (!prv) {
			if ((!xkey.substring(0, 8).equals("0488B21E")) && (!xkey.substring(0, 8).equals("0488b21e"))) {
				System.out.println("BAD KEY HEADER FAILURE.");
				System.exit(0);
			}
		}

		if (prv) {
			if ((!xkey.substring(0, 8).equals("0488ADE4")) && (!xkey.substring(0, 8).equals("0488ade4"))) {
				System.out.println("BAD KEY HEADER FAILURE.");
				System.exit(0);
			}
		}

		int n = 32;
		if (prv) {
			n = 33;
		}

		String K_or_k;
		K_or_k = xkey.substring(26 + (n * 2), xkey.length());
		String[] retval = new String[6];
		retval[0] = "standard"; // xtype
		retval[1] = depth;
		retval[2] = fingerprint;
		retval[3] = child_number;
		retval[4] = c;
		retval[5] = K_or_k;

		return retval;
	}

	public static byte[] get_root512_from_bip32root(byte[] bip32root) {
		byte[] root512 = hmac_sha_512_bytes(bip32root, "Bitcoin seed");
		return root512;
	}

	public static String fetchPrivateKey(String masterXPRV, boolean change, int index) {

		// This function returns a private key for an address. Main wrapper function.

		String deserializedXprvPieces[] = Bitcoin.deserialize_xkey(masterXPRV, true);
		String xprv_c = deserializedXprvPieces[4];
		String xprv_k = deserializedXprvPieces[5];

		int change_int = 0;
		if (change) {
			change_int = 1;
		}

		String[] initial = Bitcoin.CKD_priv(xprv_k, xprv_c, change_int);

		String[] second = Bitcoin.CKD_priv(initial[0], initial[1], index);

		String retval = second[0];

		return retval;

	}

	public static String[] CKD_priv(String k, String c, int n) {

		// Child Key Derivation Private Key function

		BigInteger BIP32_PRIME = new BigInteger("80000000", 16);
		BigInteger bi_n = BigInteger.valueOf(n);
		BigInteger bi_prime = bi_n.and(BIP32_PRIME);
		boolean is_prime;

		if (bi_prime.compareTo(BigInteger.ZERO) == 1) {
			is_prime = true;
		} else {
			is_prime = false;
		}

		String hex_n = Integer.toHexString(n);
		hex_n = Util.padLeftHexString(hex_n, 8);

		String[] retval = _CKD_priv(k, c, hex_n, is_prime);
		return retval;

	}

	public static String[] _CKD_priv(String k, String c, String s, boolean is_prime) {

		// This function does the heavy lifting for CKD private key

		// SECP256k1 ORDER
		Security.addProvider(new BouncyCastleProvider());
		ECParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec(EC_GEN_PARAM_SPEC);
		BigInteger order = ecParameterSpec.getN();

		byte[] kBytes = Util.hexStringToByteArray(k);

		String cK = get_pubkey_from_secret(kBytes);

		// System.out.println("cK is "+cK);
		String data;

		if (is_prime) {
			// for hardened keys. Isn't used in first version of this wallet
			data = "00" + k + s;
		}

		else {
			data = cK + s;
		}

		byte[] dataBytes = Util.hexStringToByteArray(data);
		byte[] I = hmac_sha_512_bytes_from_hex(dataBytes, c);

		String I_hex = Util.bytesToHex(I);
		String I_hex32 = I_hex.substring(0, 64); // first 32 bytes (64 chars)
		String I_hex32b = I_hex.substring(64, I_hex.length()); // first 32 bytes (64 chars)

		BigInteger bi_I32 = new BigInteger(I_hex32, 16);
		BigInteger bi_k = new BigInteger(k, 16);

		BigInteger val1 = bi_I32.add(bi_k);
		BigInteger val2 = val1.mod(order);
		String k_n = val2.toString(16);
		// Pad this so the key is exactly 32 bytes
		k_n = Util.padLeftHexString(k_n, 64);

		String[] retval = new String[2];

		retval[0] = k_n;

		retval[1] = I_hex32b;
		return retval;
	}

	public static String[] _CKD_pub(String cK, String c, String s) {

		// This function does the heavy lifting for CKD public key.

		// SECP256k1 ORDER
		Security.addProvider(new BouncyCastleProvider());
		ECParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec(EC_GEN_PARAM_SPEC);
		BigInteger order = ecParameterSpec.getN();

		String data = cK + s;
		byte[] dataBytes = Util.hexStringToByteArray(data);
		byte[] I = hmac_sha_512_bytes_from_hex(dataBytes, c);
		String I_hex = Util.bytesToHex(I);
		String I_hex32 = I_hex.substring(0, 64); // first 32 bytes (64 chars)
		BigInteger bi_I32 = new BigInteger(I_hex32, 16);
		// System.out.println("bi I32 is "+ bi_I32);

		// ECPoint ecPoint; // = ecParameterSpec.getG();
		byte[] cK_bytes = new BigInteger(cK, 16).toByteArray();

		ECPoint ecPoint = ecParameterSpec.getG().multiply(bi_I32).add(ecParameterSpec.getCurve().decodePoint(cK_bytes));
		KeySpec publicKeySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
		PublicKey publicKey = null;
		KeyFactory keyFactory = null;
		try {
			keyFactory = KeyFactory.getInstance(KEY_PAIR_GEN_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NO SUCH ALGORITHM EXCEPTION");
			System.exit(0);
		}
		try {
			publicKey = keyFactory.generatePublic(publicKeySpec);
		} catch (InvalidKeySpecException e) {
			System.out.println("INVALID KEY SPEC EXCEPTION");
			System.exit(0);
		}
		ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
		String pubKeyHexFormat = getHexPubKeyfromECkeys(ecPublicKey, true);
		String retval[] = new String[2];
		retval[0] = pubKeyHexFormat;
		retval[1] = I_hex.substring(64);
		return retval;
	}

	public static String get_pubkey_from_secret(byte[] secret) {

		// This function is to get the public key from private key, mostly for XPUB.

		// Bouncy Castle used to provide Crypto Libraries
		Security.addProvider(new BouncyCastleProvider());

		// Format Secret Exponent (the Private Key) into an Integer
		BigInteger secretExponent = new BigInteger(Util.bytesToHex(secret), 16);

		// Calculate the Public Key
		ECParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec(EC_GEN_PARAM_SPEC);
		ECPoint ecPoint = ecParameterSpec.getG().multiply(secretExponent);

		KeySpec publicKeySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);

		PublicKey publicKey = null;
		KeyFactory keyFactory = null;
		try {
			keyFactory = KeyFactory.getInstance(KEY_PAIR_GEN_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NO SUCH ALGORITHM EXCEPTION");
			System.exit(0);
		}
		try {
			publicKey = keyFactory.generatePublic(publicKeySpec);

			// privateKey = keyFactory.generatePublic(publicKeySpec);
		} catch (InvalidKeySpecException e) {
			System.out.println("INVALID KEY SPEC EXCEPTION");
			System.exit(0);
		}

		// Native java library does not provide good format, so use Bouncy Castle class:
		ECPublicKey ecPublicKey = (ECPublicKey) publicKey;

		// Extract and return Hex Value:
		String pubKeyHexFormat = getHexPubKeyfromECkeys(ecPublicKey, true);
		// System.out.println("DEBUG pubkey is " + pubKeyHexFormat);
		return pubKeyHexFormat;
	}

	public static String hmac_sha_512(String message, String key) {

		// There are 3 versions of the mac function depending on what format our data is
		// in.

		// SOURCE:
		// https://stackoverflow.com/questions/39355241/compute-hmac-sha512-with-secret-key-in-java

		Mac sha512_HMAC = null;
		String result = null;

		try {
			byte[] byteKey = key.getBytes("UTF-8");
			final String HMAC_SHA512 = "HmacSHA512";
			sha512_HMAC = Mac.getInstance(HMAC_SHA512);
			SecretKeySpec keySpec = new SecretKeySpec(byteKey, HMAC_SHA512);
			sha512_HMAC.init(keySpec);
			byte[] mac_data = sha512_HMAC.doFinal(message.getBytes("UTF-8"));
			result = Util.bytesToHex(mac_data);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// We're done!
		}

		return result;
	}

	public static byte[] hmac_sha_512_bytes(byte[] message, String key) {

		// SOURCE:
		// https://stackoverflow.com/questions/39355241/compute-hmac-sha512-with-secret-key-in-java

		Mac sha512_HMAC = null;
		byte[] mac_data = null;
		try {
			byte[] byteKey = key.getBytes("UTF-8");
			final String HMAC_SHA512 = "HmacSHA512";
			sha512_HMAC = Mac.getInstance(HMAC_SHA512);
			SecretKeySpec keySpec = new SecretKeySpec(byteKey, HMAC_SHA512);
			sha512_HMAC.init(keySpec);
			mac_data = sha512_HMAC.doFinal(message);

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// We're done!
		}

		return mac_data;
	}

	public static byte[] hmac_sha_512_bytes_from_hex(byte[] message, String hexkey) {

		// SOURCE:
		// https://stackoverflow.com/questions/39355241/compute-hmac-sha512-with-secret-key-in-java

		Mac sha512_HMAC = null;
		byte[] mac_data = null;
		try {
			byte[] byteKey = Util.hexStringToByteArray(hexkey);
			final String HMAC_SHA512 = "HmacSHA512";
			sha512_HMAC = Mac.getInstance(HMAC_SHA512);
			SecretKeySpec keySpec = new SecretKeySpec(byteKey, HMAC_SHA512);
			sha512_HMAC.init(keySpec);
			mac_data = sha512_HMAC.doFinal(message);

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// We're done!
		}

		return mac_data;
	}

	public static boolean curveContainsPoint(BigInteger x, BigInteger y) {

		// Implemented natively here rather than use a library.
		// This function should therefore be heavily code reviewed.

		// expression is: (y*y - ((x*x*x) + b)) % p == 0
		BigInteger expression;
		BigInteger ySquared = y.multiply(y);
		BigInteger xCubed = x.multiply(x).multiply(x);
		expression = (ySquared.subtract(xCubed.add(curve_b))).mod(curve_p);
		if (expression.compareTo(BigInteger.ZERO) == 0) {
			return true;
		} else {
			return false;
		}
	}

} // end class
