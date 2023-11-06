package edu.grossmont.cryptography;


import java.io.FileOutputStream;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.TreeSet;

import javax.crypto.Cipher;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by rgillespie on 8/8/2018.
 */

public class RsaProcessor {

    public static void main(String [] args) throws Exception {

        // Test key generation and encrypt/decrypt.
        try {

            //listSecurityAlgosSupported();

            // Generate keys.
            RsaKeyPair oPair = RsaProcessor.generatePrivatePublicKeys();
            System.out.println("private key: " + oPair.privateKey);
            System.out.println("public key: " + oPair.publicKey);


            //saveKeysToFiles(oPair.privateKey, oPair.publicKey);


            String sSigned = applyECDSASig(oPair.privateKey, "ozymandias");
            boolean bWorks = verifyECDSASig(oPair.publicKey, "ozymandias",sSigned);

            System.out.println("works: " + bWorks);

            //listSecurityAlgosSupported();

            /*
            String sMessage = "secret";
            System.out.println("Encrypting this message: " + sMessage);

            RsaProcessor oProcessor = new RsaProcessor();

            // Encrypt.
            String sEncryptedMessage = oProcessor.encrypt(oPair.privateKey, sMessage);
            System.out.println("Encrypted: " + sEncryptedMessage);

            // Decrypt.
            String sDecryptedMessage = oProcessor.decrypt(oPair.publicKey, sEncryptedMessage);
            System.out.println("Decrypted: " + sDecryptedMessage);
            */
        }
        catch(Exception ex){
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }



    private static void saveKeysToFiles(String sPrivateKey, String sPublicKey) throws Exception {

        FileOutputStream oPrivateKeyStream = new FileOutputStream("privateKey");
        oPrivateKeyStream.write(sPrivateKey.getBytes());
        oPrivateKeyStream.close();
        FileOutputStream oPublicKeyStream= new FileOutputStream("publicKey");
        oPublicKeyStream.write(sPublicKey.getBytes());
        oPublicKeyStream.close();
    }


    // Private key at 0, public key at 1.
    public static synchronized RsaKeyPair generatePrivatePublicKeys() throws Exception{

        // generate private and public keys
        KeyPair oKeyPair = generateKeyPair();
        PrivateKey oPrivateKey = oKeyPair.getPrivate();
        PublicKey oPublicKey = oKeyPair.getPublic();

        // Transform to Strings and populate RsaKeyPair.
        RsaKeyPair oPair = new RsaKeyPair();
        oPair.privateKey = getStringFromKey(oPrivateKey);
        oPair.publicKey = getStringFromKey(oPublicKey);

        return oPair;
    }

    private static KeyPair generateKeyPair() throws Exception {

        //works 3 lines... but DSA
//        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA", "SUN");
//        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
//        keyGen.initialize(1024, random);

        //works 2 lines but RSA
//        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
//        keyGen.initialize(2048, new SecureRandom());

        // Using ECDSA to align with bitcoin.
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(256, random);

        return keyGen.generateKeyPair();
    }


//    public static KeyPair generateKeyPair() throws Exception {
//        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
//        generator.initialize(2048, new SecureRandom());
//        KeyPair pair = generator.generateKeyPair();
//
//        return pair;
//    }



    /*
    // Create base64 encoded signature using SHA256/RSA.
    private static String signSHA256RSA(String input, String sPrivateKey) throws Exception {

        byte[] abPrivateKey = Base64.getDecoder().decode(sPrivateKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(abPrivateKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(kf.generatePrivate(spec));
        privateSignature.update(input.getBytes("UTF-8"));
        byte[] abFinal = privateSignature.sign();
        return Base64.getEncoder().encodeToString(abFinal);
    }
    */


    //Applies ECDSA Signature and returns the result as Base64 encoded string.
    public static synchronized String applyECDSASig(String sPrivateKey, String input) {

        try {
            //works 5 lines... but DSA
//            Signature dsa = Signature.getInstance("DSA", "SUN");
//            dsa.initSign(privateKey);
//            byte[] strByte = input.getBytes();
//            dsa.update(strByte);
//            return getBase64StringFromByteArray(dsa.sign());

            PrivateKey oPrivateKey = getPrivateKeyFromString(sPrivateKey);

            Signature privateSignature = Signature.getInstance("SHA1withECDSA");
            privateSignature.initSign(oPrivateKey);
            privateSignature.update(input.getBytes(UTF_8));
            byte[] signature = privateSignature.sign();

            return getBase64StringFromByteArray(signature);

        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    //Verifies an ECDSA signature
    public static synchronized boolean verifyECDSASig(String sPublicKey, String data, String signature) {
        try {
            //works 5 lines but DSA
//            byte[] sigBytes = getByteArrayFromBase64String(signature);
//            Signature ecdsaVerify = Signature.getInstance("DSA", "SUN");
//            ecdsaVerify.initVerify(publicKey);
//            ecdsaVerify.update(data.getBytes());
//            return ecdsaVerify.verify(sigBytes);

            PublicKey oPublicKey = getPublicKeyFromString(sPublicKey);

            Signature publicSignature = Signature.getInstance("SHA1withECDSA");
            publicSignature.initVerify(oPublicKey);
            publicSignature.update(data.getBytes(UTF_8));

            byte[] signatureBytes = getByteArrayFromBase64String(signature);

            return publicSignature.verify(signatureBytes);
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }



    private static String getStringFromKey(Key oKey){

        return Base64.getEncoder().encodeToString(oKey.getEncoded());
    }

    private static PrivateKey getPrivateKeyFromString(String sPrivateKey) throws Exception{

        byte[] clear = getByteArrayFromBase64String(sPrivateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clear);
        KeyFactory fact = KeyFactory.getInstance("EC");

        return fact.generatePrivate(keySpec);
    }

    private static PublicKey getPublicKeyFromString(String sPublicKey) throws Exception{

        byte[] data = getByteArrayFromBase64String(sPublicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("EC");

        return fact.generatePublic(spec);
    }

    private static String getBase64StringFromByteArray(byte[] bytes){
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] getByteArrayFromBase64String(String sB64Encoded){
        return Base64.getDecoder().decode(sB64Encoded);
    }


    // Prints list of all security algos supported on this system (SHA256withRSA, etc.)
    private static void listSecurityAlgosSupported(){

        // See which security types exist on system.
        TreeSet<String> algorithms = new TreeSet<>();
        for (Provider provider : Security.getProviders())
            for (Provider.Service service : provider.getServices())
                if (service.getType().equals("Signature"))
                    algorithms.add(service.getAlgorithm());
        for (String algorithm : algorithms)
            System.out.println(algorithm);
        return;
    }


    /*
    // Currently not used in blockchain since transactions are transparent.
    // Won't work with ECDSA... not meant for encryption, only signing.
    public static String encrypt(String plainText, PublicKey publicKey) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] cipherText = encryptCipher.doFinal(plainText.getBytes(UTF_8));

        return Base64.getEncoder().encodeToString(cipherText);
    }


    // Currently not used in blockchain since transactions are transparent.
    // Won't work with ECDSA... not meant for encryption, only signing.
    public static String decrypt(String cipherText, PrivateKey privateKey) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(cipherText);

        Cipher decriptCipher = Cipher.getInstance("RSA");
        decriptCipher.init(Cipher.DECRYPT_MODE, privateKey);

        return new String(decriptCipher.doFinal(bytes), UTF_8);
    }
    */


}
