/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.parquet.crypto;


import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.parquet.format.EncryptionAlgorithm;
import org.apache.parquet.hadoop.metadata.ColumnPath;

import static org.apache.parquet.crypto.AesEncryptor.AAD_FILE_UNIQUE_LENGTH;

public class FileEncryptionProperties {
  
  private static final ParquetCipher ALGORITHM_DEFAULT = ParquetCipher.AES_GCM_V1;
  private static final boolean ENCRYPTED_FOOTER_DEFAULT = true;
  
  private final ParquetCipher parquetCipher;
  private final byte[] aadPrefix;
  private final EncryptionAlgorithm algorithm;
  private final boolean encryptedFooter;
  private final byte[] footerKey;
  private final byte[] footerKeyMetadata;
  private final byte[] fileAAD;
  private final Map<ColumnPath, ColumnEncryptionProperties> columnPropertyMap;
  private boolean utilized;
  private final boolean storeAadPrefixInFile;

  
  private FileEncryptionProperties(ParquetCipher cipher, 
      byte[] footerKey, byte[] footerKeyMetadata, boolean encryptedFooter,
      byte[] aadPrefix, boolean storeAadPrefixInFile,
      Map<ColumnPath, ColumnEncryptionProperties> columnPropertyMap) {
    
    // file encryption properties object can be used for writing only one file.
    // Upon completion of file writing, the encryption keys in the properties will be wiped out (set to 0 in memory).
    utilized = false;
    
    if (null == footerKey) {
      throw new IllegalArgumentException("Footer key is null");
    }
    
    if (! (footerKey.length == 16 || footerKey.length == 24 || footerKey.length == 32)) {
      throw new IllegalArgumentException("Wrong footer key length " + footerKey.length);
    }
    
    if (null != columnPropertyMap && columnPropertyMap.size() == 0) {
      throw new IllegalArgumentException("No encrypted columns");
    }
    
    SecureRandom random = new SecureRandom();
    byte[] aadFileUnique = new byte[AAD_FILE_UNIQUE_LENGTH];
    random.nextBytes(aadFileUnique);
    
    boolean supplyAadPrefix = false;
    if (null == aadPrefix) {
      this.fileAAD = aadFileUnique;
    }
    else {
      this.fileAAD = AesEncryptor.concatByteArrays(aadPrefix, aadFileUnique);
      if (!storeAadPrefixInFile) supplyAadPrefix = true;
    }
    
    this.parquetCipher = cipher;
    this.algorithm = cipher.getEncryptionAlgorithm();

    if (algorithm.isSetAES_GCM_V1()) {
      algorithm.getAES_GCM_V1().setAad_file_unique(aadFileUnique);
      algorithm.getAES_GCM_V1().setSupply_aad_prefix(supplyAadPrefix);
      if (null != aadPrefix && storeAadPrefixInFile) {
        algorithm.getAES_GCM_V1().setAad_prefix(aadPrefix);
      }
    }
    else {
      algorithm.getAES_GCM_CTR_V1().setAad_file_unique(aadFileUnique);
      algorithm.getAES_GCM_CTR_V1().setSupply_aad_prefix(supplyAadPrefix);
      if (null != aadPrefix && storeAadPrefixInFile) {
        algorithm.getAES_GCM_CTR_V1().setAad_prefix(aadPrefix);
      }
    }

    this.footerKey = footerKey;
    this.footerKeyMetadata = footerKeyMetadata;
    this.encryptedFooter = encryptedFooter;
    this.columnPropertyMap = columnPropertyMap;
    this.storeAadPrefixInFile = storeAadPrefixInFile;
    this.aadPrefix = aadPrefix;
  }
  
  /**
   * New encryption properties object must be created for each encrypted file.
   * Encryption keys (footer and column) are cloned.
   * Upon completion of file writing, the cloned encryption keys in the properties will 
   * be wiped out (array values set to 0).
   * Caller is responsible for wiping out the input key array. 
   * 
   * @param keyBytes Encryption key for file footer and some (or all) columns. 
   * Key length must be either 16, 24 or 32 bytes.
   * If null, footer won't be encrypted. At least one column must be encrypted then.
   */
  public static Builder builder(byte[] footerKey) {
    return new Builder(footerKey);
  }
  
  
  public static class Builder {
    private byte[] footerKeyBytes;
    private boolean encryptedFooter;
    private ParquetCipher parquetCipher;
    private byte[] footerKeyMetadata;
    private byte[] aadPrefix;
    private Map<ColumnPath, ColumnEncryptionProperties> columnPropertyMap;
    private boolean storeAadPrefixInFile;
    
    
    private Builder(byte[] footerKey) {
      this.parquetCipher = ALGORITHM_DEFAULT;
      this.encryptedFooter = ENCRYPTED_FOOTER_DEFAULT;
      this.footerKeyBytes = new byte[footerKey.length];
      System.arraycopy(footerKey, 0, this.footerKeyBytes, 0, footerKey.length);
    }
    
    /**
     * Create files with plaintext footer.
     * If not called, the files will be created with encrypted footer (default).
     * @return
     */
    public Builder withPlaintextFooter() {
      this.encryptedFooter = false;
      return this;
    }
    
    /**
     * Set encryption algorithm.
     * If not called, files will be encrypted with AES_GCM_V1 (default).
     * @param parquetCipher
     * @return
     */
    public Builder withAlgorithm(ParquetCipher parquetCipher) {
      this.parquetCipher = parquetCipher;
      return this;
    }
    
    /**
     * Set a key retrieval metadata (converted from String).
     * use either withFooterKeyMetaData or withFooterKeyID, not both.
     * @param keyID will be converted to metadata (UTF-8 array).
     * @return
     */
    public Builder withFooterKeyID(String keyID) {
      if (null == keyID) {
        return this;
      }
      return withFooterKeyMetadata(keyID.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Set a key retrieval metadata.
     * use either withFooterKeyMetaData or withFooterKeyID, not both.
     * @param footerKeyMetadata
     * @return
     */
    public Builder withFooterKeyMetadata(byte[] footerKeyMetadata) {
      if (null == footerKeyMetadata) {
        return this;
      }
      if (null != this.footerKeyMetadata) {
        throw new IllegalArgumentException("Footer key metadata already set");
      }
      this.footerKeyMetadata = footerKeyMetadata;
      return this;
    }
    
    /**
     * Set the file AAD Prefix.
     * @param aadPrefixBytes
     */
    public Builder withAADPrefix(byte[] aadPrefixBytes) {
      if (null == aadPrefixBytes) {
        return this;
      }
      if (null != this.aadPrefix) {
        throw new IllegalArgumentException("AAD Prefix already set");
      }
      this.aadPrefix = aadPrefixBytes;
      this.storeAadPrefixInFile = true;
      return this;
    }
    
    /**
     * Skip storing AAD Prefix in file.
     * If not called, and if AAD Prefix is set, it will be stored.
     */
    public Builder withoutAADPrefixStorage() {
      if (null == this.aadPrefix) {
        throw new IllegalArgumentException("AAD Prefix not yet set");
      }
      this.storeAadPrefixInFile = false;
      return this;
    }
    
    /**
     * Set the list of encrypted columns and their properties (keys etc).
     * If not called, all columns will be encrypted with the footer key. 
     * If called, the file columns not in the list will be left unencrypted.
     * @param encryptedColumns
     * @return
     */
    public Builder withEncryptedColumns(Map<ColumnPath, ColumnEncryptionProperties> encryptedColumns)  {
      if (null == encryptedColumns) {
        return this;
      }
      if (null != this.columnPropertyMap) {
        throw new IllegalArgumentException("Column properties already set");
      }
      for (Map.Entry<ColumnPath, ColumnEncryptionProperties> entry : encryptedColumns.entrySet()) {
        if(entry.getValue().isUtilized()) {
          throw new IllegalArgumentException("Column properties re-used in another file");
        }
        entry.getValue().setUtilized();
      }
      // Copy the map to make column properties immutable
      this.columnPropertyMap = new HashMap<ColumnPath, ColumnEncryptionProperties>(encryptedColumns);
      return this;
    }
    
    public FileEncryptionProperties build() {
      return new FileEncryptionProperties(parquetCipher, 
          footerKeyBytes, footerKeyMetadata, encryptedFooter,
          aadPrefix, storeAadPrefixInFile, 
          columnPropertyMap);
    }
  }
  
  public EncryptionAlgorithm getAlgorithm() {
    return algorithm;
  }

  public byte[] getFooterKey() {
    return footerKey;
  }

  public byte[] getFooterKeyMetadata() {
    return footerKeyMetadata;
  }
  
  public Map<ColumnPath, ColumnEncryptionProperties> getEncryptedColumns() {
    return columnPropertyMap;
  }

  public ColumnEncryptionProperties getColumnProperties(ColumnPath columnPath) {
    if (null == columnPropertyMap) {
      // encrypted, with footer key
      return ColumnEncryptionProperties.builder(columnPath, true).build();
    }
    else {
      ColumnEncryptionProperties columnProperties = columnPropertyMap.get(columnPath);
      if (null != columnProperties) {
        return columnProperties;
      }
      else {
        // plaintext column
        return ColumnEncryptionProperties.builder(columnPath, false).build();
      }
    }
  }

  public byte[] getFileAAD() {
    return fileAAD;
  }

  public boolean encryptedFooter() {
    return encryptedFooter;
  }

  boolean isUtilized() {
    return utilized;
  }

  void setUtilized() {
    utilized = true;
  }

  void wipeOutEncryptionKeys() {
    Arrays.fill(footerKey, (byte)0);
    
    if (null != columnPropertyMap) {
      for (Map.Entry<ColumnPath, ColumnEncryptionProperties> entry : columnPropertyMap.entrySet()) {
        entry.getValue().wipeOutEncryptionKey();
      }
    }
  }
  
  /** EncryptionProperties object can be used for writing one file only.
   * (at the end, keys are wiped out in the memory).
   * This method allows to clone identical properties for another file, 
   * with an option to update the aadPrefix (if newAadPrefix is null, 
   * aadPrefix will be cloned too) 
   */
  public FileEncryptionProperties deepClone(byte[] newAadPrefix) {
    
    byte[] footerKeyBytes = (null == footerKey?null:footerKey.clone());
    Map<ColumnPath, ColumnEncryptionProperties> columnProps = null;
    if (null != columnPropertyMap) {
      columnProps = new HashMap<ColumnPath, ColumnEncryptionProperties>();
      for (Map.Entry<ColumnPath, ColumnEncryptionProperties> entry : columnPropertyMap.entrySet()) {
        columnProps.put(entry.getKey(), entry.getValue().deepClone());
      }
    }
    
    if (null == newAadPrefix) newAadPrefix = aadPrefix;
    
    return new FileEncryptionProperties(parquetCipher, 
        footerKeyBytes, footerKeyMetadata, encryptedFooter,
        newAadPrefix, storeAadPrefixInFile, 
        columnProps);
  }
}
