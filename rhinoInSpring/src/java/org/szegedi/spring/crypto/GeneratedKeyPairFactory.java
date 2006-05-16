package org.szegedi.spring.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Generates a new pair of a public and private keys. Note that as keys are not
 * preserved across application context restarts, whatever you might have 
 * encrypted or signed using them will become invalid no later than when the
 * JVM is shut down. Use the {@link KeyStoreKeyPairFactory} instead to load a 
 * persistent keypair from a Java keystore file.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class GeneratedKeyPairFactory extends ProviderBasedFactory
{
    private String algorithm = "RSA";
    private SecureRandom secureRandom;
    private int keySize = 1024;
    
    /**
     * Sets the key algorithm to use. Defaults to "RSA".
     * @param algorithm the key algorithm
     */
    public void setAlgorithm(String algorithm)
    {
        this.algorithm = algorithm;
    }
    
    /**
     * Sets the size of the keys in bits. Defaults to 1024.
     * @param keySize the size of the keys.
     */
    public void setKeySize(int keySize)
    {
        this.keySize = keySize;
    }
    
    /**
     * Sets an instance of secure random to use. If not set, a one-off instance
     * created using a private instance of {@link SecureRandomFactory} will be
     * created, with the configured security provider.
     * @param secureRandom the secure random instance to use.
     */
    public void setSecureRandom(SecureRandom secureRandom)
    {
        this.secureRandom = secureRandom;
    }
    
    protected Object createInstance() throws Exception
    {
        if(secureRandom == null)
        {
            SecureRandomFactory secureRandomFactory = new SecureRandomFactory();
            secureRandomFactory.setProvider(provider);
            secureRandomFactory.afterPropertiesSet();
            secureRandom = (SecureRandom)secureRandomFactory.createInstance();
        }
        KeyPairGenerator kpg;
        if(provider == null)
        {
            kpg = KeyPairGenerator.getInstance(algorithm);
        }
        else
        {
            kpg = KeyPairGenerator.getInstance(algorithm, provider);
        }
        kpg.initialize(keySize, secureRandom);
        return kpg.generateKeyPair();
    }
    
    /**
     * @return {@link KeyPair}.class
     */
    public Class getObjectType()
    {
        return KeyPair.class;
    }
}
