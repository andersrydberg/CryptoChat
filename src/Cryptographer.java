import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
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


        // send encrypted key to remote host


    }

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
            throw new Exception("Could not generate a key");
        }
    }

    /**
     * Encodes the passed byte array using Base64 MIME encoding.
     * @param data the data (encrypted or unencrypted) to write
     * @return the Base64 representation as a String
     */
    private String writeBytesInBase64(byte[] data) {
        var encoder = Base64.getMimeEncoder();
        return encoder.encodeToString(data);
    }

    /**
     *
     * @param cleartext a byte array of the unencrypted data
     * @return a byte array of the encrypted data
     * @throws Exception if the encryption failed for any reason
     */
    private byte[] encrypt(byte[] cleartext) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(transformationSym);
            cipher.init(Cipher.ENCRYPT_MODE, ownSecretKey);
            return cipher.doFinal(cleartext);
        } catch (Exception e) {
            throw new Exception("Something went wrong during the encryption process");
        }
    }

    /**
     *
     * @param ciphertext a byte array of the encrypted data
     * @return a byte array of the decrypted data
     * @throws Exception if the decryption failed for any reason
     */
    private byte[] decrypt(byte[] ciphertext) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(transformationSym);
            cipher.init(Cipher.DECRYPT_MODE, othersSecretKey);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new Exception("Something went wrong during the decryption process");
        }
    }


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

}
