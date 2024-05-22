import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.*;
import java.util.Base64;

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
    }

    public Cryptographer(String keyGenAlgorithm,
                         String transformationSym,
                         int keySizeSym,
                         String keyPairGenAlgorithm,
                         String transformationAsym,
                         int keySizeAsym) {
        this.keyGenAlgorithm = keyGenAlgorithm;
        this.transformationSym = transformationSym;
        this.keySizeSym = keySizeSym;
        this.keyPairGenAlgorithm = keyPairGenAlgorithm;
        this.transformationAsym = transformationAsym;
        this.keySizeAsym = keySizeAsym;
    }

    public void exchangeKeys(ObjectInputStream ois, ObjectOutputStream oos) throws Exception {
        System.err.println(Thread.currentThread().getName() + " Cryptographer.exchangeKeys");

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
     * Returns a hashed version of the own public key
     * @return
     */
    public String getOwnPublicKey() {
        if (ownPublicKey == null) {
            throw new IllegalStateException("Own public key has not been generated yet.");
        }
        return writeBytesInBase64(digest(ownPublicKey.getEncoded()));
    }

    /**
     * Returns a hashed version of the other's public key
     * @return
     */
    public String getOthersPublicKey() {
        if (ownPublicKey == null) {
            throw new IllegalStateException("Other's public key has not been retrieved yet.");
        }
        return writeBytesInBase64(digest(othersPublicKey.getEncoded()));
    }

    public SignedObject cipher(String message) throws Exception {
        return sign(encrypt(message));
    }

    public String decipher(SignedObject signedObject) throws Exception {
        return decryptMessage(verify(signedObject));
    }


    // helper methods for symmetric cryptography //
    /**
     * Instantiates a secret key of the given algorithm and key size.
     * @return the SecretKey instance
     * @throws Exception if something went wrong, e.g. the algorithm is not supported
     */
    private SecretKey getSecretKey() throws Exception {
        try {
            var keyGenerator = KeyGenerator.getInstance(keyGenAlgorithm);
            keyGenerator.init(keySizeSym, new SecureRandom());
            return keyGenerator.generateKey();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new Exception("Something went wrong while generating the secret key");
        }
    }

    /**
     *
     * @param message the unencrypted String
     * @return the encrypted String
     * @throws Exception if the encryption failed for any reason
     */
    private SealedObject encrypt(String message) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(transformationSym);
            cipher.init(Cipher.ENCRYPT_MODE, ownSecretKey);
            return new SealedObject(message, cipher);
        } catch (Exception e) {
            throw new Exception("Something went wrong during the encryption of a message");
        }
    }

    /**
     *
     * @param sealedObject the encrypted String object
     * @return the decrypted String
     * @throws Exception if the decryption failed for any reason
     */
    private String decryptMessage(SealedObject sealedObject) throws Exception {
        try {
            return (String) sealedObject.getObject(othersSecretKey);
        } catch (Exception e) {
            throw new Exception("Something went wrong during the decryption of a message");
        }
    }


    // helper methods for asymmetric cryptography //

    /**
     * Generates a public/private key pair using the given algorithm and key size
     * @return the KeyPair
     * @throws Exception if the key pair could not be generated, e.g. if the encryption algorithm
     * is not supported.
     */
    private KeyPair getKeyPair() throws Exception {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyPairGenAlgorithm);
            keyGen.initialize(keySizeAsym, new SecureRandom());
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new Exception("Something went wrong while generating the public/private keys");
        }
    }

    private SealedObject encrypt(SecretKey secretKey) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(transformationAsym);
            cipher.init(Cipher.ENCRYPT_MODE, othersPublicKey);
            return new SealedObject(secretKey, cipher);
        } catch (Exception e) {
            throw new Exception("Something went wrong when encrypting your secret key");
        }
    }

    private SecretKey decryptKey(SealedObject sealedObject) throws Exception {
        try {
            return (SecretKey) sealedObject.getObject(ownPrivateKey);
        } catch (Exception e) {
            throw new Exception("Something went wrong when decrypting your friend's secret key");
        }
    }

    private SignedObject sign(SealedObject sealedObject) throws Exception {
        try {
            Signature signingEngine = Signature.getInstance(DEFAULT_SIGNING_ALGORITHM);
            return new SignedObject(sealedObject, ownPrivateKey, signingEngine);
        } catch (Exception e) {
            throw new Exception("Something went wrong while generating a signature");
        }
    }

    private SealedObject verify(SignedObject signedObject) throws Exception {
        try {
            Signature verificationEngine = Signature.getInstance(DEFAULT_SIGNING_ALGORITHM);
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
     * @param data
     * @return
     */
    private byte[] digest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }
}
