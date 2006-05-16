package org.szegedi.spring.crypto;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Loads a private and public key pair from a Java keystore file. This is a 
 * recommended way to obtain a keypair, as it will remain valid across JVM 
 * restarts.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class KeyStoreKeyPairFactory extends ProviderBasedFactory implements ResourceLoaderAware 
{
    private String keyAlias = "default";
    private String keystoreType = "JKS";
    private URL keystoreUrl;
    private String keystoreResource;
    private String keyPassword;
    private ResourceLoader resourceLoader;

    /**
     * Sets the key alias (the name used to retrieve the keys from the factory).
     * Required.
     * @param keyAlias the key alias.
     */
    public void setKeyAlias(String keyAlias)
    {
        this.keyAlias = keyAlias;
    }

    /**
     * Sets the resource path to the keystore file. You can use this method when
     * the keystore file is a resource managed by a resource loader. You must
     * set the resource loader as well either manually, or let the framework 
     * take care of it (as it implements {@link ResourceLoaderAware}).
     * @param keystoreResource the path to the keystore resource
     */
    public void setKeystoreResource(String keystoreResource)
    {
        this.keystoreResource = keystoreResource;
    }

    /**
     * Sets the type of the keystore. Defaults to "JKS".
     * @param keystoreType the type of the keystore.
     */
    public void setKeystoreType(String keystoreType)
    {
        this.keystoreType = keystoreType;
    }

    /**
     * Sets the keystore URL. You must supply either a URL or a resource path
     * using {@link #setKeystoreResource(String)}.
     * @param keystoreUrl the URL to the keystore
     */
    public void setKeystoreUrl(URL keystoreUrl)
    {
        this.keystoreUrl = keystoreUrl;
    }

    public void setResourceLoader(ResourceLoader resourceLoader)
    {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Sets the password used to protect the private key in the keystore.
     * @param keyPassword the key password.
     */
    public void setKeyPassword(String keyPassword)
    {
        this.keyPassword = keyPassword;
    }
    
    protected Object createInstance() throws Exception
    {
        KeyStore keyStore;
        if(provider == null)
        {
            keyStore = KeyStore.getInstance(keystoreType);
        }
        else
        {
            keyStore = KeyStore.getInstance(keystoreType, provider);
        }
        InputStream in;
        if(resourceLoader != null && keystoreResource != null)
        {
            in = resourceLoader.getResource(keystoreResource).getInputStream();
        }
        else
        {
            in = keystoreUrl.openStream();
        }
        try
        {
            keyStore.load(in, null);
            return new KeyPair(keyStore.getCertificate(keyAlias).getPublicKey(),
                    (PrivateKey)keyStore.getKey(keyAlias, keyPassword.toCharArray()));
        }
        finally
        {
            in.close();
        }
    }
    
    /**
     * @return {@link KeyPair}.class
     */
    public Class getObjectType()
    {
        return KeyPair.class;
    }
}
