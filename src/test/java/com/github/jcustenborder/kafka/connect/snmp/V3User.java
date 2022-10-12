package com.github.jcustenborder.kafka.connect.snmp;

import com.github.jcustenborder.kafka.connect.snmp.enums.AuthenticationProtocol;
import com.github.jcustenborder.kafka.connect.snmp.enums.PrivacyProtocol;
import org.snmp4j.security.AuthHMAC384SHA512;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

class V3User {
  public String username;
  public String authPassphrase;
  public String privacyPassphrase;
  public OID authProtocol = AuthMD5.ID;
  public OID privacyProtocol = PrivDES.ID;

  public V3User(String username, String privacyPassphrase, String authPassphrase) {
    this.username = username;
    this.authPassphrase = authPassphrase;
    this.privacyPassphrase = privacyPassphrase;
  }

  public V3User(String username, String authPassphrase, String privacyPassphrase, OID authProtocol) {
    this.username = username;
    this.authPassphrase = authPassphrase;
    this.privacyPassphrase = privacyPassphrase;
    this.authProtocol = authProtocol;
  }

  public V3User(String username, String authPassphrase, String privacyPassphrase, OID authProtocol, OID privacyProtocol) {
    this.username = username;
    this.authPassphrase = authPassphrase;
    this.privacyPassphrase = privacyPassphrase;
    this.authProtocol = authProtocol;
    this.privacyProtocol = privacyProtocol;
  }

  public UsmUser toUsm() {
    return new UsmUser(
        new OctetString(this.username),
        this.authProtocol,
        new OctetString(this.privacyPassphrase),
        privacyProtocol,
        new OctetString(this.privacyPassphrase)
    );
  }

  public PrivacyProtocol getPriv() {
    return convertPrivacyProtocol(this.privacyProtocol);
  }
  public AuthenticationProtocol getAuth() {
    return convertAuthenticationProtocol(this.authProtocol);
  }
  private PrivacyProtocol convertPrivacyProtocol(OID priv) {
    if (Priv3DES.ID.equals(priv)) {
      return PrivacyProtocol.DES3;
    } else if (PrivAES128.ID.equals(priv)) {
      return PrivacyProtocol.AES128;
    } else if (PrivAES256.ID.equals(priv)) {
      return PrivacyProtocol.AES256;
    }
    return PrivacyProtocol.AES128;
  }

  private AuthenticationProtocol convertAuthenticationProtocol(OID auth) {
    if (AuthMD5.ID.equals(auth)) {
      return AuthenticationProtocol.MD5;
    } else if (AuthSHA.ID.equals(auth)) {
      return AuthenticationProtocol.SHA;
    } else if (AuthHMAC384SHA512.ID.equals(auth)) {
      return AuthenticationProtocol.SHA2_512;
    }
    return AuthenticationProtocol.MD5;
  }
}
