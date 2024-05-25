import javax.crypto.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.*;
import java.util.Base64;

/**
 * Handles all cryptography (symmetric as well as asymmetric).
 */
public class Cryptographer {

    private static final String DEFAULT_KEY_GENERATOR_ALGORITHM = "Blowfish";
    private static final String DEFAULT_TRANSFORMATION_SYMMETRIC = "Blowfish/ECB/PKCS5Padding";
    private static final int DEFAULT_KEY_SIZE_SYMMETRIC = 448;
    private static final String DEFAULT_KEY_PAIR_GENERATOR_ALGORITHM = "RSA";
    private static final String DEFAULT_TRANSFORMATION_ASYMMETRIC = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int DEFAULT_KEY_SIZE_ASYMMETRIC = 2048;
    private static final String DEFAULT_SIGNING_ALGORITHM = "SHA256withRSA";



    private final String keyGenAlgorithm;
    private final String transformationSym;
    private final int keySizeSym;
    private final String keyPairGenAlgorithm;
    private final String transformationAsym;
    private final int keySizeAsym;
    private final String signingAlgorithm;


    private SecretKey ownSecretKey;
    private SecretKey othersSecretKey;
    private PrivateKey ownPrivateKey;
    private PublicKey ownPublicKey;
    private PublicKey othersPublicKey;


    public Cryptographer() {
        this.keyGenAlgorithm = DEFAULT_KEY_GENERATOR_ALGORITHM;
        this.transformationSym = DEFAULT_TRANSFORMATION_SYMMETRIC;
        this.keySizeSym = DEFAULT_KEY_SIZE_SYMMETRIC;
        this.keyPairGenAlgorithm = DEFAULT_KEY_PAIR_GENERATOR_ALGORITHM;
        this.transformationAsym = DEFAULT_TRANSFORMATION_ASYMMETRIC;
        this.keySizeAsym = DEFAULT_KEY_SIZE_ASYMMETRIC;
        this.signingAlgorithm = DEFAULT_SIGNING_ALGORITHM;
    }

    public Cryptographer(String keyGenAlgorithm,
                         String transformationSym,
                         int keySizeSym,
                         String keyPairGenAlgorithm,
                         String transformationAsym,
                         int keySizeAsym,
                         String signingAlgorithm) {
        this.keyGenAlgorithm = keyGenAlgorithm;
        this.transformationSym = transformationSym;
        this.keySizeSym = keySizeSym;
        this.keyPairGenAlgorithm = keyPairGenAlgorithm;
        this.transformationAsym = transformationAsym;
        this.keySizeAsym = keySizeAsym;
        this.signingAlgorithm = signingAlgorithm;
    }

    /**
     * Exchanges secret keys with remote host by means of asymmetric cryptography.
     * @param ois the input stream on which to read objects sent from remote host
     * @param oos the output stream to which to write objects to remote host
     * @throws Exception
     */
    public void exchangeKeys(ObjectInputStream ois, ObjectOutputStream oos) throws Exception {

        // generate a secret key for symmetric encryption and decryption of messages
        ownSecretKey = getSecretKey();

        // generate a key pair for asymmetric cryptography (used for the exchange of secret keys)
        KeyPair keyPair = getKeyPair();
        ownPrivateKey = keyPair.getPrivate();
        ownPublicKey = keyPair.getPublic();

        // send public key to remote host (with which remote host will encrypt his secret key)
        oos.writeObject(ownPublicKey);
        oos.flush();

        // get remote host's public key
        othersPublicKey = (PublicKey) ois.readObject();

        // encrypt own secret key using remote host's public key
        SealedObject ownEncryptedKey = encrypt(ownSecretKey);

        // send encrypted key to remote host
        oos.writeObject(ownEncryptedKey);
        oos.flush();

        // get remote host's encrypted secret key
        SealedObject othersEncryptedKey = (SealedObject) ois.readObject();

        // decrypt remote host's encrypted secret key with own private key
        othersSecretKey = decryptKey(othersEncryptedKey);
    }

    /**
     * @return a hashed version of the own public key
     */
    public String getOwnPublicKey() throws NoSuchAlgorithmException {
        if (ownPublicKey == null) {
            throw new IllegalStateException("Own public key has not been generated yet.");
        }
        return writeBytesInBase64(digest(ownPublicKey.getEncoded()));
    }

    /**
     * @return a hashed version of the other's public key
     */
    public String getOthersPublicKey() throws NoSuchAlgorithmException {
        if (ownPublicKey == null) {
            throw new IllegalStateException("Other's public key has not been retrieved yet.");
        }
        return writeBytesInBase64(digest(othersPublicKey.getEncoded()));
    }

    /**
     * Encrypts the given message with user's secret key, then signs it with user's
     * own private key.
     * @param message the message to be ciphered
     * @return the ciphered message
     * @throws Exception
     */
    public SignedObject cipher(String message) throws Exception {
        return sign(encrypt(message));
    }

    /**
     * Verifies the signature with remote host's public key, then decrypts it with
     * remote host's secret key.
     * @param signedObject
     * @return
     * @throws Exception
     */
    public String decipher(SignedObject signedObject) throws Exception {
        return decryptMessage(verify(signedObject));
    }


    // helper methods for symmetric cryptography //

    /**
     * Instantiates a secret key of the given algorithm and key size.
     * @return the SecretKey instance
     * @throws NoSuchAlgorithmException if e.g. the algorithm is not supported
     */
    private SecretKey getSecretKey() throws NoSuchAlgorithmException {
        var keyGenerator = KeyGenerator.getInstance(keyGenAlgorithm);
        keyGenerator.init(keySizeSym, new SecureRandom());
        return keyGenerator.generateKey();
    }

    /**
     *
     * @param message the unencrypted String
     * @return the encrypted String
     * @throws Exception if the encryption failed for any reason
     */
    private SealedObject encrypt(String message) throws Exception {
        Cipher cipher = Cipher.getInstance(transformationSym);
        cipher.init(Cipher.ENCRYPT_MODE, ownSecretKey);
        return new SealedObject(message, cipher);
    }

    /**
     *
     * @param sealedObject the encrypted String object
     * @return the decrypted String
     * @throws Exception if the decryption failed for any reason
     */
    private String decryptMessage(SealedObject sealedObject) throws Exception {
        return (String) sealedObject.getObject(othersSecretKey);
    }


    // helper methods for asymmetric cryptography //

    /**
     * Generates a public/private key pair using the given algorithm and key size
     * @return the KeyPair
     * @throws Exception if the key pair could not be generated, e.g. if the encryption algorithm
     * is not supported.
     */
    private KeyPair getKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyPairGenAlgorithm);
        keyGen.initialize(keySizeAsym, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    private SealedObject encrypt(SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance(transformationAsym);
        cipher.init(Cipher.ENCRYPT_MODE, othersPublicKey);
        return new SealedObject(secretKey, cipher);
    }

    private SecretKey decryptKey(SealedObject sealedObject) throws Exception {
        return (SecretKey) sealedObject.getObject(ownPrivateKey);
    }

    private SignedObject sign(SealedObject sealedObject) throws Exception {
        Signature signingEngine = Signature.getInstance(signingAlgorithm);
        return new SignedObject(sealedObject, ownPrivateKey, signingEngine);
    }

    private SealedObject verify(SignedObject signedObject) throws Exception {
        try {
            Signature verificationEngine = Signature.getInstance(signingAlgorithm);
            if (signedObject.verify(othersPublicKey, verificationEngine)) {
                return (SealedObject) signedObject.getObject();
            }
            throw new Exception("Could not verify signature");
        } catch (Exception e) {
            throw new Exception("Something went wrong when verifying a signature");
        }
    }


    // other helper methods //

    /**
     * Encodes the passed public key using Base64 MIME encoding.
     * @param data the data to encode
     * @return the Base64 representation as a String
     */
    private String writeBytesInBase64(byte[] data) {
        var encoder = Base64.getMimeEncoder();
        return encoder.encodeToString(data);
    }

    /**
     * Generates an MD5 hash of the passed data (e.g. a public key)
     * @param data the data to be digested
     * @return the digested data
     * @throws NoSuchAlgorithmException if e.g. the algorithm is not supported
     */
    private byte[] digest(byte[] data) throws NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return md.digest();
    }
}
